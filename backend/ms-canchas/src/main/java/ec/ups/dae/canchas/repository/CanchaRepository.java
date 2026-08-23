package ec.ups.dae.canchas.repository;

import ec.ups.dae.canchas.entity.Cancha;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Unico punto de acceso a la tabla cancha de canchas_db. Ninguna consulta toca tablas de
 * otro microservicio: la integracion con usuarios y reservas es via REST.
 */
public interface CanchaRepository extends JpaRepository<Cancha, Long> {

    /** Listado que ve el USUARIO: solo canchas activas (HU-01, decision P-05). */
    List<Cancha> findByActivaTrue();

    /** Verificacion previa del alta (HU-03, decision P-01). */
    boolean existsByNombre(String nombre);

    /** Verificacion previa de la edicion: excluye la propia cancha (HU-04). */
    boolean existsByNombreAndCanchaIdNot(String nombre, Long canchaId);
}
