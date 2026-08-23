package ec.ups.dae.usuarios.config;

import ec.ups.dae.usuarios.service.TokenService;
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
 */
@Component
public class FiltroToken extends OncePerRequestFilter {

    private static final String ENCABEZADO = "Authorization";
    private static final String PREFIJO = "Bearer ";

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
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + tokenService.rolDe(claims)));
                var autenticacion = new UsernamePasswordAuthenticationToken(
                        tokenService.usuarioIdDe(claims), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
            }
        }
        cadena.doFilter(peticion, respuesta);
    }
}
