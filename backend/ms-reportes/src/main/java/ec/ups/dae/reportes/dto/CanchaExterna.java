package ec.ups.dae.reportes.dto;

/**
 * Respuesta de GET /api/canchas de ms-canchas, recortada a lo que ms-reportes necesita
 * (design D-12). DTO de entrada del cliente HTTP: nunca se serializa hacia el cliente final.
 *
 * activa NO se declara a proposito: los reportes incluyen tambien las canchas inactivas,
 * porque sus reservas historicas son parte del reporte (decision P-09). Declarar el campo
 * sugeriria un filtro que no existe.
 *
 * Spring Boot deja FAIL_ON_UNKNOWN_PROPERTIES en false, asi que los campos sobrantes que
 * envie ms-canchas se ignoran.
 */
public record CanchaExterna(
        Long canchaId,
        String nombre,
        String deporte,
        String horaApertura,
        String horaCierre) {
}
