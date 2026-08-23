package ec.ups.dae.canchas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;

/**
 * Mapea la tabla cancha de canchas_db, creada por infra/postgres/03-ddl-canchas.sql.
 * El esquema lo manda el DDL versionado: si ddl-auto=validate falla, se corrige esta clase.
 */
@Entity
@Table(name = "cancha")
public class Cancha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cancha_id")
    private Long canchaId;

    @Column(name = "nombre", nullable = false, length = 80, unique = true)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "deporte", nullable = false, length = 8)
    private Deporte deporte;

    @Column(name = "hora_apertura", nullable = false)
    private LocalTime horaApertura;

    @Column(name = "hora_cierre", nullable = false)
    private LocalTime horaCierre;

    @Column(name = "activa", nullable = false)
    private boolean activa;

    protected Cancha() {
        // Requerido por JPA.
    }

    public Cancha(String nombre, Deporte deporte, LocalTime horaApertura, LocalTime horaCierre,
                  boolean activa) {
        this.nombre = nombre;
        this.deporte = deporte;
        this.horaApertura = horaApertura;
        this.horaCierre = horaCierre;
        this.activa = activa;
    }

    public Long getCanchaId() {
        return canchaId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Deporte getDeporte() {
        return deporte;
    }

    public void setDeporte(Deporte deporte) {
        this.deporte = deporte;
    }

    public LocalTime getHoraApertura() {
        return horaApertura;
    }

    public void setHoraApertura(LocalTime horaApertura) {
        this.horaApertura = horaApertura;
    }

    public LocalTime getHoraCierre() {
        return horaCierre;
    }

    public void setHoraCierre(LocalTime horaCierre) {
        this.horaCierre = horaCierre;
    }

    public boolean isActiva() {
        return activa;
    }

    public void setActiva(boolean activa) {
        this.activa = activa;
    }
}
