package ec.ups.dae.reportes.exception;

/**
 * ms-canchas caido, con error 5xx, con 401/403 o fuera de plazo. Sale como
 * 500 ERROR_INTERNO con un mensaje fijo; la causa real solo va al log.
 *
 * Un 401 o 403 aqui significa que el token de servicio esta mal configurado: es un defecto
 * nuestro, no del cliente final.
 */
public class CatalogoNoDisponibleException extends RuntimeException {

    public CatalogoNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public CatalogoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
