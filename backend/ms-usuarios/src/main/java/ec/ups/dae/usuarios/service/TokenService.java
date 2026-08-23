package ec.ups.dae.usuarios.service;

import ec.ups.dae.usuarios.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Emite y valida el JWT HS256 del proyecto. El mismo JWT_SECRET lo comparten los cuatro
 * microservicios, que validan el token localmente: no hay endpoint de validacion ni llamada
 * HTTP a ms-usuarios.
 */
@Service
public class TokenService {

    private static final String CLAIM_ROL = "rol";

    private final SecretKey clave;
    private final Duration vigencia;

    public TokenService(@Value("${jwt.secret}") String secreto,
                        @Value("${jwt.vigencia-horas}") long vigenciaHoras) {
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.vigencia = Duration.ofHours(vigenciaHoras);
    }

    public String emitir(Usuario usuario) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(usuario.getUsuarioId()))
                .claim(CLAIM_ROL, usuario.getRol().name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(vigencia)))
                .signWith(clave)
                .compact();
    }

    /**
     * Devuelve los claims del token o null si esta vencido, alterado o firmado con otro
     * secreto. Quien llama traduce el null en 401 NO_AUTENTICADO.
     */
    public Claims validar(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException excepcion) {
            return null;
        }
    }

    public Long usuarioIdDe(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String rolDe(Claims claims) {
        return claims.get(CLAIM_ROL, String.class);
    }
}
