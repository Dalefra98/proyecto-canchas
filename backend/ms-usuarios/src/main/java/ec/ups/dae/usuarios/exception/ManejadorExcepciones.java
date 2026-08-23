package ec.ups.dae.usuarios.exception;

import ec.ups.dae.usuarios.dto.ErrorResponse;
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

/**
 * Traduce toda excepcion al formato { "codigo", "mensaje" } del contrato. El cliente nunca
 * recibe stacktrace, nombre de clase Java ni consulta SQL.
 */
@RestControllerAdvice
public class ManejadorExcepciones {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorExcepciones.class);

    private static final String DATOS_INVALIDOS = "DATOS_INVALIDOS";
    private static final String NO_AUTENTICADO = "NO_AUTENTICADO";
    private static final String NO_ENCONTRADO = "NO_ENCONTRADO";
    private static final String EMAIL_DUPLICADO = "EMAIL_DUPLICADO";
    private static final String ERROR_INTERNO = "ERROR_INTERNO";

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

    @ExceptionHandler(AutoInactivacionException.class)
    public ResponseEntity<ErrorResponse> autoInactivacion(AutoInactivacionException excepcion) {
        return respuesta(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS, excepcion.getMessage());
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorResponse> credencialesInvalidas(CredencialesInvalidasException excepcion) {
        return respuesta(HttpStatus.UNAUTHORIZED, NO_AUTENTICADO, excepcion.getMessage());
    }

    @ExceptionHandler(UsuarioNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> usuarioNoEncontrado(UsuarioNoEncontradoException excepcion) {
        return respuesta(HttpStatus.NOT_FOUND, NO_ENCONTRADO, excepcion.getMessage());
    }

    @ExceptionHandler(EmailDuplicadoException.class)
    public ResponseEntity<ErrorResponse> emailDuplicado(EmailDuplicadoException excepcion) {
        return respuesta(HttpStatus.CONFLICT, EMAIL_DUPLICADO, excepcion.getMessage());
    }

    // Carrera entre dos registros simultaneos: la restriccion uq_usuario_email es el arbitro.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integridadViolada(DataIntegrityViolationException excepcion) {
        LOG.warn("Violacion de integridad al guardar un usuario", excepcion);
        return respuesta(HttpStatus.CONFLICT, EMAIL_DUPLICADO, "El correo ya esta registrado");
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
