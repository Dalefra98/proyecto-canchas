package ec.ups.dae.reportes.dto;

/**
 * Fila de GET /api/reservas de ms-reservas, recortada a los tres campos que los reportes
 * usan (design D-12).
 *
 * No se declaran id, usuarioId, horaInicio ni horaFin: ningun reporte lleva datos de
 * usuario, y cada reserva vale exactamente una hora por RN-01, asi que las horas no
 * intervienen en ningun calculo.
 *
 * estado llega ya resuelto por ms-reservas, que calcula FINALIZADA al leer sin persistirlo
 * (decision D-02 de la spec 04). ms-reportes no lo recalcula.
 */
public record ReservaExterna(
        Long canchaId,
        String fecha,
        String estado) {
}
