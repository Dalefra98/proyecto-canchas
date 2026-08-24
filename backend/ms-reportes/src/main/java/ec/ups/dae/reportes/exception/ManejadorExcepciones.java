package ec.ups.dae.reportes.exception;

import ec.ups.dae.reportes.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce toda excepcion al formato { "codigo", "mensaje" } del contrato. El cliente nunca
 * recibe stacktrace, nombre de clase Java ni el cuerpo que devolvio otro microservicio.
 *
 * Copia del de ms-reservas, recortada y ampliada segun el design seccion 6:
 * - Se eliminan los manejadores de las excepciones de dominio de reservas y el de
 *   DataIntegrityViolationException: aqui no hay base de datos.
 * - Se eliminan tambien HttpMessageNotReadableException y HttpMediaTypeNotSupportedException:
 *   los tres endpoints son GET sin cuerpo de peticion.
 * - Se agregan RangoInvalidoException, CatalogoNoDisponibleException y
 *   ReservasNoDisponiblesException.
 */
@RestControllerAdvice
public class ManejadorExcepciones {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorExcepciones.class);

    private static final String DATOS_INVALIDOS = "DATOS_INVALIDOS";
    private static final String NO_ENCONTRADO = "NO_ENCONTRADO";
    private static final String ERROR_INTERNO = "ERROR_INTERNO";

    // Mensajes fijos de los dos fallos de dependencia (design seccion 6). La causa real solo
    // va al log: al cliente no se le filtra el estado ni el cuerpo del servicio caido.
    private static final String CATALOGO_CAIDO = "No se pudo consultar el catalogo de canchas";
    private static final String RESERVAS_CAIDAS = "No se pudo consultar las reservas";

    private static final String MENSAJE_ERROR_INTERNO = "Ocurrio un error interno en el servidor";

    // Falta desde o hasta: los dos son obligatorios en los tres reportes (HU-04).
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> parametroAusente(MissingServletRequestParameterException excepcion) {
        return respuesta(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS,
                "Falta el parametro obligatorio " + excepcion.getParameterName());
    }

    // Formato distinto de AAAA-MM-DD, fecha inexistente, o desde posterior a hasta (HU-04).
    @ExceptionHandler(RangoInvalidoException.class)
    public ResponseEntity<ErrorResponse> rangoInvalido(RangoInvalidoException excepcion) {
        return respuesta(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS, excepcion.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> tipoIncorrecto(MethodArgumentTypeMismatchException excepcion) {
        return respuesta(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS,
                "El parametro " + excepcion.getName() + " no tiene el tipo esperado");
    }

    // El 405 se traduce a 400 DATOS_INVALIDOS: es un error del cliente, no del servidor.
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> verboNoSoportado(HttpRequestMethodNotSupportedException excepcion) {
        return respuesta(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS,
                "El metodo " + excepcion.getMethod() + " no aplica a esta ruta");
    }

    /**
     * ms-canchas no respondio. Nunca se devuelve un reporte parcial: un reporte con la mitad
     * de los datos y aspecto de completo es peor que un error visible (design D-08).
     */
    @ExceptionHandler(CatalogoNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> catalogoNoDisponible(CatalogoNoDisponibleException excepcion) {
        LOG.error("Fallo al consultar ms-canchas", excepcion);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_INTERNO, CATALOGO_CAIDO);
    }

    /** ms-reservas no respondio. Mismo criterio que el catalogo (design D-08). */
    @ExceptionHandler(ReservasNoDisponiblesException.class)
    public ResponseEntity<ErrorResponse> reservasNoDisponibles(ReservasNoDisponiblesException excepcion) {
        LOG.error("Fallo al consultar ms-reservas", excepcion);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_INTERNO, RESERVAS_CAIDAS);
    }

    /**
     * Ruta inexistente (asunto A-02, cerrado en los otros tres microservicios en la T10 de la
     * spec 04). Sin este manejador caeria en la red de seguridad y saldria como 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> rutaNoEncontrada(NoResourceFoundException excepcion) {
        return respuesta(HttpStatus.NOT_FOUND, NO_ENCONTRADO, "El recurso solicitado no existe");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> errorInterno(Exception excepcion) {
        LOG.error("Error no previsto", excepcion);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_INTERNO, MENSAJE_ERROR_INTERNO);
    }

    private ResponseEntity<ErrorResponse> respuesta(HttpStatus estado, String codigo, String mensaje) {
        return ResponseEntity.status(estado).body(new ErrorResponse(codigo, mensaje));
    }
}
