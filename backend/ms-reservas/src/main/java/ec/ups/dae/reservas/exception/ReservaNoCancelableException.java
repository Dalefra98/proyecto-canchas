package ec.ups.dae.reservas.exception;

public class ReservaNoCancelableException extends RuntimeException {

    public ReservaNoCancelableException(String mensaje) {
        super(mensaje);
    }
}
