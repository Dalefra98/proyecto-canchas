package ec.ups.dae.reservas.dto;

import java.util.List;

/**
 * Payload congelado DisponibilidadResponse del contrato: canchaId, fecha, horaApertura,
 * horaCierre y bloques. El horario de atencion lo aporta ms-canchas por HTTP; los bloques
 * los calcula DisponibilidadService.
 */
public record DisponibilidadResponse(
        Long canchaId,
        String fecha,
        String horaApertura,
        String horaCierre,
        List<BloqueResponse> bloques) {
}
