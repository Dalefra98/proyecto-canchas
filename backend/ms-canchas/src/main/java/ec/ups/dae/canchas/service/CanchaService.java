package ec.ups.dae.canchas.service;

import ec.ups.dae.canchas.dto.CanchaRequest;
import ec.ups.dae.canchas.dto.CanchaResponse;
import ec.ups.dae.canchas.entity.Cancha;
import ec.ups.dae.canchas.exception.CanchaNoEncontradaException;
import ec.ups.dae.canchas.exception.HorarioInvalidoException;
import ec.ups.dae.canchas.exception.NombreDuplicadoException;
import ec.ups.dae.canchas.mapper.CanchaMapper;
import ec.ups.dae.canchas.repository.CanchaRepository;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CanchaService {

    private static final String ROL_ADMIN = "ROLE_ADMIN";

    private final CanchaRepository canchaRepository;
    private final CanchaMapper canchaMapper;

    public CanchaService(CanchaRepository canchaRepository, CanchaMapper canchaMapper) {
        this.canchaRepository = canchaRepository;
        this.canchaMapper = canchaMapper;
    }

    /**
     * HU-01: el ADMIN ve todas las canchas y el USUARIO solo las activas. El filtro sale del
     * rol del token, no de un parametro de consulta, para que el cliente no pueda cambiarlo
     * (decision P-05, design D-05).
     */
    @Transactional(readOnly = true)
    public List<CanchaResponse> listar() {
        List<Cancha> canchas = esAdmin() ? canchaRepository.findAll() : canchaRepository.findByActivaTrue();
        return canchas.stream().map(canchaMapper::aRespuesta).toList();
    }

    /**
     * HU-02: detalle de una cancha. Para un USUARIO, una cancha inactiva responde igual que
     * una inexistente: se lanza la misma excepcion a proposito, para que las dos respuestas
     * sean indistinguibles y el detalle no contradiga al listado (decision P-05, design D-06).
     */
    @Transactional(readOnly = true)
    public CanchaResponse obtener(Long canchaId) {
        return canchaMapper.aRespuesta(buscarVisible(canchaId));
    }

    /**
     * HU-03: alta de cancha. Toda cancha nace activa (S-02). Doble barrera contra el nombre
     * duplicado: si dos altas simultaneas pasan existsByNombre, la restriccion
     * uq_cancha_nombre es el arbitro y el manejador traduce la violacion al mismo
     * 409 NOMBRE_DUPLICADO (decision P-01, design D-08).
     */
    @Transactional
    public CanchaResponse crear(CanchaRequest peticion) {
        Cancha cancha = canchaMapper.aEntidad(peticion);
        validarHorario(cancha);
        if (canchaRepository.existsByNombre(cancha.getNombre())) {
            throw new NombreDuplicadoException("Ya existe una cancha con ese nombre");
        }
        return canchaMapper.aRespuesta(canchaRepository.save(cancha));
    }

    /**
     * HU-04: edicion de cancha. Reemplaza los cuatro campos editables y no toca activa, que
     * se maneja solo con PATCH .../estado (S-03, design D-11). Reenviar el nombre que ya
     * tenia esa misma cancha no es duplicado: por eso la consulta excluye su propio id.
     */
    @Transactional
    public CanchaResponse editar(Long canchaId, CanchaRequest peticion) {
        Cancha cancha = buscar(canchaId);
        canchaMapper.copiarSobre(cancha, peticion);
        validarHorario(cancha);
        if (canchaRepository.existsByNombreAndCanchaIdNot(cancha.getNombre(), canchaId)) {
            throw new NombreDuplicadoException("Ya existe una cancha con ese nombre");
        }
        return canchaMapper.aRespuesta(canchaRepository.save(cancha));
    }

    /**
     * HU-05: activa o inactiva una cancha. No borra ninguna fila, ni la cancha ni sus
     * bloqueos de mantenimiento.
     */
    @Transactional
    public CanchaResponse cambiarEstado(Long canchaId, boolean activa) {
        Cancha cancha = buscar(canchaId);
        cancha.setActiva(activa);
        return canchaMapper.aRespuesta(canchaRepository.save(cancha));
    }

    /**
     * RN-07: el horario de atencion debe ser un rango coherente. Se valida aqui, antes de
     * llegar a la base; ck_cancha_horario es la red de seguridad, no el mecanismo.
     */
    private void validarHorario(Cancha cancha) {
        if (!cancha.getHoraCierre().isAfter(cancha.getHoraApertura())) {
            throw new HorarioInvalidoException("horaCierre debe ser posterior a horaApertura");
        }
    }

    private Cancha buscar(Long canchaId) {
        return canchaRepository.findById(canchaId)
                .orElseThrow(() -> new CanchaNoEncontradaException("La cancha no existe"));
    }

    private Cancha buscarVisible(Long canchaId) {
        Cancha cancha = canchaRepository.findById(canchaId)
                .orElseThrow(() -> new CanchaNoEncontradaException("La cancha no existe"));
        if (!cancha.isActiva() && !esAdmin()) {
            throw new CanchaNoEncontradaException("La cancha no existe");
        }
        return cancha;
    }

    private boolean esAdmin() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null) {
            return false;
        }
        return autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROL_ADMIN::equals);
    }
}
