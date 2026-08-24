package ec.ups.dae.reportes.service;

import ec.ups.dae.reportes.client.CanchasClient;
import ec.ups.dae.reportes.client.ReservasClient;
import ec.ups.dae.reportes.dto.CanchaExterna;
import ec.ups.dae.reportes.dto.ReservaExterna;
import ec.ups.dae.reportes.dto.ReservasItem;
import ec.ups.dae.reportes.mapper.ReporteMapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Arma los reportes cruzando el catalogo de ms-canchas con el listado global de
 * ms-reservas. No hay repositorio: este servicio no toca ninguna base de datos.
 *
 * Las dos llamadas salientes son secuenciales, no concurrentes (design D-09): son dos
 * peticiones dentro de la misma red de Docker y el paralelismo ahorraria milisegundos a
 * cambio de un modo de fallo mas dificil de explicar.
 */
@Service
public class ReporteService {

    private static final Logger LOG = LoggerFactory.getLogger(ReporteService.class);

    // RN-08. Una reserva que ya ocurrio ocupo la cancha igual, asi que FINALIZADA cuenta
    // junto a CONFIRMADA; contar solo CONFIRMADA daria cero en todo rango pasado, que es
    // justo lo que un reporte necesita mostrar (decision P-04). CANCELADA tiene su propio
    // reporte y no cuenta aqui, coherente con RN-05.
    private static final Set<String> ESTADOS_QUE_OCUPAN = Set.of("CONFIRMADA", "FINALIZADA");

    private static final Set<String> ESTADOS_CONOCIDOS = Set.of("CONFIRMADA", "FINALIZADA", "CANCELADA");

    private final CanchasClient canchasClient;
    private final ReservasClient reservasClient;
    private final ReporteMapper reporteMapper;

    public ReporteService(CanchasClient canchasClient, ReservasClient reservasClient,
                          ReporteMapper reporteMapper) {
        this.canchasClient = canchasClient;
        this.reservasClient = reservasClient;
        this.reporteMapper = reporteMapper;
    }

    /**
     * Numero de reservas por cancha en el rango (HU-02). Toda cancha del catalogo aparece,
     * con cero si no tuvo actividad (decision P-09).
     */
    public List<ReservasItem> reservasPorCancha(LocalDate desde, LocalDate hasta) {
        List<CanchaExterna> canchas = canchasClient.listarCanchas();
        List<ReservaExterna> reservas = reservasClient.listarTodas();
        Map<Long, Long> conteo = contarPorCancha(canchas, reservas, desde, hasta, ESTADOS_QUE_OCUPAN);
        return canchas.stream()
                .map(cancha -> reporteMapper.aReservasItem(cancha,
                        conteo.getOrDefault(cancha.canchaId(), 0L)))
                .toList();
    }

    /**
     * Cuenta reservas por canchaId, quedandose solo con las del rango y con los estados
     * pedidos. El rango es inclusivo en ambos extremos (decision P-07).
     *
     * El catalogo manda: una reserva cuyo canchaId no este en el (hoy imposible, porque las
     * canchas se inactivan pero nunca se borran) se descarta y se registra, en vez de
     * inventar una fila sin nombre ni deporte (design D-15).
     */
    private Map<Long, Long> contarPorCancha(List<CanchaExterna> canchas, List<ReservaExterna> reservas,
                                            LocalDate desde, LocalDate hasta, Set<String> estados) {
        Set<Long> idsDelCatalogo = new HashSet<>();
        for (CanchaExterna cancha : canchas) {
            idsDelCatalogo.add(cancha.canchaId());
        }

        Map<Long, Long> conteo = new HashMap<>();
        for (ReservaExterna reserva : reservas) {
            if (!ESTADOS_CONOCIDOS.contains(reserva.estado())) {
                LOG.warn("Reserva con estado desconocido, no cuenta en ningun reporte: {}", reserva.estado());
                continue;
            }
            if (!estados.contains(reserva.estado()) || !estaEnRango(reserva.fecha(), desde, hasta)) {
                continue;
            }
            if (!idsDelCatalogo.contains(reserva.canchaId())) {
                LOG.warn("Reserva de una cancha que no esta en el catalogo, se descarta: canchaId={}",
                        reserva.canchaId());
                continue;
            }
            conteo.merge(reserva.canchaId(), 1L, Long::sum);
        }
        return conteo;
    }

    private boolean estaEnRango(String fecha, LocalDate desde, LocalDate hasta) {
        try {
            LocalDate dia = LocalDate.parse(fecha);
            return !dia.isBefore(desde) && !dia.isAfter(hasta);
        } catch (DateTimeParseException excepcion) {
            LOG.warn("Reserva con fecha ilegible, no cuenta en ningun reporte: {}", fecha);
            return false;
        }
    }
}
