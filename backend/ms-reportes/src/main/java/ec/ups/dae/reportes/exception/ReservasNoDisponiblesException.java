package ec.ups.dae.reportes.exception;

/**
 * ms-reservas caido, con error 5xx, con 401/403 o fuera de plazo. Sale como
 * 500 ERROR_INTERNO con su propio mensaje fijo, distinto al del catalogo, para que el log y
 * la respuesta digan cual de las dos dependencias fallo.
 */
public class ReservasNoDisponiblesException extends RuntimeException {

    public ReservasNoDisponiblesException(String mensaje) {
        super(mensaje);
    }

    public ReservasNoDisponiblesException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
