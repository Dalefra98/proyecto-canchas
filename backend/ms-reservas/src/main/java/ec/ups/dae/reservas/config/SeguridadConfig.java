package ec.ups.dae.reservas.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.ups.dae.reservas.dto.ErrorResponse;
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
                        // Sin estas tres, springdoc responde 401 y E-08 no se puede demostrar.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Listado global del sistema: solo ADMIN. Es la unica restriccion por
                        // rol de este servicio (design seccion 5.3).
                        .requestMatchers(HttpMethod.GET, "/api/reservas").hasRole("ADMIN")
                        // Disponibilidad, historial propio y alta: ADMIN y USUARIO. El ADMIN
                        // tambien reserva y tiene historial propio (decision D-08).
                        .requestMatchers(HttpMethod.GET, "/api/reservas/disponibilidad").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/reservas/mias").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/reservas").authenticated()
                        // Cancelacion: ambos roles. La propiedad de la reserva es una regla de
                        // negocio y la comprueba el servicio, no la cadena (RN-03, design D-07).
                        .requestMatchers(HttpMethod.PATCH, "/api/reservas/*/cancelacion").authenticated()
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
