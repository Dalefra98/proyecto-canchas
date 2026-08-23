package ec.ups.dae.canchas.controller;

import ec.ups.dae.canchas.dto.CanchaResponse;
import ec.ups.dae.canchas.dto.ErrorResponse;
import ec.ups.dae.canchas.service.CanchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
