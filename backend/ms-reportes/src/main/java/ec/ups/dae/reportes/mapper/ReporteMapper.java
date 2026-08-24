package ec.ups.dae.reportes.mapper;

import ec.ups.dae.reportes.dto.CancelacionesItem;
import ec.ups.dae.reportes.dto.CanchaExterna;
import ec.ups.dae.reportes.dto.OcupacionItem;
import ec.ups.dae.reportes.dto.ReservasItem;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Mapeo manual de cancha del catalogo mas conteo a fila de reporte. Sin MapStruct ni Lombok
 * (CLAUDE.md seccion 3).
 *
 * Los nombres de los campos son los congelados en docs/contratos/README.md y no se
 * renombran. En particular, la fila de cancelaciones NO llevara deporte.
 */
@Component
public class ReporteMapper {

    public ReservasItem aReservasItem(CanchaExterna cancha, long totalReservas) {
        return new ReservasItem(cancha.canchaId(), cancha.nombre(), cancha.deporte(), totalReservas);
    }

    /**
     * Fila del reporte de cancelaciones. NO recibe ni copia el deporte: el payload congelado
     * declara solo canchaId, nombre y totalCancelaciones.
     */
    public CancelacionesItem aCancelacionesItem(CanchaExterna cancha, long totalCancelaciones) {
        return new CancelacionesItem(cancha.canchaId(), cancha.nombre(), totalCancelaciones);
    }

    public OcupacionItem aOcupacionItem(CanchaExterna cancha, long horasReservadas,
                                        long horasDisponibles, BigDecimal porcentajeOcupacion) {
        return new OcupacionItem(cancha.canchaId(), cancha.nombre(), cancha.deporte(),
                horasReservadas, horasDisponibles, porcentajeOcupacion);
    }
}
