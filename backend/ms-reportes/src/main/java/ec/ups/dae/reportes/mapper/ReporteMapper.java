package ec.ups.dae.reportes.mapper;

import ec.ups.dae.reportes.dto.CanchaExterna;
import ec.ups.dae.reportes.dto.ReservasItem;
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
}
