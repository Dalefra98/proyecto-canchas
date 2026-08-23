package ec.ups.dae.reservas.mapper;

import ec.ups.dae.reservas.dto.ReservaResponse;
import ec.ups.dae.reservas.entity.EstadoReserva;
import ec.ups.dae.reservas.entity.Reserva;
import ec.ups.dae.reservas.exception.FormatoInvalidoException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import org.springframework.stereotype.Component;

/**
 * Mapeo manual y explicito de las reservas, mas el parseo estricto de fecha y hora.
 *
 * La fecha se parsea con ResolverStyle.STRICT para que 2026-02-31 no pase: el @Pattern del
 * DTO solo comprueba la forma AAAA-MM-DD (design D-11).
 */
@Component
public class ReservaMapper {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public ReservaResponse aRespuesta(Reserva reserva) {
        return new ReservaResponse(
                reserva.getId(),
                reserva.getUsuarioId(),
                reserva.getCanchaId(),
                reserva.getFecha().format(FECHA),
                reserva.getHoraInicio().format(HORA),
                reserva.getHoraFin().format(HORA),
                estadoVisible(reserva).name());
    }

    /**
     * RN-08 y design D-02: FINALIZADA se calcula al leer y NO se persiste. En reservas_db
     * solo existen CONFIRMADA y CANCELADA.
     *
     * Una reserva CONFIRMADA cuya fecha y horaFin ya pasaron se ve como FINALIZADA. En el
     * instante exacto de horaFin el bloque ya termino, asi que tambien se considera
     * finalizada: mismo criterio de borde que D-20 aplica al inicio del bloque.
     *
     * El caso FINALIZADA persistido no lo produce este servicio, pero se respeta si existe
     * (design D-04).
     */
    public EstadoReserva estadoVisible(Reserva reserva) {
        if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            return reserva.getEstado();
        }
        LocalDateTime fin = LocalDateTime.of(reserva.getFecha(), reserva.getHoraFin());
        return fin.isAfter(LocalDateTime.now()) ? EstadoReserva.CONFIRMADA : EstadoReserva.FINALIZADA;
    }

    public LocalDate aFecha(String valor, String campo) {
        try {
            return LocalDate.parse(valor, FECHA);
        } catch (DateTimeParseException excepcion) {
            throw new FormatoInvalidoException(
                    "El campo " + campo + " debe ser una fecha valida en formato AAAA-MM-DD");
        }
    }

    public LocalTime aHora(String valor, String campo) {
        try {
            return LocalTime.parse(valor, HORA);
        } catch (DateTimeParseException excepcion) {
            throw new FormatoInvalidoException(
                    "El campo " + campo + " debe ser una hora valida en formato HH:mm");
        }
    }

    public String formatearFecha(LocalDate fecha) {
        return fecha.format(FECHA);
    }

    public String formatearHora(LocalTime hora) {
        return hora.format(HORA);
    }
}
