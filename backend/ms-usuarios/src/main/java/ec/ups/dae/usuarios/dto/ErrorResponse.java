package ec.ups.dae.usuarios.dto;

/**
 * Formato de error unico de todos los microservicios: { "codigo", "mensaje" }.
 */
public record ErrorResponse(String codigo, String mensaje) {
}
