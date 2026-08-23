package ec.ups.dae.reservas.dto;

/**
 * Cada bloqueo devuelto por GET /api/canchas/{canchaId}/bloqueos?fecha de ms-canchas,
 * recortado a la franja. bloqueoId, canchaId y motivo se descartan: ms-reservas solo
 * necesita saber que horas quedan ocupadas.
 */
public record BloqueoExterno(
        String fecha,
        String horaInicio,
        String horaFin) {
}
