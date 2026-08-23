package ec.ups.dae.reservas.dto;

/**
 * Respuesta de GET /api/canchas/{canchaId} de ms-canchas, recortada a lo que ms-reservas
 * necesita: horario de atencion, existencia y estado. nombre y deporte se descartan.
 *
 * DTO de entrada del cliente HTTP: nunca se serializa hacia el cliente final. Spring Boot
 * deja FAIL_ON_UNKNOWN_PROPERTIES en false, asi que los campos sobrantes se ignoran.
 */
public record CanchaExterna(
        Long canchaId,
        String horaApertura,
        String horaCierre,
        boolean activa) {
}
