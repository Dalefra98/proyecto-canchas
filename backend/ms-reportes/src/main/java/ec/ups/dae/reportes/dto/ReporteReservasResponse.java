package ec.ups.dae.reportes.dto;

import java.util.List;

/** Respuesta de GET /api/reportes/reservas. Misma envoltura congelada del contrato. */
public record ReporteReservasResponse(String desde, String hasta, List<ReservasItem> items) {
}
