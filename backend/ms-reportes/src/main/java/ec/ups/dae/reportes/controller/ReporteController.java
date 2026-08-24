package ec.ups.dae.reportes.controller;

import ec.ups.dae.reportes.dto.ErrorResponse;
import ec.ups.dae.reportes.dto.ReporteOcupacionResponse;
import ec.ups.dae.reportes.dto.ReporteReservasResponse;
import ec.ups.dae.reportes.exception.RangoInvalidoException;
import ec.ups.dae.reportes.service.ReporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los tres reportes de solo lectura, todos de ADMIN. La cadena de seguridad ya filtro el rol
 * antes de llegar aqui (SeguridadConfig), asi que este controlador no vuelve a comprobarlo.
 */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    /**
     * Parseo estricto: con ResolverStyle.STRICT una fecha inexistente como 2026-02-30 falla
     * en vez de corregirse sola al 28. uuuu, no yyyy, porque el modo estricto exige el año
     * con era explicita.
     */
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final ReporteService reporteService;

    public ReporteController(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    @Operation(summary = "Porcentaje de ocupacion por cancha en un rango de fechas",
            description = "horasDisponibles es (horaCierre - horaApertura) por el numero de dias del "
                    + "rango, sin restar los bloqueos de mantenimiento. horasReservadas cuenta las "
                    + "reservas CONFIRMADA y FINALIZADA, una hora cada una. porcentajeOcupacion se "
                    + "devuelve con un decimal, redondeo HALF_UP.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte generado"),
            @ApiResponse(responseCode = "400", description = "DATOS_INVALIDOS: falta un parametro, "
                    + "el formato no es AAAA-MM-DD o desde es posterior a hasta",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "NO_AUTENTICADO: falta el token o no es valido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "SIN_PERMISO: el rol no es ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "ERROR_INTERNO: ms-canchas o ms-reservas "
                    + "no respondieron",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping("/ocupacion")
    public ReporteOcupacionResponse ocupacion(@RequestParam String desde, @RequestParam String hasta) {
        LocalDate inicio = parsear(desde, "desde");
        LocalDate fin = parsear(hasta, "hasta");
        validarOrden(inicio, fin);
        return new ReporteOcupacionResponse(desde, hasta, reporteService.ocupacionPorCancha(inicio, fin));
    }

    @Operation(summary = "Numero de reservas por cancha y deporte en un rango de fechas",
            description = "Cuenta las reservas CONFIRMADA y FINALIZADA cuya fecha cae dentro del rango, "
                    + "ambos extremos inclusive. Las canceladas tienen su propio reporte. Toda cancha "
                    + "aparece en items, con cero si no tuvo actividad.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte generado"),
            @ApiResponse(responseCode = "400", description = "DATOS_INVALIDOS: falta un parametro, "
                    + "el formato no es AAAA-MM-DD o desde es posterior a hasta",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "NO_AUTENTICADO: falta el token o no es valido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "SIN_PERMISO: el rol no es ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "ERROR_INTERNO: ms-canchas o ms-reservas "
                    + "no respondieron",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping("/reservas")
    public ReporteReservasResponse reservas(@RequestParam String desde, @RequestParam String hasta) {
        LocalDate inicio = parsear(desde, "desde");
        LocalDate fin = parsear(hasta, "hasta");
        validarOrden(inicio, fin);
        // desde y hasta se devuelven tal como llegaron, no reformateados (design seccion 4.1).
        return new ReporteReservasResponse(desde, hasta, reporteService.reservasPorCancha(inicio, fin));
    }

    /**
     * Se parsea a mano en vez de dejar que Spring convierta a LocalDate (design D-04): asi el
     * mensaje del 400 dice que parametro fallo y por que, sin depender de como Spring formule
     * el error de conversion.
     */
    private LocalDate parsear(String valor, String nombre) {
        try {
            return LocalDate.parse(valor, FORMATO_FECHA);
        } catch (DateTimeParseException excepcion) {
            throw new RangoInvalidoException(
                    "El parametro " + nombre + " debe tener el formato AAAA-MM-DD y ser una fecha real");
        }
    }

    private void validarOrden(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new RangoInvalidoException("El parametro desde no puede ser posterior a hasta");
        }
    }
}
