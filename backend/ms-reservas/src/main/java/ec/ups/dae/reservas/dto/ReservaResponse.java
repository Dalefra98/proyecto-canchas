package ec.ups.dae.reservas.dto;

/**
 * Respuesta del alta, del historial propio, del listado global y de la cancelacion.
 *
 * estado NO es siempre el valor persistido: FINALIZADA se calcula al leer y nunca se guarda
 * (design D-02 y D-15). El calculo vive en ReservaMapper.
 */
public record ReservaResponse(
        Long id,
        Long usuarioId,
        Long canchaId,
        String fecha,
        String horaInicio,
        String horaFin,
        String estado) {
}
