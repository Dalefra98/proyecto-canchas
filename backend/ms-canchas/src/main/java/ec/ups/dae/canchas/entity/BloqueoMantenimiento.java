package ec.ups.dae.canchas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Mapea la tabla bloqueo_mantenimiento de canchas_db, creada por
 * infra/postgres/03-ddl-canchas.sql.
 *
 * canchaId es una columna simple, no una asociacion @ManyToOne (design D-02): el servicio
 * nunca navega del bloqueo a la cancha, y cuando necesita el horario ya cargo la cancha por
 * el canchaId de la ruta. La clave foranea fk_bloqueo_cancha sigue existiendo en la base.
 */
@Entity
@Table(name = "bloqueo_mantenimiento")
public class BloqueoMantenimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bloqueo_id")
    private Long bloqueoId;

    @Column(name = "cancha_id", nullable = false)
    private Long canchaId;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "motivo", nullable = false, length = 200)
    private String motivo;

    protected BloqueoMantenimiento() {
        // Requerido por JPA.
    }

    public BloqueoMantenimiento(Long canchaId, LocalDate fecha, LocalTime horaInicio,
                                LocalTime horaFin, String motivo) {
        this.canchaId = canchaId;
        this.fecha = fecha;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.motivo = motivo;
    }

    public Long getBloqueoId() {
        return bloqueoId;
    }

    public Long getCanchaId() {
        return canchaId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public String getMotivo() {
        return motivo;
    }
}
