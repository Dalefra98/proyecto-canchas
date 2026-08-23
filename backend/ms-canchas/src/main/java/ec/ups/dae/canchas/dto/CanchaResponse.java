package ec.ups.dae.canchas.dto;

/**
 * Respuesta de listado, detalle, alta, edicion y cambio de estado. Nombres exactos del
 * contrato congelado; horaApertura y horaCierre en formato HH:mm.
 */
public record CanchaResponse(
        Long canchaId,
        String nombre,
        String deporte,
        String horaApertura,
        String horaCierre,
        boolean activa) {
}
