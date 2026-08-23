package ec.ups.dae.canchas.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApiMsCanchas() {
        return new OpenAPI().info(new Info()
                .title("ms-canchas")
                .version("v1")
                .description("Catalogo de canchas, horarios de atencion y bloqueos de "
                        + "mantenimiento. El token es un JWT HS256 emitido por ms-usuarios y "
                        + "se envia en el encabezado Authorization: Bearer <token>."));
    }
}
