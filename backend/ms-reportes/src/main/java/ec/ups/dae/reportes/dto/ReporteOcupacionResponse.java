package ec.ups.dae.reportes.dto;

import java.util.List;

/**
 * Respuesta de GET /api/reportes/ocupacion. Envoltura congelada del contrato:
 * { "desde", "hasta", "items" }.
 *
 * desde y hasta son String, no LocalDate: se devuelven exactamente como llegaron en la
 * peticion, sin depender de como Jackson serialice una fecha (design seccion 4.1).
 */
public record ReporteOcupacionResponse(String desde, String hasta, List<OcupacionItem> items) {
}
