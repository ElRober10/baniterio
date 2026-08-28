package com.baniterio.api.auth;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro que se ejecuta ANTES que los controladores en cada petición HTTP.
 *
 * <p>Si la petición trae cabecera {@code Authorization: Bearer <token>}, valida
 * el token con {@link JwtService} y, si es correcto, registra al usuario como
 * "autenticado" en el {@code SecurityContext} de Spring Security (dándole la
 * autoridad {@code ROLE_SUPERADMIN} si procede). A partir de ese momento
 * {@code @AuthenticationPrincipal} funciona y las rutas protegidas dejan pasar.
 *
 * <p>Si no hay cabecera, o el token es inválido, no hace nada y la petición
 * sigue como anónima: será {@code SecurityConfig} quien decida si esa ruta
 * exige estar autenticado (→ 401) o es pública.
 *
 * <p>{@code OncePerRequestFilter} garantiza que se ejecuta una sola vez por
 * petición aunque Spring haga reenvíos internos.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIJO)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            jwtService.verificar(header.substring(PREFIJO.length())).ifPresent(principal -> {
                var authorities = principal.esSuperadmin()
                        ? List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN"))
                        : List.<SimpleGrantedAuthority>of();
                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(request, response);
    }
}
