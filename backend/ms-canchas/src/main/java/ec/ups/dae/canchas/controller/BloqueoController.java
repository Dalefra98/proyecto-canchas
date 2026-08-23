package ec.ups.dae.canchas.controller;

import ec.ups.dae.canchas.dto.BloqueoRequest;
import ec.ups.dae.canchas.dto.BloqueoResponse;
import ec.ups.dae.canchas.dto.ErrorResponse;
import ec.ups.dae.canchas.service.BloqueoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/canchas/{canchaId}/bloqueos")
public class BloqueoController {

    private final BloqueoService bloqueoService;

    public BloqueoController(BloqueoService bloqueoService) {
        this.bloqueoService = bloqueoService;
    }

    /**
     * HU-06 — Listado de bloqueos. Respuestas: 200, 400 DATOS_INVALIDOS,
     * 401 NO_AUTENTICADO, 404 NO_ENCONTRADO.
     */
    @Operation(summary = "Lista los bloqueos de una cancha",
            description = "ADMIN y USUARIO. El parametro fecha es opcional; sin el devuelve todos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de bloqueos"),
            @ApiResponse(responseCode = "400", description = "DATOS_INVALIDOS",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "NO_AUTENTICADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NO_ENCONTRADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<BloqueoResponse>> listar(
            @PathVariable Long canchaId,
            @RequestParam(required = false) String fecha) {
        return ResponseEntity.ok(bloqueoService.listar(canchaId, fecha));
    }

    /**
     * HU-07 — Alta de un bloqueo. Respuestas: 201, 400 DATOS_INVALIDOS,
     * 401 NO_AUTENTICADO, 403 SIN_PERMISO, 404 NO_ENCONTRADO, 409 BLOQUEO_DUPLICADO.
     */
    @Operation(summary = "Registra un bloqueo de mantenimiento",
            description = "Solo ADMIN. Se admite sobre una cancha inactiva.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Bloqueo creado"),
            @ApiResponse(responseCode = "400", description = "DATOS_INVALIDOS",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "NO_AUTENTICADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "SIN_PERMISO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NO_ENCONTRADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "BLOQUEO_DUPLICADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<BloqueoResponse> crear(@PathVariable Long canchaId,
                                                 @Valid @RequestBody BloqueoRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bloqueoService.crear(canchaId, peticion));
    }

    /**
     * HU-08 — Baja de un bloqueo. Respuestas: 204, 401 NO_AUTENTICADO, 403 SIN_PERMISO,
     * 404 NO_ENCONTRADO.
     */
    @Operation(summary = "Elimina un bloqueo de mantenimiento",
            description = "Solo ADMIN. El bloqueo debe pertenecer a esa cancha.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Bloqueo eliminado"),
            @ApiResponse(responseCode = "401", description = "NO_AUTENTICADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "SIN_PERMISO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NO_ENCONTRADO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long canchaId, @PathVariable Long id) {
        bloqueoService.eliminar(canchaId, id);
        return ResponseEntity.noContent().build();
    }
}
