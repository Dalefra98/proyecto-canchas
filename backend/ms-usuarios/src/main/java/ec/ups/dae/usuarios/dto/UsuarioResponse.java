package ec.ups.dae.usuarios.dto;

/**
 * Respuesta de registro, listado y cambio de estado. No declara password ni passwordHash:
 * la omision es estructural, no depende de una anotacion.
 */
public record UsuarioResponse(
        Long usuarioId,
        String nombre,
        String email,
        String rol,
        boolean activo) {
}
