package ec.ups.dae.usuarios.controller;

import ec.ups.dae.usuarios.dto.CambioEstadoRequest;
import ec.ups.dae.usuarios.dto.LoginRequest;
import ec.ups.dae.usuarios.dto.LoginResponse;
import ec.ups.dae.usuarios.dto.RegistroRequest;
import ec.ups.dae.usuarios.dto.UsuarioResponse;
import ec.ups.dae.usuarios.service.AutenticacionService;
import ec.ups.dae.usuarios.service.UsuarioService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final AutenticacionService autenticacionService;

    public UsuarioController(UsuarioService usuarioService, AutenticacionService autenticacionService) {
        this.usuarioService = usuarioService;
        this.autenticacionService = autenticacionService;
    }

    /**
     * HU-02 — Inicio de sesion. Respuestas: 200, 400 DATOS_INVALIDOS, 401 NO_AUTENTICADO.
     */
    @PostMapping("/sesiones")
    public ResponseEntity<LoginResponse> iniciarSesion(@Valid @RequestBody LoginRequest peticion) {
        return ResponseEntity.ok(autenticacionService.iniciarSesion(peticion));
    }

    /**
     * HU-01 — Registro publico. Respuestas: 201, 400 DATOS_INVALIDOS, 409 EMAIL_DUPLICADO.
     */
    @PostMapping
    public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody RegistroRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.registrar(peticion));
    }

    /**
     * HU-03 — Listado para el ADMIN. Respuestas: 200, 401 NO_AUTENTICADO, 403 SIN_PERMISO.
     */
    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> listar() {
        return ResponseEntity.ok(usuarioService.listar());
    }

    /**
     * HU-04 — Activar o inactivar un usuario. Respuestas: 200, 400 DATOS_INVALIDOS,
     * 401 NO_AUTENTICADO, 403 SIN_PERMISO, 404 NO_ENCONTRADO.
     */
    @PatchMapping("/{usuarioId}/estado")
    public ResponseEntity<UsuarioResponse> cambiarEstado(@PathVariable Long usuarioId,
                                                         @Valid @RequestBody CambioEstadoRequest peticion,
                                                         @AuthenticationPrincipal Long solicitanteId) {
        return ResponseEntity.ok(usuarioService.cambiarEstado(usuarioId, peticion.activo(), solicitanteId));
    }
}
