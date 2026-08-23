package ec.ups.dae.usuarios.dto;

/**
 * Payload congelado LoginResponse del contrato: token + usuario.
 */
public record LoginResponse(String token, UsuarioResponse usuario) {
}
