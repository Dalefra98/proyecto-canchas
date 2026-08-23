package ec.ups.dae.reservas.repository;

import ec.ups.dae.reservas.entity.EstadoReserva;
import ec.ups.dae.reservas.entity.Reserva;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Unico punto de acceso a la tabla reserva de reservas_db. Ninguna consulta toca tablas de
 * otro microservicio: el usuario y la cancha llegan por REST.
 */
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /** Disponibilidad: reservas CONFIRMADA de una cancha en un dia (HU-01, RN-02, RN-05). */
    List<Reserva> findByCanchaIdAndFechaAndEstado(Long canchaId, LocalDate fecha,
                                                  EstadoReserva estado);

    /**
     * RN-02, primera barrera del alta (HU-02). La segunda es la violacion del indice parcial
     * ux_reserva_bloque_confirmada, traducida al mismo 409 BLOQUE_OCUPADO.
     */
    boolean existsByCanchaIdAndFechaAndHoraInicioAndEstado(Long canchaId, LocalDate fecha,
                                                           LocalTime horaInicio,
                                                           EstadoReserva estado);

    /**
     * RN-06: reservas activas de un usuario, es decir CONFIRMADA cuya fecha y hora de inicio
     * aun no han ocurrido (decision D-04 del requirements). Las pasadas no cuentan: contarlas
     * dejaria al usuario bloqueado para siempre tras tres reservas.
     *
     * Es el unico @Query del repositorio: la condicion mezcla dos comparaciones sobre
     * columnas distintas y no se expresa como consulta derivada legible (design D-05).
     */
    @Query("""
            SELECT COUNT(r) FROM Reserva r
            WHERE r.usuarioId = :usuarioId
              AND r.estado = ec.ups.dae.reservas.entity.EstadoReserva.CONFIRMADA
              AND (r.fecha > :hoy OR (r.fecha = :hoy AND r.horaInicio > :ahora))
            """)
    long contarActivas(@Param("usuarioId") Long usuarioId, @Param("hoy") LocalDate hoy,
                       @Param("ahora") LocalTime ahora);

    /** Historial propio, lo mas reciente primero (HU-03, decision D-09 del requirements). */
    List<Reserva> findByUsuarioIdOrderByFechaDescHoraInicioDesc(Long usuarioId);

    /** Listado global, con el mismo orden (HU-04, decision D-09 del requirements). */
    List<Reserva> findAllByOrderByFechaDescHoraInicioDesc();
}
