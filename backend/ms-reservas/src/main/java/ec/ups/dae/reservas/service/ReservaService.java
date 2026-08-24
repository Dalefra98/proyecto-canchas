package ec.ups.dae.reservas.service;

import ec.ups.dae.reservas.dto.BloqueoExterno;
import ec.ups.dae.reservas.dto.CanchaExterna;
import ec.ups.dae.reservas.dto.ReservaRequest;
import ec.ups.dae.reservas.dto.ReservaResponse;
import ec.ups.dae.reservas.entity.EstadoReserva;
import ec.ups.dae.reservas.entity.Reserva;
import ec.ups.dae.reservas.exception.BloqueInvalidoException;
import ec.ups.dae.reservas.exception.BloqueOcupadoException;
import ec.ups.dae.reservas.exception.CanchaNoEncontradaException;
import ec.ups.dae.reservas.exception.FechaPasadaException;
import ec.ups.dae.reservas.exception.LimiteReservasException;
import ec.ups.dae.reservas.mapper.ReservaMapper;
import ec.ups.dae.reservas.repository.ReservaRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de negocio de las reservas. RN-01 a RN-06 y RN-08.
 */
@Service
public class ReservaService {

    /** RN-01: el bloque es de exactamente una hora. */
    private static final int DURACION_BLOQUE_HORAS = 1;

    private final ReservaRepository reservaRepository;
    private final CanchasClient canchasClient;
    private final ReservaMapper reservaMapper;
    private final int maximoReservasActivas;

    public ReservaService(ReservaRepository reservaRepository, CanchasClient canchasClient,
                          ReservaMapper reservaMapper,
                          @Value("${reservas.max-activas}") int maximoReservasActivas) {
        this.reservaRepository = reservaRepository;
        this.canchasClient = canchasClient;
        this.reservaMapper = reservaMapper;
        this.maximoReservasActivas = maximoReservasActivas;
    }

    /**
     * HU-02: alta de reserva.
     *
     * El orden de las comprobaciones no es casual (design D-10 y D-19): primero todas las de
     * 400, despues el 404 de la cancha, y al final los tres 409. Entre esos tres, las dos
     * consultas locales van antes que la que exige red, porque devuelven el mismo codigo y el
     * rechazo mas frecuente —el bloque ya reservado— no debe costar una llamada HTTP.
     */
    @Transactional
    public ReservaResponse crear(ReservaRequest peticion) {
        LocalDate fecha = reservaMapper.aFecha(peticion.fecha(), "fecha");
        LocalTime horaInicio = reservaMapper.aHora(peticion.horaInicio(), "horaInicio");
        LocalTime horaFin = horaInicio.plusHours(DURACION_BLOQUE_HORAS);

        // RN-01: los bloques son franjas fijas que empiezan en hora en punto.
        if (horaInicio.getMinute() != 0) {
            throw new BloqueInvalidoException("El campo horaInicio debe ser una hora en punto, con minutos 00");
        }

        // D-03: no se reserva el pasado. La referencia es la fecha y hora del servidor (S-09).
        if (!LocalDateTime.of(fecha, horaInicio).isAfter(LocalDateTime.now())) {
            throw new FechaPasadaException("No se puede reservar un bloque que ya ocurrio");
        }

        // D-05: una cancha inexistente y una inactiva responden lo mismo, 404 NO_ENCONTRADO.
        CanchaExterna cancha = canchasClient.obtenerCancha(peticion.canchaId());
        if (!cancha.activa()) {
            throw new CanchaNoEncontradaException("La cancha no existe");
        }

        LocalTime apertura = reservaMapper.aHora(cancha.horaApertura(), "horaApertura");
        LocalTime cierre = reservaMapper.aHora(cancha.horaCierre(), "horaCierre");
        if (horaInicio.isBefore(apertura) || horaFin.isAfter(cierre)) {
            throw new BloqueInvalidoException(
                    "El bloque debe estar dentro del horario de atencion de la cancha, de "
                            + reservaMapper.formatearHora(apertura) + " a "
                            + reservaMapper.formatearHora(cierre));
        }

        Long usuarioId = usuarioAutenticado();

        // RN-02, primera barrera: consulta local. La segunda es ux_reserva_bloque_confirmada,
        // que el manejador traduce al mismo 409 BLOQUE_OCUPADO (design D-03).
        if (reservaRepository.existsByCanchaIdAndFechaAndHoraInicioAndEstado(
                peticion.canchaId(), fecha, horaInicio, EstadoReserva.CONFIRMADA)) {
            throw new BloqueOcupadoException("El bloque horario ya esta reservado");
        }

        // RN-06: solo cuentan las CONFIRMADA cuya fecha y hora de inicio aun no ocurrieron
        // (decision D-04 del requirements, borde estricto de design D-20).
        long activas = reservaRepository.contarActivas(usuarioId, LocalDate.now(), LocalTime.now());
        if (activas >= maximoReservasActivas) {
            throw new LimiteReservasException(
                    "Alcanzo el limite de " + maximoReservasActivas + " reservas activas simultaneas");
        }

        // D-07: un bloque bajo mantenimiento se rechaza con el mismo codigo que uno reservado.
        // Va al final de los tres 409 por ser el unico que necesita red (design D-19).
        if (enMantenimiento(peticion.canchaId(), fecha, horaInicio, horaFin)) {
            throw new BloqueOcupadoException("El bloque horario esta en mantenimiento");
        }

        Reserva reserva = new Reserva(usuarioId, peticion.canchaId(), fecha, horaInicio, horaFin,
                EstadoReserva.CONFIRMADA);
        return reservaMapper.aRespuesta(reservaRepository.save(reserva));
    }

    /**
     * Solapamiento con un bloqueo de mantenimiento: inicioA < finB y finA > inicioB. Tocarse en
     * un extremo no ocupa. Es el mismo criterio que aplica la disponibilidad (HU-01).
     */
    private boolean enMantenimiento(Long canchaId, LocalDate fecha, LocalTime inicio, LocalTime fin) {
        List<BloqueoExterno> bloqueos = canchasClient.listarBloqueos(canchaId, fecha);
        return bloqueos.stream().anyMatch(bloqueo -> {
            LocalTime inicioBloqueo = reservaMapper.aHora(bloqueo.horaInicio(), "horaInicio");
            LocalTime finBloqueo = reservaMapper.aHora(bloqueo.horaFin(), "horaFin");
            return inicio.isBefore(finBloqueo) && fin.isAfter(inicioBloqueo);
        });
    }

    /**
     * El usuarioId sale siempre del claim sub del token, nunca del cuerpo de la peticion
     * (HU-02, HU-08). El filtro deja ese valor como principal del contexto.
     */
    private Long usuarioAutenticado() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return (Long) autenticacion.getPrincipal();
    }
}
