package ec.ups.dae.reservas.config;

import ec.ups.dae.reservas.service.TokenService;
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
 * Un token con rol SERVICIO SI se autentica, pero con principal nulo: no trae claim sub y
 * por tanto no identifica a ninguna persona. Solo le sirve para GET /api/reservas, la unica
 * ruta que SeguridadConfig le abre; en las otras cuatro recibe 403 SIN_PERMISO.
 *
 * Cambio de la spec 05 (decision P-01), que revisa el supuesto S-12 de esta spec: hasta el
 * 23/08/2026 este filtro descartaba el token SERVICIO, y con eso ms-reportes se quedaba sin
 * ninguna credencial para leer el listado global, que es su unica fuente de datos. Se
 * descarto que ms-reportes reenviara el token del ADMIN porque contradice C-01 de la spec 03:
 * las llamadas internas no propagan el token del usuario final.
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
                // El token de servicio no trae claim sub: pedirle el usuarioId reventaria.
                // Su principal es nulo a proposito, y ninguna ruta que necesite dueno se lo
                // abre (design seccion 8 de la spec 05).
                Object principal = ROL_SERVICIO.equals(rol) ? null : tokenService.usuarioIdDe(claims);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + rol));
                var autenticacion = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
            }
        }
        cadena.doFilter(peticion, respuesta);
    }
}
