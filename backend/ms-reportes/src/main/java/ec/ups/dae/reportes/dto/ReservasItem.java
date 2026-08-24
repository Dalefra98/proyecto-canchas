package ec.ups.dae.reportes.dto;

/** Fila del reporte de reservas por periodo. */
public record ReservasItem(
        Long canchaId,
        String nombre,
        String deporte,
        long totalReservas) {
}
