package ec.ups.dae.canchas.mapper;

import ec.ups.dae.canchas.dto.BloqueoRequest;
import ec.ups.dae.canchas.dto.BloqueoResponse;
import ec.ups.dae.canchas.entity.BloqueoMantenimiento;
import ec.ups.dae.canchas.exception.FormatoInvalidoException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import org.springframework.stereotype.Component;

/**
 * Mapeo manual y explicito de los bloqueos de mantenimiento.
 *
 * La fecha se parsea con ResolverStyle.STRICT para que 2026-02-31 no pase: el @Pattern del
 * DTO solo comprueba la forma AAAA-MM-DD (design D-04).
 */
@Component
public class BloqueoMapper {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final CanchaMapper canchaMapper;

    public BloqueoMapper(CanchaMapper canchaMapper) {
        this.canchaMapper = canchaMapper;
    }

    public BloqueoMantenimiento aEntidad(Long canchaId, BloqueoRequest peticion) {
        return new BloqueoMantenimiento(
                canchaId,
                aFecha(peticion.fecha(), "fecha"),
                canchaMapper.aHora(peticion.horaInicio(), "horaInicio"),
                canchaMapper.aHora(peticion.horaFin(), "horaFin"),
                peticion.motivo());
    }

    public BloqueoResponse aRespuesta(BloqueoMantenimiento bloqueo) {
        return new BloqueoResponse(
                bloqueo.getBloqueoId(),
                bloqueo.getCanchaId(),
                bloqueo.getFecha().format(FECHA),
                bloqueo.getHoraInicio().format(HORA),
                bloqueo.getHoraFin().format(HORA),
                bloqueo.getMotivo());
    }

    public LocalDate aFecha(String valor, String campo) {
        try {
            return LocalDate.parse(valor, FECHA);
        } catch (DateTimeParseException excepcion) {
            throw new FormatoInvalidoException(
                    "El campo " + campo + " debe ser una fecha valida en formato AAAA-MM-DD");
        }
    }
}
