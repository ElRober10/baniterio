package com.baniterio.api.config;

import java.util.List;

import com.baniterio.api.auth.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuración de Spring Security. Define la "cadena de filtros" por la que
 * pasa cada petición HTTP antes de llegar a un controlador.
 *
 * <p>Decisiones de este proyecto:
 * <ul>
 *   <li><b>CSRF desactivado</b>: solo tiene sentido con sesiones por cookie;
 *       aquí la autenticación va por cabecera Bearer, no por cookie.
 *   <li><b>CORS activado</b>: el navegador solo deja al front (localhost:4200)
 *       llamar a la API (localhost:8080) si la API lo autoriza explícitamente
 *       (bean {@link #corsConfigurationSource}).
 *   <li><b>STATELESS</b>: el servidor no guarda sesión; cada petición se
 *       autentica sola con su JWT.
 *   <li><b>Rutas públicas</b>: solo {@code /health}, {@code /auth/registro} y
 *       {@code /auth/login}. Cualquier otra ruta exige estar autenticado.
 *   <li><b>401 en vez de 403</b>: sin el {@code authenticationEntryPoint},
 *       Spring devolvería 403 a una petición sin token; forzamos 401.
 *   <li>{@code addFilterBefore(jwtFilter, ...)}: mete nuestro filtro JWT en la
 *       cadena, antes del filtro de login por usuario/contraseña.
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health", "/api/v1/auth/registro", "/api/v1/auth/login").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        (req, res, ex) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** BCrypt: guarda las contraseñas como hash con sal, y a propósito es lento
     *  para dificultar los ataques de fuerza bruta. Lo usan AuthService (al
     *  registrar) y para comparar (al hacer login). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Qué orígenes/métodos/cabeceras del navegador acepta la API. Los orígenes
     *  salen de la config ({@code app.cors.allowed-origins}). */
    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.cors().allowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
