package ec.ups.dae.canchas.service;

import ec.ups.dae.canchas.dto.BloqueoRequest;
import ec.ups.dae.canchas.dto.BloqueoResponse;
import ec.ups.dae.canchas.entity.BloqueoMantenimiento;
import ec.ups.dae.canchas.entity.Cancha;
import ec.ups.dae.canchas.exception.BloqueoDuplicadoException;
import ec.ups.dae.canchas.exception.BloqueoNoEncontradoException;
import ec.ups.dae.canchas.exception.CanchaNoEncontradaException;
import ec.ups.dae.canchas.exception.FechaPasadaException;
import ec.ups.dae.canchas.exception.FueraDeHorarioException;
import ec.ups.dae.canchas.exception.HorarioInvalidoException;
import ec.ups.dae.canchas.mapper.BloqueoMapper;
import ec.ups.dae.canchas.repository.BloqueoRepository;
import ec.ups.dae.canchas.repository.CanchaRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BloqueoService {

    private final BloqueoRepository bloqueoRepository;
    private final CanchaRepository canchaRepository;
    private final BloqueoMapper bloqueoMapper;

    public BloqueoService(BloqueoRepository bloqueoRepository, CanchaRepository canchaRepository,
                          BloqueoMapper bloqueoMapper) {
        this.bloqueoRepository = bloqueoRepository;
        this.canchaRepository = canchaRepository;
        this.bloqueoMapper = bloqueoMapper;
    }

    /**
     * HU-06: listado de bloqueos de una cancha. El parametro fecha es opcional (decision
     * P-06); sin el se devuelven todos. No se filtra por rol: el contrato abre esta ruta a
     * ADMIN y USUARIO para que ms-reservas calcule disponibilidad (design D-12).
     */
    @Transactional(readOnly = true)
    public List<BloqueoResponse> listar(Long canchaId, String fecha) {
        exigirCancha(canchaId);
        List<BloqueoMantenimiento> bloqueos = (fecha == null || fecha.isBlank())
                ? bloqueoRepository.findByCanchaId(canchaId)
                : bloqueoRepository.findByCanchaIdAndFecha(canchaId, bloqueoMapper.aFecha(fecha, "fecha"));
        return bloqueos.stream().map(bloqueoMapper::aRespuesta).toList();
    }

    /**
     * HU-07: alta de un bloqueo de mantenimiento.
     *
     * El orden importa (design D-10): primero los 400 de forma y de regla, y solo despues el
     * 409 de conflicto, para que un cuerpo invalido nunca se reporte como conflicto.
     */
    @Transactional
    public BloqueoResponse crear(Long canchaId, BloqueoRequest peticion) {
        BloqueoMantenimiento bloqueo = bloqueoMapper.aEntidad(canchaId, peticion);
        // D-16: se admite bloquear una cancha inactiva; puede estar justamente en
        // mantenimiento. Solo se exige que exista.
        Cancha cancha = exigirCancha(canchaId);

        if (!bloqueo.getHoraFin().isAfter(bloqueo.getHoraInicio())) {
            throw new HorarioInvalidoException("horaFin debe ser posterior a horaInicio");
        }
        // P-02.c: bloquear el pasado no tiene efecto y ensucia los reportes. Hoy si se admite.
        if (bloqueo.getFecha().isBefore(LocalDate.now())) {
            throw new FechaPasadaException("No se puede bloquear una fecha ya pasada");
        }
        // P-02.b: la franja debe caer completa dentro del horario de atencion de la cancha.
        if (bloqueo.getHoraInicio().isBefore(cancha.getHoraApertura())
                || bloqueo.getHoraFin().isAfter(cancha.getHoraCierre())) {
            throw new FueraDeHorarioException(
                    "El bloqueo debe estar dentro del horario de atencion de la cancha");
        }
        // P-02.a y P-02.d: duplicado exacto y solapamiento parcial comparten codigo. La regla
        // vive aqui porque uq_bloqueo_franja solo compara hora_inicio exacta (design D-07).
        if (bloqueoRepository.existsByCanchaIdAndFechaAndHoraInicioLessThanAndHoraFinGreaterThan(
                canchaId, bloqueo.getFecha(), bloqueo.getHoraFin(), bloqueo.getHoraInicio())) {
            throw new BloqueoDuplicadoException("Ya existe un bloqueo que se solapa con esa franja");
        }
        return bloqueoMapper.aRespuesta(bloqueoRepository.save(bloqueo));
    }

    /**
     * HU-08: baja de un bloqueo. Un bloqueo que existe pero pertenece a otra cancha responde
     * 404, igual que uno inexistente: la ruta identifica el recurso por el par cancha mas
     * bloqueo (supuesto S-05).
     */
    @Transactional
    public void eliminar(Long canchaId, Long bloqueoId) {
        exigirCancha(canchaId);
        BloqueoMantenimiento bloqueo = bloqueoRepository.findByBloqueoIdAndCanchaId(bloqueoId, canchaId)
                .orElseThrow(() -> new BloqueoNoEncontradoException("El bloqueo no existe"));
        bloqueoRepository.delete(bloqueo);
    }

    private Cancha exigirCancha(Long canchaId) {
        return canchaRepository.findById(canchaId)
                .orElseThrow(() -> new CanchaNoEncontradaException("La cancha no existe"));
    }
}
