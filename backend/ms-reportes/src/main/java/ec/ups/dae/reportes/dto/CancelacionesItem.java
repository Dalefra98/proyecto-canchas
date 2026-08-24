package ec.ups.dae.reportes.dto;

/**
 * Fila del reporte de cancelaciones.
 *
 * NO lleva deporte: el payload congelado en docs/contratos/README.md declara solo canchaId,
 * nombre y totalCancelaciones. Agregarlo seria inventar un campo (CLAUDE.md seccion 5).
 */
public record CancelacionesItem(
        Long canchaId,
        String nombre,
        long totalCancelaciones) {
}
