package ec.ups.dae.reservas.service;

import ec.ups.dae.reservas.dto.BloqueoExterno;
import ec.ups.dae.reservas.dto.CanchaExterna;
import ec.ups.dae.reservas.exception.CanchaNoEncontradaException;
import ec.ups.dae.reservas.exception.CatalogoNoDisponibleException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Unico punto de ms-reservas que llama a ms-canchas. Ninguna otra clase hace HTTP, y nadie
 * lee canchas_db: la integracion entre microservicios es por REST (CLAUDE.md seccion 3).
 *
 * Toda llamada va autenticada con el token de servicio (rol SERVICIO, sin sub, exp de 5
 * minutos). El token del usuario final NUNCA se reenvia: ms-reservas necesita ver tambien las
 * canchas inactivas, que un USUARIO no veria (decision C-01 de la spec 03, design D-01).
 *
 * Traduccion de fallos (design D-06 y D-08):
 * - 404 de ms-canchas -> CanchaNoEncontradaException, que es un 404 legitimo del cliente.
 * - 5xx, 401, 403, timeout o error de conexion -> CatalogoNoDisponibleException, que sale
 *   como 500 ERROR_INTERNO con un mensaje fijo. Un 401 o 403 aqui significa que el token de
 *   servicio esta mal configurado: es un defecto nuestro, no del cliente final.
 */
@Service
public class CanchasClient {

    private static final String RUTA_CANCHA = "/api/canchas/{canchaId}";
    private static final String RUTA_BLOQUEOS = "/api/canchas/{canchaId}/bloqueos?fecha={fecha}";

    private final RestClient clienteCanchas;
    private final EmisorTokenServicio emisorTokenServicio;

    public CanchasClient(RestClient clienteCanchas, EmisorTokenServicio emisorTokenServicio) {
        this.clienteCanchas = clienteCanchas;
        this.emisorTokenServicio = emisorTokenServicio;
    }

    /** Horario de atencion, existencia y estado de la cancha (HU-01, HU-02). */
    public CanchaExterna obtenerCancha(Long canchaId) {
        try {
            return clienteCanchas.get()
                    .uri(RUTA_CANCHA, canchaId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisorTokenServicio.emitir())
                    .retrieve()
                    .onStatus(estado -> estado.value() == 404, (peticion, respuesta) -> {
                        throw new CanchaNoEncontradaException("La cancha no existe");
                    })
                    .onStatus(HttpStatusCode::isError, (peticion, respuesta) -> {
                        throw new CatalogoNoDisponibleException(
                                "ms-canchas respondio " + respuesta.getStatusCode() + " al pedir la cancha "
                                        + canchaId);
                    })
                    .body(CanchaExterna.class);
        } catch (CanchaNoEncontradaException | CatalogoNoDisponibleException excepcion) {
            throw excepcion;
        } catch (RuntimeException excepcion) {
            throw new CatalogoNoDisponibleException(
                    "No se pudo contactar a ms-canchas al pedir la cancha " + canchaId, excepcion);
        }
    }

    /**
     * Bloqueos de mantenimiento de esa cancha y ese dia. Usa el parametro opcional ?fecha que
     * la spec 03 congelo en el contrato justamente para esto (decision P-06 de esa spec).
     */
    public List<BloqueoExterno> listarBloqueos(Long canchaId, LocalDate fecha) {
        try {
            return clienteCanchas.get()
                    .uri(RUTA_BLOQUEOS, canchaId, fecha.toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisorTokenServicio.emitir())
                    .retrieve()
                    .onStatus(estado -> estado.value() == 404, (peticion, respuesta) -> {
                        throw new CanchaNoEncontradaException("La cancha no existe");
                    })
                    .onStatus(HttpStatusCode::isError, (peticion, respuesta) -> {
                        throw new CatalogoNoDisponibleException(
                                "ms-canchas respondio " + respuesta.getStatusCode()
                                        + " al pedir los bloqueos de la cancha " + canchaId);
                    })
                    .body(new ParameterizedTypeReference<List<BloqueoExterno>>() { });
        } catch (CanchaNoEncontradaException | CatalogoNoDisponibleException excepcion) {
            throw excepcion;
        } catch (RuntimeException excepcion) {
            throw new CatalogoNoDisponibleException(
                    "No se pudo contactar a ms-canchas al pedir los bloqueos de la cancha " + canchaId,
                    excepcion);
        }
    }
}
