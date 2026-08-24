package ec.ups.dae.canchas.exception;

import ec.ups.dae.canchas.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce toda excepcion al formato { "codigo", "mensaje" } del contrato. El cliente nunca
 * recibe stacktrace, nombre de clase Java ni consulta SQL.
 */
@RestControllerAdvice
public class ManejadorExcepciones {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorExcepciones.class);

    private static final String DATOS_INVALIDOS = "DATOS_INVALIDOS";
    private static final String NO_ENCONTRADO = "NO_ENCONTRADO";
    private static final String NOMBRE_DUPLICADO = "NOMBRE_DUPLICADO";
    private static final String BLOQUEO_DUPLICADO = "BLOQUEO_DUPLICADO";
    private static final String ERROR_INTERNO = "ERROR_INTERNO";

    // Nombres de las restricciones de infra/postgres/03-ddl-canchas.sql, para distinguir
    // cual salto en una carrera entre dos peticiones simultaneas (design D-08).
    private static final String UQ_CANCHA_NOMBRE = "uq_cancha_nombre";
    private static final String UQ_BLOQUEO_FRANJA = "uq_bloqueo_franja";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> datosInvalidos(MethodArgumentNotValidException excepcion) {
        String detalle = excepcion.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((uno, otro) -> uno + "; " + otro)
                .orElse("Datos de entrada invalidos");
        return respuesta(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS, detalle);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> cuerpoIlegible(HttpMessageNotReadableException excepcion) {
        return respuesta(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS,
                "El cuerpo de la peticion no es un JSON valido para esta operacion");
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

    // El 415 se traduce a 400 DATOS_INVALIDOS por la misma razon.
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> tipoDeContenidoNoSoportado(HttpMediaTypeNotSupportedException excepcion) {
        return respuesta(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS,
                "El tipo de contenido debe ser application/json");
    }

    // Parseo estricto fallido de fecha u hora, o deporte fuera del enum (design D-04).
    @ExceptionHandler(FormatoInvalidoException.class)
    public ResponseEntity<ErrorResponse> formatoInvalido(FormatoInvalidoException excepcion) {
        return respuesta(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS, excepcion.getMessage());
    }

    // horaCierre <= horaApertura en una cancha, u horaFin <= horaInicio en un bloqueo.
    @ExceptionHandler(HorarioInvalidoException.class)
    public ResponseEntity<ErrorResponse> horarioInvalido(HorarioInvalidoException excepcion) {
        return respuesta(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS, excepcion.getMessage());
    }

    // Bloqueo fuera del horario de atencion de su cancha (decision P-02.b).
    @ExceptionHandler(FueraDeHorarioException.class)
    public ResponseEntity<ErrorResponse> fueraDeHorario(FueraDeHorarioException excepcion) {
        return respuesta(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS, excepcion.getMessage());
    }

    // Bloqueo con fecha anterior a hoy (decision P-02.c).
    @ExceptionHandler(FechaPasadaException.class)
    public ResponseEntity<ErrorResponse> fechaPasada(FechaPasadaException excepcion) {
        return respuesta(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS, excepcion.getMessage());
    }

    @ExceptionHandler(CanchaNoEncontradaException.class)
    public ResponseEntity<ErrorResponse> canchaNoEncontrada(CanchaNoEncontradaException excepcion) {
        return respuesta(HttpStatus.NOT_FOUND, NO_ENCONTRADO, excepcion.getMessage());
    }

    @ExceptionHandler(BloqueoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> bloqueoNoEncontrado(BloqueoNoEncontradoException excepcion) {
        return respuesta(HttpStatus.NOT_FOUND, NO_ENCONTRADO, excepcion.getMessage());
    }

    @ExceptionHandler(NombreDuplicadoException.class)
    public ResponseEntity<ErrorResponse> nombreDuplicado(NombreDuplicadoException excepcion) {
        return respuesta(HttpStatus.CONFLICT, NOMBRE_DUPLICADO, excepcion.getMessage());
    }

    @ExceptionHandler(BloqueoDuplicadoException.class)
    public ResponseEntity<ErrorResponse> bloqueoDuplicado(BloqueoDuplicadoException excepcion) {
        return respuesta(HttpStatus.CONFLICT, BLOQUEO_DUPLICADO, excepcion.getMessage());
    }

    /**
     * Carrera entre dos peticiones simultaneas: la restriccion de la base es el arbitro. Se
     * distingue cual salto por su nombre; si no se reconoce ninguna, es un error no previsto
     * y sale como 500 ERROR_INTERNO (design D-08).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integridadViolada(DataIntegrityViolationException excepcion) {
        String detalle = String.valueOf(excepcion.getMostSpecificCause().getMessage()).toLowerCase();
        if (detalle.contains(UQ_CANCHA_NOMBRE)) {
            LOG.warn("Carrera sobre uq_cancha_nombre", excepcion);
            return respuesta(HttpStatus.CONFLICT, NOMBRE_DUPLICADO,
                    "Ya existe una cancha con ese nombre");
        }
        if (detalle.contains(UQ_BLOQUEO_FRANJA)) {
            LOG.warn("Carrera sobre uq_bloqueo_franja", excepcion);
            return respuesta(HttpStatus.CONFLICT, BLOQUEO_DUPLICADO,
                    "Ya existe un bloqueo en esa franja para la cancha");
        }
        LOG.error("Violacion de integridad no prevista", excepcion);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_INTERNO,
                "Ocurrio un error interno en el servidor");
    }

    /**
     * Ruta inexistente (asunto A-02 de la spec 04). Spring lanza NoResourceFoundException al no
     * encontrar handler ni recurso estatico; sin este manejador caeria en la red de seguridad y
     * saldria como 500. Un recurso que no existe es un 404, en los tres microservicios.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> rutaNoEncontrada(NoResourceFoundException excepcion) {
        return respuesta(HttpStatus.NOT_FOUND, NO_ENCONTRADO, "El recurso solicitado no existe");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> errorInterno(Exception excepcion) {
        LOG.error("Error no previsto", excepcion);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_INTERNO,
                "Ocurrio un error interno en el servidor");
    }

    private ResponseEntity<ErrorResponse> respuesta(HttpStatus estado, String codigo, String mensaje) {
        return ResponseEntity.status(estado).body(new ErrorResponse(codigo, mensaje));
    }
}
