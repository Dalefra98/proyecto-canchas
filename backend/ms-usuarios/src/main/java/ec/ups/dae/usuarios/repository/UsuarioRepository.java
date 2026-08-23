package ec.ups.dae.usuarios.repository;

import ec.ups.dae.usuarios.entity.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Unico punto de acceso a la tabla usuario de usuarios_db. Ninguna consulta toca tablas de
 * otro microservicio: la integracion con canchas y reservas es via REST.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
