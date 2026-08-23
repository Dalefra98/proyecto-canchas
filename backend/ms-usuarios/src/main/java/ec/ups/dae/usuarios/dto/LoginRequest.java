package ec.ups.dae.usuarios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de POST /api/usuarios/sesiones. Aqui no se valida la longitud de password: una
 * clave que no cumple la politica actual debe terminar en 401, no en 400, para no revelar
 * politicas ni existencia de cuentas.
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password) {
}
