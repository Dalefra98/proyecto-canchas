package ec.ups.dae.reportes.dto;

import java.util.List;

/** Respuesta de GET /api/reportes/cancelaciones. Misma envoltura congelada del contrato. */
public record ReporteCancelacionesResponse(String desde, String hasta, List<CancelacionesItem> items) {
}
