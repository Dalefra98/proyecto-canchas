package ec.ups.dae.usuarios.exception;

public class EmailDuplicadoException extends RuntimeException {

    public EmailDuplicadoException(String mensaje) {
        super(mensaje);
    }
}
