package ec.ups.dae.usuarios.service;

import ec.ups.dae.usuarios.dto.RegistroRequest;
import ec.ups.dae.usuarios.dto.UsuarioResponse;
import ec.ups.dae.usuarios.entity.Usuario;
import ec.ups.dae.usuarios.exception.EmailDuplicadoException;
import ec.ups.dae.usuarios.mapper.UsuarioMapper;
import ec.ups.dae.usuarios.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper usuarioMapper;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          UsuarioMapper usuarioMapper) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.usuarioMapper = usuarioMapper;
    }

    /**
     * HU-01: registro publico. S-03 fija rol USUARIO y activo true; la clave se guarda como
     * hash BCrypt. Si dos peticiones simultaneas pasan la verificacion previa, la restriccion
     * uq_usuario_email es el arbitro y el manejador traduce la violacion a 409
     * EMAIL_DUPLICADO (D-08).
     */
    @Transactional
    public UsuarioResponse registrar(RegistroRequest peticion) {
        if (usuarioRepository.existsByEmail(peticion.email())) {
            throw new EmailDuplicadoException("El correo ya esta registrado");
        }
        String passwordHash = passwordEncoder.encode(peticion.password());
        Usuario usuario = usuarioRepository.save(usuarioMapper.aEntidad(peticion, passwordHash));
        return usuarioMapper.aRespuesta(usuario);
    }
}
