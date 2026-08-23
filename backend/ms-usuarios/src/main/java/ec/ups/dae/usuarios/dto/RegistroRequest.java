package ec.ups.dae.usuarios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/usuarios. No declara rol ni activo: el registro publico siempre crea
 * un USUARIO activo, asi que un rol enviado por el cliente no tiene donde entrar.
 */
public record RegistroRequest(
        @NotBlank @Size(max = 80) String nombre,
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Size(min = 8, max = 100) String password) {
}
