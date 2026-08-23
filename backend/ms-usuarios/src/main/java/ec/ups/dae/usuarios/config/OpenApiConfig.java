package ec.ups.dae.usuarios.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApiMsUsuarios() {
        return new OpenAPI().info(new Info()
                .title("ms-usuarios")
                .version("v1")
                .description("Registro, autenticacion y gestion de usuarios y roles. "
                        + "El token es un JWT HS256 que se envia en el encabezado "
                        + "Authorization: Bearer <token>."));
    }
}
