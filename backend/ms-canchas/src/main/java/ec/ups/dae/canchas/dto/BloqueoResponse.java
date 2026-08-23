package ec.ups.dae.canchas.dto;

/**
 * Respuesta de listado y de alta de bloqueos. Composicion exacta de las "Notas de uso" del
 * contrato: bloqueoId, canchaId, fecha, horaInicio, horaFin y motivo.
 */
public record BloqueoResponse(
        Long bloqueoId,
        Long canchaId,
        String fecha,
        String horaInicio,
        String horaFin,
        String motivo) {
}
