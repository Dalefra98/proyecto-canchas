package ec.ups.dae.usuarios.service;

import ec.ups.dae.usuarios.dto.RegistroRequest;
import ec.ups.dae.usuarios.dto.UsuarioResponse;
import ec.ups.dae.usuarios.entity.Usuario;
import ec.ups.dae.usuarios.exception.AutoInactivacionException;
import ec.ups.dae.usuarios.exception.EmailDuplicadoException;
import ec.ups.dae.usuarios.exception.UsuarioNoEncontradoException;
import ec.ups.dae.usuarios.mapper.UsuarioMapper;
import ec.ups.dae.usuarios.repository.UsuarioRepository;
import java.util.List;
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

    /**
     * HU-03: listado completo para el ADMIN, incluidos los inactivos. Sin paginacion ni
     * filtros, porque el contrato no congela ningun parametro de consulta (S-07).
     */
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAll().stream()
                .map(usuarioMapper::aRespuesta)
                .toList();
    }

    /**
     * HU-04: el ADMIN activa o inactiva a un usuario. S-05 prohibe que un ADMIN se inactive
     * a si mismo, para no dejar el sistema sin administrador.
     */
    @Transactional
    public UsuarioResponse cambiarEstado(Long usuarioId, boolean activo, Long solicitanteId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNoEncontradoException("El usuario no existe"));
        if (!activo && usuarioId.equals(solicitanteId)) {
            throw new AutoInactivacionException("Un administrador no puede inactivarse a si mismo");
        }
        usuario.setActivo(activo);
        return usuarioMapper.aRespuesta(usuarioRepository.save(usuario));
    }
}
