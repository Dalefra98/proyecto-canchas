package ec.ups.dae.reportes.service;

import ec.ups.dae.reportes.dto.CanchaExterna;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Aritmetica del reporte de ocupacion (design seccion 3). Se separa del ReporteService
 * porque es el unico reporte con logica propia: horario por cancha, division y redondeo.
 */
@Component
public class CalculadoraOcupacion {

    private static final Logger LOG = LoggerFactory.getLogger(CalculadoraOcupacion.class);

    private static final int ESCALA_PORCENTAJE = 1;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    /** Cero con un decimal, para que el JSON diga 0.0 y no 0, igual que el resto de filas. */
    private static final BigDecimal CERO_PORCENTAJE = BigDecimal.ZERO.setScale(ESCALA_PORCENTAJE);

    /**
     * Dias que cubre el rango, con ambos extremos inclusive (decision P-07): desde igual a
     * hasta da 1. Nunca es cero ni negativo, porque el controlador ya rechazo con 400 el caso
     * desde posterior a hasta.
     */
    public long diasDelRango(LocalDate desde, LocalDate hasta) {
        return ChronoUnit.DAYS.between(desde, hasta) + 1;
    }

    /**
     * (horaCierre - horaApertura) x dias del rango (decision P-03). NO se restan los bloqueos
     * de mantenimiento: hacerlo obligaria a una llamada HTTP por dia y por cancha.
     *
     * Se calcula con el horario propio de cada cancha, no con un valor fijo: Padel 1 abre 15
     * horas (07:00-22:00) y Padel 2 abre 13 (08:00-21:00).
     */
    public long horasDisponibles(CanchaExterna cancha, long diasDelRango) {
        return horasPorDia(cancha) * diasDelRango;
    }

    /**
     * horasReservadas / horasDisponibles x 100, con un decimal y redondeo HALF_UP
     * (decision P-06). BigDecimal y no double: un double arrastraria error binario y Jackson
     * podria serializar 26.699999999999999 en vez de 26.7 (design D-05).
     */
    public BigDecimal porcentajeOcupacion(long horasReservadas, long horasDisponibles) {
        if (horasDisponibles <= 0) {
            return CERO_PORCENTAJE;
        }
        return BigDecimal.valueOf(horasReservadas)
                .multiply(CIEN)
                .divide(BigDecimal.valueOf(horasDisponibles), ESCALA_PORCENTAJE, RoundingMode.HALF_UP);
    }

    /**
     * Un horario invertido o vacio da cero horas, nunca un negativo que ensuciaria el
     * porcentaje. ms-canchas ya impide crear una cancha asi, pero el calculo no puede
     * depender de eso (design seccion 3.2).
     */
    private long horasPorDia(CanchaExterna cancha) {
        try {
            LocalTime apertura = LocalTime.parse(cancha.horaApertura());
            LocalTime cierre = LocalTime.parse(cancha.horaCierre());
            long horas = Duration.between(apertura, cierre).toHours();
            return Math.max(horas, 0);
        } catch (DateTimeParseException | NullPointerException excepcion) {
            LOG.warn("Cancha con horario ilegible, se cuenta con cero horas disponibles: canchaId={}",
                    cancha.canchaId());
            return 0;
        }
    }
}
