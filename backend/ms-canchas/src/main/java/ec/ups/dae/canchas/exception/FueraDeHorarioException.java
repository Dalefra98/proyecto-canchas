package ec.ups.dae.canchas.exception;

public class FueraDeHorarioException extends RuntimeException {

    public FueraDeHorarioException(String mensaje) {
        super(mensaje);
    }
}
