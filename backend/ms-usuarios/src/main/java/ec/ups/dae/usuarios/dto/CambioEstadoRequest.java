package ec.ups.dae.usuarios.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de PATCH /api/usuarios/{usuarioId}/estado. Boolean y no boolean primitivo: con el
 * primitivo, un cuerpo sin el campo se interpretaria como false e inactivaria al usuario en
 * silencio.
 */
public record CambioEstadoRequest(@NotNull Boolean activo) {
}
