package ec.ups.dae.usuarios.controller;

import ec.ups.dae.usuarios.dto.RegistroRequest;
import ec.ups.dae.usuarios.dto.UsuarioResponse;
import ec.ups.dae.usuarios.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /**
     * HU-01 — Registro publico. Respuestas: 201, 400 DATOS_INVALIDOS, 409 EMAIL_DUPLICADO.
     */
    @PostMapping
    public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody RegistroRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.registrar(peticion));
    }
}
