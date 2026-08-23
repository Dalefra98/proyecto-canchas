package ec.ups.dae.canchas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/canchas y de PUT /api/canchas/{canchaId}. No declara canchaId ni
 * activa: el identificador lo genera la base y el estado se maneja solo con
 * PATCH /api/canchas/{canchaId}/estado (supuestos S-02 y S-03).
 *
 * Las horas viajan como String para que la respuesta respete el HH:mm congelado y para que
 * un valor mal formado sea un 400 DATOS_INVALIDOS limpio (design D-03).
 */
public record CanchaRequest(
        @NotBlank @Size(max = 80) String nombre,
        @NotBlank @Pattern(regexp = "PADEL|TENIS|BASQUET",
                message = "debe ser PADEL, TENIS o BASQUET") String deporte,
        @NotBlank @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$",
                message = "debe tener formato HH:mm") String horaApertura,
        @NotBlank @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$",
                message = "debe tener formato HH:mm") String horaCierre) {
}
