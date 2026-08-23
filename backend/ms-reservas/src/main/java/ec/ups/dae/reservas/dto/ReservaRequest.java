package ec.ups.dae.reservas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Cuerpo de POST /api/reservas. Es exactamente { canchaId, fecha, horaInicio }
 * (decision D-11 del requirements): horaFin lo calcula el servicio como horaInicio + 1h.
 *
 * No declara usuarioId, que sale del claim sub del token, ni id ni estado, que los pone el
 * servicio. Si llegan en el cuerpo, Jackson los ignora.
 *
 * El patron de fecha acepta 2026-02-31, asi que el mapper hace ademas un parseo estricto
 * (design D-11).
 */
public record ReservaRequest(
        @NotNull @Positive Long canchaId,
        @NotBlank @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$",
                message = "debe tener formato AAAA-MM-DD") String fecha,
        @NotBlank @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$",
                message = "debe tener formato HH:mm") String horaInicio) {
}
