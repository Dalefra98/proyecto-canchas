package ec.ups.dae.canchas.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de PATCH /api/canchas/{canchaId}/estado.
 *
 * activa es Boolean y no boolean: con el primitivo, un cuerpo sin el campo se interpretaria
 * como false e inactivaria la cancha en silencio (design D-05 de la spec 02).
 */
public record CambioEstadoCanchaRequest(@NotNull Boolean activa) {
}
