package ec.ups.dae.reportes.client;

import ec.ups.dae.reportes.dto.ReservaExterna;
import ec.ups.dae.reportes.exception.ReservasNoDisponiblesException;
import ec.ups.dae.reportes.service.EmisorTokenServicio;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Unico punto de ms-reportes que llama a ms-reservas. Nadie lee reservas_db.
 *
 * Trae el listado global completo en cada peticion y el filtrado por rango se hace despues,
 * en memoria: GET /api/reservas esta congelado sin parametros y no se le agregan (decision
 * P-02, design D-07). Esto NO escala, y esta asumido por escrito en el requirements seccion
 * 7.1: es aceptable para el alcance academico de este proyecto.
 *
 * La llamada va autenticada con el token de servicio, que ms-reservas acepta en esta unica
 * ruta desde la decision P-01.
 */
@Component
public class ReservasClient {

    private static final String RUTA_RESERVAS = "/api/reservas";

    private final RestClient clienteReservas;
    private final EmisorTokenServicio emisorTokenServicio;

    public ReservasClient(@Qualifier("clienteReservas") RestClient clienteReservas,
                          EmisorTokenServicio emisorTokenServicio) {
        this.clienteReservas = clienteReservas;
        this.emisorTokenServicio = emisorTokenServicio;
    }

    /** Todas las reservas del sistema, en todos los estados. */
    public List<ReservaExterna> listarTodas() {
        try {
            return clienteReservas.get()
                    .uri(RUTA_RESERVAS)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisorTokenServicio.emitir())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (peticion, respuesta) -> {
                        throw new ReservasNoDisponiblesException(
                                "ms-reservas respondio " + respuesta.getStatusCode()
                                        + " al pedir el listado global");
                    })
                    .body(new ParameterizedTypeReference<List<ReservaExterna>>() { });
        } catch (ReservasNoDisponiblesException excepcion) {
            throw excepcion;
        } catch (RuntimeException excepcion) {
            throw new ReservasNoDisponiblesException("No se pudo contactar a ms-reservas", excepcion);
        }
    }
}
