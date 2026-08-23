package ec.ups.dae.canchas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/canchas/{canchaId}/bloqueos. No declara canchaId, que viene de la
 * ruta, ni bloqueoId, que lo genera la base.
 *
 * El patron de fecha acepta 2026-02-31, asi que el mapper hace ademas un parseo estricto
 * (design D-04).
 */
public record BloqueoRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$",
                message = "debe tener formato AAAA-MM-DD") String fecha,
        @NotBlank @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$",
                message = "debe tener formato HH:mm") String horaInicio,
        @NotBlank @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$",
                message = "debe tener formato HH:mm") String horaFin,
        @NotBlank @Size(max = 200) String motivo) {
}
