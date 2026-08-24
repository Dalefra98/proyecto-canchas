package ec.ups.dae.reportes.exception;

/**
 * Rango desde/hasta mal formado: formato distinto de AAAA-MM-DD, fecha inexistente, o desde
 * posterior a hasta. Sale como 400 DATOS_INVALIDOS.
 *
 * Una sola excepcion para los tres casos: lo que cambia es el mensaje, no el tratamiento
 * (design seccion 6).
 */
public class RangoInvalidoException extends RuntimeException {

    public RangoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
