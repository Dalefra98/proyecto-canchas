package ec.ups.dae.canchas.controller;

import ec.ups.dae.canchas.dto.CambioEstadoCanchaRequest;
import ec.ups.dae.canchas.dto.CanchaRequest;
import ec.ups.dae.canchas.dto.CanchaResponse;
import ec.ups.dae.canchas.dto.ErrorResponse;
import ec.ups.dae.canchas.service.CanchaService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/canchas")
public class CanchaController {

    private final CanchaService canchaService;

    public CanchaController(CanchaService canchaService) {
        this.canchaService = canchaService;
    }

    /**
     * HU-01 — Listado del catalogo. Respuestas: 200, 401 NO_AUTENTICADO.
     */
    @Operation(summary = "Lista las canchas",
            description = "ADMIN y USUARIO. El ADMIN ve todas; el USUARIO solo las activas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de canchas"),
            @ApiResponse(responseCode = "401", description = "NO_AUTENTICADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<CanchaResponse>> listar() {
        return ResponseEntity.ok(canchaService.listar());
    }

    /**
     * HU-02 — Detalle de una cancha. Respuestas: 200, 401 NO_AUTENTICADO,
     * 404 NO_ENCONTRADO.
     */
    @Operation(summary = "Obtiene una cancha por su identificador",
            description = "ADMIN y USUARIO. Para un USUARIO, una cancha inactiva responde 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancha encontrada"),
            @ApiResponse(responseCode = "401", description = "NO_AUTENTICADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NO_ENCONTRADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{canchaId}")
    public ResponseEntity<CanchaResponse> obtener(@PathVariable Long canchaId) {
        return ResponseEntity.ok(canchaService.obtener(canchaId));
    }

    /**
     * HU-03 — Alta de cancha (RN-07). Respuestas: 201, 400 DATOS_INVALIDOS,
     * 401 NO_AUTENTICADO, 403 SIN_PERMISO, 409 NOMBRE_DUPLICADO.
     */
    @Operation(summary = "Crea una cancha", description = "Solo ADMIN. La cancha nace activa.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cancha creada"),
            @ApiResponse(responseCode = "400", description = "DATOS_INVALIDOS",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "NO_AUTENTICADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "SIN_PERMISO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "NOMBRE_DUPLICADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<CanchaResponse> crear(@Valid @RequestBody CanchaRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(canchaService.crear(peticion));
    }

    /**
     * HU-04 — Edicion de cancha (RN-07). Respuestas: 200, 400 DATOS_INVALIDOS,
     * 401 NO_AUTENTICADO, 403 SIN_PERMISO, 404 NO_ENCONTRADO, 409 NOMBRE_DUPLICADO.
     */
    @Operation(summary = "Edita una cancha",
            description = "Solo ADMIN. No cambia el estado: para eso esta PATCH /{canchaId}/estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancha actualizada"),
            @ApiResponse(responseCode = "400", description = "DATOS_INVALIDOS",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "NO_AUTENTICADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "SIN_PERMISO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NO_ENCONTRADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "NOMBRE_DUPLICADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{canchaId}")
    public ResponseEntity<CanchaResponse> editar(@PathVariable Long canchaId,
                                                 @Valid @RequestBody CanchaRequest peticion) {
        return ResponseEntity.ok(canchaService.editar(canchaId, peticion));
    }

    /**
     * HU-05 — Activar o inactivar una cancha (RN-07). Respuestas: 200, 400 DATOS_INVALIDOS,
     * 401 NO_AUTENTICADO, 403 SIN_PERMISO, 404 NO_ENCONTRADO.
     */
    @Operation(summary = "Activa o inactiva una cancha", description = "Solo ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancha actualizada"),
            @ApiResponse(responseCode = "400", description = "DATOS_INVALIDOS",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "NO_AUTENTICADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "SIN_PERMISO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NO_ENCONTRADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{canchaId}/estado")
    public ResponseEntity<CanchaResponse> cambiarEstado(
            @PathVariable Long canchaId,
            @Valid @RequestBody CambioEstadoCanchaRequest peticion) {
        return ResponseEntity.ok(canchaService.cambiarEstado(canchaId, peticion.activa()));
    }
}
