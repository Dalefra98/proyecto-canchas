package ec.ups.dae.reportes.service;

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
 * Emite el token de servicio con el que ms-reportes llama a ms-canchas y a ms-reservas.
 * Copia del de ms-reservas: el mecanismo esta congelado en docs/contratos/README.md y no se
 * vuelve a decidir.
 *
 * Es un JWT HS256 firmado con el mismo JWT_SECRET del proyecto, con claim rol = SERVICIO,
 * SIN claim sub y con exp corto. No identifica a ninguna persona: solo dice "soy otro
 * microservicio". ms-canchas lo acepta en sus rutas de lectura y le devuelve la vista
 * completa del catalogo, inactivas incluidas, que es justo lo que los reportes necesitan
 * (decision P-09). ms-reservas lo acepta unicamente en GET /api/reservas (decision P-01).
 *
 * Se emite uno nuevo en cada llamada y no se cachea: firmar un JWT corto es despreciable
 * frente a la llamada HTTP que lo acompaña, y cachearlo obligaria a manejar expiracion y
 * concurrencia sin ahorro medible (design D-14).
 *
 * El token del usuario final NUNCA se reenvia (decision C-01 de la spec 03).
 */
@Service
public class EmisorTokenServicio {

    private static final String CLAIM_ROL = "rol";
    private static final String ROL_SERVICIO = "SERVICIO";

    private final SecretKey clave;
    private final Duration duracion;

    public EmisorTokenServicio(@Value("${jwt.secret}") String secreto,
                               @Value("${reportes.token-servicio.duracion}") Duration duracion) {
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.duracion = duracion;
    }

    public String emitir() {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .claim(CLAIM_ROL, ROL_SERVICIO)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(duracion)))
                .signWith(clave)
                .compact();
    }
}
