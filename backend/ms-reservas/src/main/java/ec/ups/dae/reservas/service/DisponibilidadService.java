package ec.ups.dae.reservas.service;

import ec.ups.dae.reservas.dto.BloqueResponse;
import ec.ups.dae.reservas.dto.BloqueoExterno;
import ec.ups.dae.reservas.dto.CanchaExterna;
import ec.ups.dae.reservas.dto.DisponibilidadResponse;
import ec.ups.dae.reservas.entity.EstadoReserva;
import ec.ups.dae.reservas.entity.Reserva;
import ec.ups.dae.reservas.mapper.ReservaMapper;
import ec.ups.dae.reservas.repository.ReservaRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-01: arma el payload congelado DisponibilidadResponse.
 *
 * El horario de atencion y los bloqueos de mantenimiento vienen de ms-canchas por HTTP; las
 * reservas salen de la propia base. Ninguna consulta toca canchas_db.
 */
@Service
public class DisponibilidadService {

    /** RN-01: los bloques son franjas fijas de una hora. */
    private static final int DURACION_BLOQUE_HORAS = 1;

    private final ReservaRepository reservaRepository;
    private final CanchasClient canchasClient;
    private final ReservaMapper reservaMapper;

    public DisponibilidadService(ReservaRepository reservaRepository, CanchasClient canchasClient,
                                 ReservaMapper reservaMapper) {
        this.reservaRepository = reservaRepository;
        this.canchasClient = canchasClient;
        this.reservaMapper = reservaMapper;
    }

    @Transactional(readOnly = true)
    public DisponibilidadResponse consultar(Long canchaId, String fechaTexto) {
        LocalDate fecha = reservaMapper.aFecha(fechaTexto, "fecha");
        CanchaExterna cancha = canchasClient.obtenerCancha(canchaId);

        LocalTime apertura = reservaMapper.aHora(cancha.horaApertura(), "horaApertura");
        LocalTime cierre = reservaMapper.aHora(cancha.horaCierre(), "horaCierre");
        List<LocalTime> inicios = iniciosDeBloque(apertura, cierre);

        List<BloqueResponse> bloques = cancha.activa()
                ? calcular(canchaId, fecha, inicios)
                : todosOcupados(inicios);

        return new DisponibilidadResponse(
                canchaId,
                reservaMapper.formatearFecha(fecha),
                reservaMapper.formatearHora(apertura),
                reservaMapper.formatearHora(cierre),
                bloques);
    }

    /**
     * Franjas de una hora desde horaApertura hasta horaCierre. Si el horario no fuera multiplo
     * exacto de una hora, el resto final se descarta: no cabe un bloque completo (S-04).
     */
    private List<LocalTime> iniciosDeBloque(LocalTime apertura, LocalTime cierre) {
        List<LocalTime> inicios = new ArrayList<>();
        for (LocalTime inicio = apertura;
             !inicio.plusHours(DURACION_BLOQUE_HORAS).isAfter(cierre);
             inicio = inicio.plusHours(DURACION_BLOQUE_HORAS)) {
            inicios.add(inicio);
        }
        return inicios;
    }

    /**
     * Un bloque esta ocupado si tiene una reserva CONFIRMADA con esa hora de inicio (RN-02), o
     * si se solapa con un bloqueo de mantenimiento. Una reserva CANCELADA no ocupa: el bloque
     * vuelve a estar libre (RN-05).
     */
    private List<BloqueResponse> calcular(Long canchaId, LocalDate fecha, List<LocalTime> inicios) {
        Set<LocalTime> horasReservadas = reservaRepository
                .findByCanchaIdAndFechaAndEstado(canchaId, fecha, EstadoReserva.CONFIRMADA)
                .stream()
                .map(Reserva::getHoraInicio)
                .collect(Collectors.toSet());

        List<BloqueoExterno> bloqueos = canchasClient.listarBloqueos(canchaId, fecha);

        return inicios.stream()
                .map(inicio -> {
                    LocalTime fin = inicio.plusHours(DURACION_BLOQUE_HORAS);
                    boolean ocupado = horasReservadas.contains(inicio) || enMantenimiento(bloqueos, inicio, fin);
                    return bloque(inicio, fin, !ocupado);
                })
                .toList();
    }

    /**
     * Cancha inactiva: todos los bloques salen ocupados y NO se pide la lista de bloqueos. El
     * resultado ya esta determinado, asi que la segunda llamada HTTP no cambiaria nada y solo
     * agregaria latencia y una via mas de fallo (design D-09, decision D-05 del requirements).
     */
    private List<BloqueResponse> todosOcupados(List<LocalTime> inicios) {
        return inicios.stream()
                .map(inicio -> bloque(inicio, inicio.plusHours(DURACION_BLOQUE_HORAS), false))
                .toList();
    }

    /**
     * Solapamiento: inicioA < finB y finA > inicioB. Tocarse en un extremo no ocupa, asi que un
     * bloqueo de 10:00 a 12:00 marca 10:00-11:00 y 11:00-12:00, pero no 09:00-10:00.
     */
    private boolean enMantenimiento(List<BloqueoExterno> bloqueos, LocalTime inicio, LocalTime fin) {
        return bloqueos.stream().anyMatch(bloqueo -> {
            LocalTime inicioBloqueo = reservaMapper.aHora(bloqueo.horaInicio(), "horaInicio");
            LocalTime finBloqueo = reservaMapper.aHora(bloqueo.horaFin(), "horaFin");
            return inicio.isBefore(finBloqueo) && fin.isAfter(inicioBloqueo);
        });
    }

    private BloqueResponse bloque(LocalTime inicio, LocalTime fin, boolean disponible) {
        return new BloqueResponse(
                reservaMapper.formatearHora(inicio),
                reservaMapper.formatearHora(fin),
                disponible);
    }
}
