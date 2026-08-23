package ec.ups.dae.reservas.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia ms-canchas. Es el RestClient que ya trae spring-boot-starter-web: no se
 * agrega ninguna libreria (design D-12).
 *
 * Timeouts explicitos de 2 s de conexion y 5 s de lectura, y SIN reintentos: si ms-canchas no
 * responde a tiempo, la peticion falla rapido y sale como 500 ERROR_INTERNO con el mensaje
 * fijo del design D-06. Reintentar multiplicaria la espera del usuario sin cambiar el
 * resultado en el caso frecuente, que es el servicio caido.
 */
@Configuration
public class ClienteHttpConfig {

    @Bean
    public RestClient clienteCanchas(@Value("${mscanchas.url}") String url,
                                     @Value("${mscanchas.timeout.conexion}") Duration conexion,
                                     @Value("${mscanchas.timeout.lectura}") Duration lectura) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout((int) conexion.toMillis());
        fabrica.setReadTimeout((int) lectura.toMillis());
        return RestClient.builder()
                .baseUrl(url)
                .requestFactory(fabrica)
                .build();
    }
}
