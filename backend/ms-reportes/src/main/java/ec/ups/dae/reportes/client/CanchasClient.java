package ec.ups.dae.reportes.client;

import ec.ups.dae.reportes.dto.CanchaExterna;
import ec.ups.dae.reportes.exception.CatalogoNoDisponibleException;
import ec.ups.dae.reportes.service.EmisorTokenServicio;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Unico punto de ms-reportes que llama a ms-canchas. Nadie lee canchas_db: la integracion
 * entre microservicios es por REST (CLAUDE.md seccion 3).
 *
 * La llamada va autenticada con el token de servicio, que ademas es lo que hace que
 * ms-canchas devuelva la vista completa del catalogo, incluidas las canchas con
 * activa = false. Los reportes las necesitan porque sus reservas historicas cuentan
 * (decision P-09).
 *
 * Cualquier fallo —5xx, 401, 403, timeout o error de conexion— sale como
 * CatalogoNoDisponibleException, que el manejador traduce a 500 ERROR_INTERNO con un
 * mensaje fijo (design D-08). Un 401 o 403 aqui significa que el token de servicio esta mal
 * configurado: es un defecto nuestro, no del cliente final.
 */
@Component
public class CanchasClient {

    private static final String RUTA_CANCHAS = "/api/canchas";

    private final RestClient clienteCanchas;
    private final EmisorTokenServicio emisorTokenServicio;

    public CanchasClient(@Qualifier("clienteCanchas") RestClient clienteCanchas,
                         EmisorTokenServicio emisorTokenServicio) {
        this.clienteCanchas = clienteCanchas;
        this.emisorTokenServicio = emisorTokenServicio;
    }

    /** Catalogo completo: es la fuente de las filas de los tres reportes. */
    public List<CanchaExterna> listarCanchas() {
        try {
            return clienteCanchas.get()
                    .uri(RUTA_CANCHAS)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisorTokenServicio.emitir())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (peticion, respuesta) -> {
                        throw new CatalogoNoDisponibleException(
                                "ms-canchas respondio " + respuesta.getStatusCode() + " al pedir el catalogo");
                    })
                    .body(new ParameterizedTypeReference<List<CanchaExterna>>() { });
        } catch (CatalogoNoDisponibleException excepcion) {
            throw excepcion;
        } catch (RuntimeException excepcion) {
            throw new CatalogoNoDisponibleException("No se pudo contactar a ms-canchas", excepcion);
        }
    }
}
