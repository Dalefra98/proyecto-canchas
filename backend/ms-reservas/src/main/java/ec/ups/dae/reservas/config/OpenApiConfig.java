package ec.ups.dae.reservas.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApiMsReservas() {
        return new OpenAPI().info(new Info()
                .title("ms-reservas")
                .version("v1")
                .description("Disponibilidad, creacion, consulta y cancelacion de reservas. "
                        + "El token es un JWT HS256 emitido por ms-usuarios y se envia en el "
                        + "encabezado Authorization: Bearer <token>."));
    }
}
