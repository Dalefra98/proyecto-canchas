package ec.ups.dae.reservas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Mapea la tabla reserva de reservas_db, creada por infra/postgres/04-ddl-reservas.sql.
 * El esquema lo manda el DDL versionado: si ddl-auto=validate falla, se corrige esta clase.
 *
 * usuarioId y canchaId son columnas simples, no asociaciones: esas filas viven en
 * usuarios_db y canchas_db, y la integracion con esos servicios es por REST (design D-02).
 */
@Entity
@Table(name = "reserva")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "cancha_id", nullable = false)
    private Long canchaId;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    // RN-01: ck_reserva_bloque_una_hora obliga a hora_fin = hora_inicio + 1 hora.
    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoReserva estado;

    protected Reserva() {
        // Requerido por JPA.
    }

    public Reserva(Long usuarioId, Long canchaId, LocalDate fecha, LocalTime horaInicio,
                   LocalTime horaFin, EstadoReserva estado) {
        this.usuarioId = usuarioId;
        this.canchaId = canchaId;
        this.fecha = fecha;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.estado = estado;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
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

    public EstadoReserva getEstado() {
        return estado;
    }

    public void setEstado(EstadoReserva estado) {
        this.estado = estado;
    }
}
