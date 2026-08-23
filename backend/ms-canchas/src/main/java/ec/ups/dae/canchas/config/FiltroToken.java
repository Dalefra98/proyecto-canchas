package ec.ups.dae.canchas.config;

import ec.ups.dae.canchas.service.TokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lee "Authorization: Bearer <token>", lo valida localmente y deja en el contexto el
 * usuarioId como principal y el rol como authority. Si el token falta o no es valido no
 * autentica a nadie: la cadena responde 401 NO_AUTENTICADO por su punto de entrada.
 *
 * Reconoce tres roles: ADMIN, USUARIO y SERVICIO (spec 04, decision D-01, que resuelve el
 * asunto A-01 de esta spec). El token de servicio lo emite otro microservicio, no una
 * persona: no trae claim sub, asi que su principal es el propio nombre del rol. Solo habilita
 * lectura; en las rutas de escritura la cadena responde 403 SIN_PERMISO, porque exigen
 * hasRole("ADMIN") (RN-07).
 */
@Component
public class FiltroToken extends OncePerRequestFilter {

    private static final String ENCABEZADO = "Authorization";
    private static final String PREFIJO = "Bearer ";
    private static final String ROL_SERVICIO = "SERVICIO";

    private final TokenService tokenService;

    public FiltroToken(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {
        String encabezado = peticion.getHeader(ENCABEZADO);
        if (encabezado != null && encabezado.startsWith(PREFIJO)) {
            Claims claims = tokenService.validar(encabezado.substring(PREFIJO.length()));
            if (claims != null) {
                String rol = tokenService.rolDe(claims);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + rol));
                // El token de servicio no trae sub: pedirle un usuarioId reventaria.
                Object principal = ROL_SERVICIO.equals(rol) ? ROL_SERVICIO : tokenService.usuarioIdDe(claims);
                var autenticacion = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
            }
        }
        cadena.doFilter(peticion, respuesta);
    }
}
