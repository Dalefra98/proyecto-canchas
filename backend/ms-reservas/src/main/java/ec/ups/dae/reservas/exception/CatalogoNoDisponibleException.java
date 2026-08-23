package ec.ups.dae.reservas.exception;

/**
 * ms-canchas caido, con error 5xx, con 401/403 o fuera de plazo (design D-06 y D-08).
 * Se traduce a 500 ERROR_INTERNO con un mensaje fijo: la causa se registra en el log y
 * nunca viaja al cliente.
 */
public class CatalogoNoDisponibleException extends RuntimeException {

    public CatalogoNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public CatalogoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
