package ec.ups.dae.usuarios.service;

import ec.ups.dae.usuarios.dto.LoginRequest;
import ec.ups.dae.usuarios.dto.LoginResponse;
import ec.ups.dae.usuarios.entity.Usuario;
import ec.ups.dae.usuarios.exception.CredencialesInvalidasException;
import ec.ups.dae.usuarios.mapper.UsuarioMapper;
import ec.ups.dae.usuarios.repository.UsuarioRepository;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutenticacionService {

    private static final String MENSAJE_RECHAZO = "Correo o contrasena incorrectos";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UsuarioMapper usuarioMapper;

    public AutenticacionService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                                TokenService tokenService, UsuarioMapper usuarioMapper) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.usuarioMapper = usuarioMapper;
    }

    /**
     * HU-02: inicio de sesion. Correo inexistente, contrasena incorrecta y usuario con
     * activo = false se rechazan con el mismo 401 y el mismo mensaje, para no permitir
     * enumerar cuentas registradas (D-04, S-04).
     */
    @Transactional(readOnly = true)
    public LoginResponse iniciarSesion(LoginRequest peticion) {
        Optional<Usuario> encontrado = usuarioRepository.findByEmail(peticion.email());
        if (encontrado.isEmpty()) {
            throw new CredencialesInvalidasException(MENSAJE_RECHAZO);
        }
        Usuario usuario = encontrado.get();
        if (!passwordEncoder.matches(peticion.password(), usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException(MENSAJE_RECHAZO);
        }
        if (!usuario.isActivo()) {
            throw new CredencialesInvalidasException(MENSAJE_RECHAZO);
        }
        return new LoginResponse(tokenService.emitir(usuario), usuarioMapper.aRespuesta(usuario));
    }
}
