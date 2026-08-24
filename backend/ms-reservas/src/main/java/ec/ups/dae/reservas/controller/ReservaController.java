package ec.ups.dae.reservas.controller;

import ec.ups.dae.reservas.dto.DisponibilidadResponse;
import ec.ups.dae.reservas.dto.ErrorResponse;
import ec.ups.dae.reservas.dto.ReservaRequest;
import ec.ups.dae.reservas.dto.ReservaResponse;
import ec.ups.dae.reservas.service.DisponibilidadService;
import ec.ups.dae.reservas.service.ReservaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las cinco rutas congeladas de /api/reservas. Los codigos de error de cada endpoint son los
 * de la tabla "Formato de error" del contrato.
 *
 * Nota sobre el campo estado de toda ReservaResponse: FINALIZADA es un estado DERIVADO, no
 * persistido. Se calcula al leer sobre una reserva CONFIRMADA cuya fecha y horaFin ya
 * pasaron; en la base solo existen CONFIRMADA y CANCELADA (decision D-02, design D-15).
 */
@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final DisponibilidadService disponibilidadService;
    private final ReservaService reservaService;

    public ReservaController(DisponibilidadService disponibilidadService,
                             ReservaService reservaService) {
        this.disponibilidadService = disponibilidadService;
        this.reservaService = reservaService;
    }

    /**
     * HU-01 — Disponibilidad de una cancha en una fecha. Respuestas: 200,
     * 400 DATOS_INVALIDOS, 401 NO_AUTENTICADO, 404 NO_ENCONTRADO, 500 ERROR_INTERNO.
     *
     * canchaId y fecha son obligatorios: si falta alguno, el manejador responde
     * 400 DATOS_INVALIDOS. fecha se recibe como String para que el parseo estricto del mapper
     * sea quien decida, y no el convertidor de Spring (design D-11).
     */
    @Operation(summary = "Consulta la disponibilidad de una cancha en una fecha",
            description = "ADMIN y USUARIO. Devuelve un bloque de una hora por cada franja del "
                    + "horario de atencion. Un bloque esta ocupado si tiene una reserva CONFIRMADA "
                    + "o si cae dentro de un bloqueo de mantenimiento. Una cancha inactiva "
                    + "responde 200 con todos los bloques ocupados, y una fecha pasada se admite: "
                    + "la consulta es informativa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Disponibilidad de la cancha"),
            @ApiResponse(responseCode = "400", description = "DATOS_INVALIDOS",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "NO_AUTENTICADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NO_ENCONTRADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "ERROR_INTERNO: ms-canchas no responde",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/disponibilidad")
    public ResponseEntity<DisponibilidadResponse> disponibilidad(@RequestParam Long canchaId,
                                                                 @RequestParam String fecha) {
        return ResponseEntity.ok(disponibilidadService.consultar(canchaId, fecha));
    }

    /**
     * HU-02 — Alta de reserva (RN-01, RN-02, RN-06). Respuestas: 201,
     * 400 DATOS_INVALIDOS, 401 NO_AUTENTICADO, 404 NO_ENCONTRADO,
     * 409 BLOQUE_OCUPADO, 409 LIMITE_RESERVAS, 500 ERROR_INTERNO.
     */
    @Operation(summary = "Crea una reserva",
            description = "ADMIN y USUARIO: un ADMIN tambien es una persona que puede reservar. "
                    + "El cuerpo es { canchaId, fecha, horaInicio }; horaFin lo calcula el "
                    + "servicio como horaInicio + 1 hora. El usuarioId sale del token, nunca del "
                    + "cuerpo. No se reserva el pasado ni una cancha inactiva.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reserva creada, en estado CONFIRMADA"),
            @ApiResponse(responseCode = "400", description = "DATOS_INVALIDOS: formato, hora fuera "
                    + "de punto, bloque fuera del horario de atencion o bloque ya ocurrido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "NO_AUTENTICADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NO_ENCONTRADO: la cancha no existe o "
                    + "esta inactiva",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "BLOQUE_OCUPADO (bloque reservado o en "
                    + "mantenimiento) o LIMITE_RESERVAS",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "ERROR_INTERNO: ms-canchas no responde",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ReservaResponse> crear(@Valid @RequestBody ReservaRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservaService.crear(peticion));
    }

    /**
     * HU-04 — Listado global. Respuestas: 200, 401 NO_AUTENTICADO, 403 SIN_PERMISO.
     */
    @Operation(summary = "Lista todas las reservas del sistema",
            description = "Solo ADMIN. Todas las reservas, de todos los usuarios y en todos los "
                    + "estados, ordenadas por fecha y horaInicio descendente. Sin filtros ni "
                    + "paginacion.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado global de reservas"),
            @ApiResponse(responseCode = "401", description = "NO_AUTENTICADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "SIN_PERMISO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<ReservaResponse>> listar() {
        return ResponseEntity.ok(reservaService.listarTodas());
    }

    /**
     * HU-03 — Historial propio (RN-03). Respuestas: 200, 401 NO_AUTENTICADO.
     */
    @Operation(summary = "Lista las reservas propias",
            description = "ADMIN y USUARIO. Solo las reservas del usuarioId del token, en todos "
                    + "los estados, ordenadas por fecha y horaInicio descendente. Es un historial, "
                    + "no una lista de reservas vigentes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial del usuario del token"),
            @ApiResponse(responseCode = "401", description = "NO_AUTENTICADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/mias")
    public ResponseEntity<List<ReservaResponse>> listarMias() {
        return ResponseEntity.ok(reservaService.listarMias());
    }

    /**
     * HU-05 — Cancelacion (RN-03, RN-04, RN-05). Respuestas: 200, 401 NO_AUTENTICADO,
     * 403 SIN_PERMISO, 404 NO_ENCONTRADO, 409 RESERVA_PASADA, 409 RESERVA_NO_CANCELABLE.
     *
     * La ruta congelada usa {id}, no {reservaId}: se implementa literal. No lee cuerpo.
     */
    @Operation(summary = "Cancela una reserva",
            description = "El USUARIO solo cancela las suyas; el ADMIN cancela cualquiera. Solo se "
                    + "cancela una reserva CONFIRMADA cuya fecha y hora de inicio aun no han "
                    + "ocurrido. La fila no se borra: cambia de estado y el bloque queda libre.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva cancelada, en estado CANCELADA"),
            @ApiResponse(responseCode = "401", description = "NO_AUTENTICADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "SIN_PERMISO: la reserva es de otro usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NO_ENCONTRADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "RESERVA_PASADA (ya ocurrio) o "
                    + "RESERVA_NO_CANCELABLE (ya estaba cancelada)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/cancelacion")
    public ResponseEntity<ReservaResponse> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.cancelar(id));
    }
}
