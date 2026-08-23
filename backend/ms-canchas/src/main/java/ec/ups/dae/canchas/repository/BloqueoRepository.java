package ec.ups.dae.canchas.repository;

import ec.ups.dae.canchas.entity.BloqueoMantenimiento;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Unico punto de acceso a la tabla bloqueo_mantenimiento de canchas_db. Ninguna consulta
 * toca tablas de otro microservicio.
 */
public interface BloqueoRepository extends JpaRepository<BloqueoMantenimiento, Long> {

    /** Listado sin filtro (HU-06). */
    List<BloqueoMantenimiento> findByCanchaId(Long canchaId);

    /** Listado con el parametro opcional ?fecha (HU-06, decision P-06). */
    List<BloqueoMantenimiento> findByCanchaIdAndFecha(Long canchaId, LocalDate fecha);

    /**
     * Solapamiento de franjas en la misma cancha y fecha: inicioA < finB y finA > inicioB.
     * Cubre tambien el duplicado exacto de uq_bloqueo_franja. Dos franjas que solo se tocan
     * en un extremo (09:00-11:00 y 11:00-12:00) no lo cumplen (HU-07, decisiones P-02.a y
     * P-02.d). El indice de la base solo compara hora_inicio exacta, por eso la regla vive
     * en el servicio y el DDL no cambia.
     */
    boolean existsByCanchaIdAndFechaAndHoraInicioLessThanAndHoraFinGreaterThan(
            Long canchaId, LocalDate fecha, LocalTime horaFin, LocalTime horaInicio);

    /** Baja: el bloqueo debe pertenecer a esa cancha (HU-08, supuesto S-05). */
    Optional<BloqueoMantenimiento> findByBloqueoIdAndCanchaId(Long bloqueoId, Long canchaId);
}
