package ec.ups.dae.reportes.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Los dos clientes HTTP de ms-reportes. Es el RestClient que ya trae
 * spring-boot-starter-web: no se agrega ninguna libreria.
 *
 * Dos beans distintos, uno por servicio destino, cada uno con su baseUrl (design D-13): asi
 * la URL de cada dependencia se configura en un solo lugar y los timeouts quedan atados al
 * cliente en vez de repartidos por el codigo.
 *
 * Timeouts explicitos de 2 s de conexion y 5 s de lectura, y SIN reintentos, heredados de la
 * decision D-06 de la spec 04: si una dependencia no responde a tiempo, la peticion falla
 * rapido y sale como 500 ERROR_INTERNO. Reintentar multiplicaria la espera sin cambiar el
 * resultado en el caso frecuente, que es el servicio caido.
 */
@Configuration
public class ClienteHttpConfig {

    @Bean
    public RestClient clienteCanchas(@Value("${mscanchas.url}") String url,
                                     @Value("${mscanchas.timeout.conexion}") Duration conexion,
                                     @Value("${mscanchas.timeout.lectura}") Duration lectura) {
        return construir(url, conexion, lectura);
    }

    @Bean
    public RestClient clienteReservas(@Value("${msreservas.url}") String url,
                                      @Value("${msreservas.timeout.conexion}") Duration conexion,
                                      @Value("${msreservas.timeout.lectura}") Duration lectura) {
        return construir(url, conexion, lectura);
    }

    private RestClient construir(String url, Duration conexion, Duration lectura) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout((int) conexion.toMillis());
        fabrica.setReadTimeout((int) lectura.toMillis());
        return RestClient.builder()
                .baseUrl(url)
                .requestFactory(fabrica)
                .build();
    }
}
