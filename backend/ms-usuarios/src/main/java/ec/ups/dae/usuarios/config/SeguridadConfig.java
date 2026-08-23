package ec.ups.dae.usuarios.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.ups.dae.usuarios.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    /**
     * La contrasena se persiste como hash BCrypt (prefijo $2), condicion que exige el CHECK
     * ck_usuario_password_bcrypt de la tabla usuario.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        // Registro e inicio de sesion son publicos por contrato.
                        .requestMatchers(HttpMethod.POST, "/api/usuarios").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/sesiones").permitAll()
                        // Sin estas tres, springdoc responde 401 y E-06 no se puede demostrar.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Listado y cambio de estado son solo del ADMIN (HU-03, HU-04).
                        .requestMatchers(HttpMethod.GET, "/api/usuarios").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/usuarios/*/estado").hasRole("ADMIN")
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
