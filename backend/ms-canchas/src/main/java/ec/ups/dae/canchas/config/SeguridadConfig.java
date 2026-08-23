package ec.ups.dae.canchas.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.ups.dae.canchas.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                        // Sin estas tres, springdoc responde 401 y E-06 no se puede demostrar.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Escritura del catalogo y de los bloqueos: solo ADMIN (RN-07).
                        .requestMatchers(HttpMethod.POST, "/api/canchas").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/canchas/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/canchas/*/estado").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/canchas/*/bloqueos").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/canchas/*/bloqueos/*").hasRole("ADMIN")
                        // Lectura del catalogo y de los bloqueos: ADMIN, USUARIO y el llamador
                        // interno con rol SERVICIO (spec 04, D-01). authenticated() ya los cubre a
                        // los tres; la escritura de arriba exige ADMIN, asi que un token SERVICIO
                        // recibe 403 SIN_PERMISO. El filtrado de canchas inactivas para el USUARIO
                        // lo hace el servicio (P-05).
                        .requestMatchers(HttpMethod.GET, "/api/canchas").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/canchas/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/canchas/*/bloqueos").authenticated()
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
