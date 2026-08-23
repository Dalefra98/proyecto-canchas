package ec.ups.dae.usuarios.mapper;

import ec.ups.dae.usuarios.dto.RegistroRequest;
import ec.ups.dae.usuarios.dto.UsuarioResponse;
import ec.ups.dae.usuarios.entity.Rol;
import ec.ups.dae.usuarios.entity.Usuario;
import org.springframework.stereotype.Component;

/**
 * Mapeo manual y explicito: sin Lombok, sin MapStruct, sin reflexion. El unico camino de
 * salida es aRespuesta, que no copia el hash de la contrasena.
 */
@Component
public class UsuarioMapper {

    public Usuario aEntidad(RegistroRequest peticion, String passwordHash) {
        return new Usuario(peticion.nombre(), peticion.email(), passwordHash, Rol.USUARIO, true);
    }

    public UsuarioResponse aRespuesta(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getUsuarioId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRol().name(),
                usuario.isActivo());
    }
}
