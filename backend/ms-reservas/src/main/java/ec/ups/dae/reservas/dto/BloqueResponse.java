package ec.ups.dae.reservas.dto;

/**
 * Cada elemento del arreglo bloques de DisponibilidadResponse. Nombres congelados en el
 * contrato: horaInicio, horaFin y disponible.
 */
public record BloqueResponse(
        String horaInicio,
        String horaFin,
        boolean disponible) {
}
