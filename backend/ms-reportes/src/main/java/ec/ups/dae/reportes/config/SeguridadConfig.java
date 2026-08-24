package ec.ups.dae.reportes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.ups.dae.reportes.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    private final FiltroToken filtroToken;
    private final ObjectMapper objectMapper;

    public SeguridadConfig(FiltroToken filtroToken, ObjectMapper objectMapper) {
        this.filtroToken = filtroToken;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        // Sin estas tres, springdoc responde 401 y E-10 no se puede demostrar.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Unica regla de negocio de este servicio: los tres reportes son solo
                        // de ADMIN (PDF seccion 3.1, contrato y design seccion 5.2). No hace
                        // falta distinguir por verbo: solo se publican GET.
                        .requestMatchers("/api/reportes/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(manejo -> manejo
                        .authenticationEntryPoint(puntoDeEntrada())
                        .accessDeniedHandler(accesoDenegado()))
                .addFilterBefore(filtroToken, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private AuthenticationEntryPoint puntoDeEntrada() {
        return (peticion, respuesta, excepcion) -> escribir(respuesta, HttpServletResponse.SC_UNAUTHORIZED,
                new ErrorResponse("NO_AUTENTICADO", "Se requiere un token valido"));
    }

    private AccessDeniedHandler accesoDenegado() {
        return (peticion, respuesta, excepcion) -> escribir(respuesta, HttpServletResponse.SC_FORBIDDEN,
                new ErrorResponse("SIN_PERMISO", "No tiene permiso para esta operacion"));
    }

    private void escribir(HttpServletResponse respuesta, int estado, ErrorResponse cuerpo) throws java.io.IOException {
        respuesta.setStatus(estado);
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(respuesta.getWriter(), cuerpo);
    }
}
