package ec.ups.dae.reservas.exception;

import ec.ups.dae.reservas.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
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
    private static final String SIN_PERMISO = "SIN_PERMISO";
    private static final String NO_ENCONTRADO = "NO_ENCONTRADO";
    private static final String BLOQUE_OCUPADO = "BLOQUE_OCUPADO";
    private static final String LIMITE_RESERVAS = "LIMITE_RESERVAS";
    private static final String RESERVA_PASADA = "RESERVA_PASADA";
    private static final String RESERVA_NO_CANCELABLE = "RESERVA_NO_CANCELABLE";
    private static final String ERROR_INTERNO = "ERROR_INTERNO";

    // Indice parcial de infra/postgres/04-ddl-reservas.sql: es el arbitro de RN-02 cuando
    // dos altas simultaneas pasan la comprobacion previa del servicio (design D-03).
    private static final String UX_RESERVA_BLOQUE_CONFIRMADA = "ux_reserva_bloque_confirmada";

    // Mensaje fijo del fallo de dependencia (design D-06). La causa solo va al log.
    private static final String CATALOGO_CAIDO = "No se pudo consultar el catalogo de canchas";

    private static final String MENSAJE_ERROR_INTERNO = "Ocurrio un error interno en el servidor";

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

    // Falta canchaId o fecha en GET /api/reservas/disponibilidad (HU-01).
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> parametroAusente(MissingServletRequestParameterException excepcion) {
        return respuesta(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS,
                "Falta el parametro obligatorio " + excepcion.getParameterName());
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

    // Parseo estricto fallido de fecha u hora (design D-11).
    @ExceptionHandler(FormatoInvalidoException.class)
    public ResponseEntity<ErrorResponse> formatoInvalido(FormatoInvalidoException excepcion) {
        return respuesta(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS, excepcion.getMessage());
    }

    // horaInicio fuera de hora en punto, o bloque fuera del horario de atencion (HU-02).
    @ExceptionHandler(BloqueInvalidoException.class)
    public ResponseEntity<ErrorResponse> bloqueInvalido(BloqueInvalidoException excepcion) {
        return respuesta(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS, excepcion.getMessage());
    }

    // Alta sobre un bloque que ya ocurrio (decision D-03 del requirements).
    @ExceptionHandler(FechaPasadaException.class)
    public ResponseEntity<ErrorResponse> fechaPasada(FechaPasadaException excepcion) {
        return respuesta(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS, excepcion.getMessage());
    }

    // RN-03: un USUARIO cancelando una reserva de otro.
    @ExceptionHandler(ReservaAjenaException.class)
    public ResponseEntity<ErrorResponse> reservaAjena(ReservaAjenaException excepcion) {
        return respuesta(HttpStatus.FORBIDDEN, SIN_PERMISO, excepcion.getMessage());
    }

    // canchaId inexistente, o inactiva en el alta (decision D-05 del requirements).
    @ExceptionHandler(CanchaNoEncontradaException.class)
    public ResponseEntity<ErrorResponse> canchaNoEncontrada(CanchaNoEncontradaException excepcion) {
        return respuesta(HttpStatus.NOT_FOUND, NO_ENCONTRADO, excepcion.getMessage());
    }

    @ExceptionHandler(ReservaNoEncontradaException.class)
    public ResponseEntity<ErrorResponse> reservaNoEncontrada(ReservaNoEncontradaException excepcion) {
        return respuesta(HttpStatus.NOT_FOUND, NO_ENCONTRADO, excepcion.getMessage());
    }

    // RN-02, y tambien el bloque bajo mantenimiento: mismo codigo (decision D-07).
    @ExceptionHandler(BloqueOcupadoException.class)
    public ResponseEntity<ErrorResponse> bloqueOcupado(BloqueOcupadoException excepcion) {
        return respuesta(HttpStatus.CONFLICT, BLOQUE_OCUPADO, excepcion.getMessage());
    }

    // RN-06: limite de reservas activas simultaneas.
    @ExceptionHandler(LimiteReservasException.class)
    public ResponseEntity<ErrorResponse> limiteReservas(LimiteReservasException excepcion) {
        return respuesta(HttpStatus.CONFLICT, LIMITE_RESERVAS, excepcion.getMessage());
    }

    // RN-04: cancelar una reserva CONFIRMADA que ya ocurrio (consecuencia C-02).
    @ExceptionHandler(ReservaPasadaException.class)
    public ResponseEntity<ErrorResponse> reservaPasada(ReservaPasadaException excepcion) {
        return respuesta(HttpStatus.CONFLICT, RESERVA_PASADA, excepcion.getMessage());
    }

    // Cancelar una reserva que ya esta CANCELADA (decision D-10 del requirements).
    @ExceptionHandler(ReservaNoCancelableException.class)
    public ResponseEntity<ErrorResponse> reservaNoCancelable(ReservaNoCancelableException excepcion) {
        return respuesta(HttpStatus.CONFLICT, RESERVA_NO_CANCELABLE, excepcion.getMessage());
    }

    /**
     * Carrera entre dos altas simultaneas sobre el mismo bloque: el indice parcial de la base
     * es el arbitro. Si la restriccion no se reconoce, es un error no previsto y sale como
     * 500 ERROR_INTERNO (design D-03).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integridadViolada(DataIntegrityViolationException excepcion) {
        String detalle = String.valueOf(excepcion.getMostSpecificCause().getMessage()).toLowerCase();
        if (detalle.contains(UX_RESERVA_BLOQUE_CONFIRMADA)) {
            LOG.warn("Carrera sobre ux_reserva_bloque_confirmada", excepcion);
            return respuesta(HttpStatus.CONFLICT, BLOQUE_OCUPADO,
                    "El bloque horario ya esta reservado");
        }
        LOG.error("Violacion de integridad no prevista", excepcion);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_INTERNO, MENSAJE_ERROR_INTERNO);
    }

    /**
     * ms-canchas caido, con error 5xx, con 401/403 o fuera de plazo. El cliente recibe
     * siempre el mismo mensaje; el detalle real queda en el log (design D-06 y D-08).
     */
    @ExceptionHandler(CatalogoNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> catalogoNoDisponible(CatalogoNoDisponibleException excepcion) {
        LOG.error("Fallo al consultar ms-canchas", excepcion);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_INTERNO, CATALOGO_CAIDO);
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
