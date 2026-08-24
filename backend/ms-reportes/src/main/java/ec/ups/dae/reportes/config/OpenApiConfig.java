package ec.ups.dae.reportes.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApiMsReportes() {
        return new OpenAPI().info(new Info()
                .title("ms-reportes")
                .version("v1")
                .description("Reportes de ocupacion, reservas y cancelaciones por rango de fechas. "
                        + "Solo ADMIN. El token es un JWT HS256 emitido por ms-usuarios y se envia "
                        + "en el encabezado Authorization: Bearer <token>."));
    }
}
