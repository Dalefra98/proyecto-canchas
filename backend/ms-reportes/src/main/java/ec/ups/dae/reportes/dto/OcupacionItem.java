package ec.ups.dae.reportes.dto;

import java.math.BigDecimal;

/**
 * Fila del reporte de ocupacion.
 *
 * porcentajeOcupacion es BigDecimal con escala 1 y redondeo HALF_UP (design D-05): un double
 * arrastraria error binario y Jackson podria serializar 26.699999999999999 en vez de 26.7.
 */
public record OcupacionItem(
        Long canchaId,
        String nombre,
        String deporte,
        long horasReservadas,
        long horasDisponibles,
        BigDecimal porcentajeOcupacion) {
}
