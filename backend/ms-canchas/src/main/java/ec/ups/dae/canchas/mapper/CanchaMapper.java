package ec.ups.dae.canchas.mapper;

import ec.ups.dae.canchas.dto.CanchaRequest;
import ec.ups.dae.canchas.dto.CanchaResponse;
import ec.ups.dae.canchas.entity.Cancha;
import ec.ups.dae.canchas.entity.Deporte;
import ec.ups.dae.canchas.exception.FormatoInvalidoException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

/**
 * Mapeo manual y explicito: sin Lombok, sin MapStruct, sin reflexion.
 *
 * Las horas se formatean a HH:mm y se parsean con DateTimeFormatter (design D-03 y D-04):
 * el @Pattern del DTO valida la forma, y este parseo es la verificacion real del valor.
 */
@Component
public class CanchaMapper {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public Cancha aEntidad(CanchaRequest peticion) {
        return new Cancha(
                peticion.nombre(),
                aDeporte(peticion.deporte()),
                aHora(peticion.horaApertura(), "horaApertura"),
                aHora(peticion.horaCierre(), "horaCierre"),
                true);
    }

    /** Copia sobre una cancha existente los cuatro campos editables. No toca activa (S-03). */
    public void copiarSobre(Cancha cancha, CanchaRequest peticion) {
        cancha.setNombre(peticion.nombre());
        cancha.setDeporte(aDeporte(peticion.deporte()));
        cancha.setHoraApertura(aHora(peticion.horaApertura(), "horaApertura"));
        cancha.setHoraCierre(aHora(peticion.horaCierre(), "horaCierre"));
    }

    public CanchaResponse aRespuesta(Cancha cancha) {
        return new CanchaResponse(
                cancha.getCanchaId(),
                cancha.getNombre(),
                cancha.getDeporte().name(),
                cancha.getHoraApertura().format(HORA),
                cancha.getHoraCierre().format(HORA),
                cancha.isActiva());
    }

    public LocalTime aHora(String valor, String campo) {
        try {
            return LocalTime.parse(valor, HORA);
        } catch (DateTimeParseException excepcion) {
            throw new FormatoInvalidoException("El campo " + campo + " debe tener formato HH:mm");
        }
    }

    private Deporte aDeporte(String valor) {
        try {
            return Deporte.valueOf(valor);
        } catch (IllegalArgumentException excepcion) {
            throw new FormatoInvalidoException("El campo deporte debe ser PADEL, TENIS o BASQUET");
        }
    }
}
