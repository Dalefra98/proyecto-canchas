package ec.ups.dae.reservas.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Valida el JWT HS256 del proyecto. Copiado de ms-canchas sin tocar la logica de
 * validacion (design D-17): el mismo JWT_SECRET lo comparten los cuatro microservicios y
 * cada uno valida el token localmente, sin endpoint de validacion ni llamada HTTP.
 *
 * ms-reservas no emite tokens de sesion, por eso esta copia no lleva el metodo emitir ni la
 * propiedad jwt.vigencia-horas; el emisor es ms-usuarios (design seccion 5.1). El unico token
 * que ms-reservas si emite es el de servicio hacia ms-canchas, y de eso se encarga
 * EmisorTokenServicio (design seccion 5.2).
 */
@Service
public class TokenService {

    private static final String CLAIM_ROL = "rol";

    private final SecretKey clave;

    public TokenService(@Value("${jwt.secret}") String secreto) {
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
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
