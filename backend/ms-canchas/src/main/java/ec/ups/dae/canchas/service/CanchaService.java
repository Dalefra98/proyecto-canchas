package ec.ups.dae.canchas.service;

import ec.ups.dae.canchas.dto.CanchaResponse;
import ec.ups.dae.canchas.entity.Cancha;
import ec.ups.dae.canchas.exception.CanchaNoEncontradaException;
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
