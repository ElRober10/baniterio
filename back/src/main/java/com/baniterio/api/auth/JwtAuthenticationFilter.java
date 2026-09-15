package com.baniterio.api.auth;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
 * <p>Si el token es el de la cuenta demo pública ({@code esDemo}), se deja
 * pasar la autenticación (puede navegar y leer todo) pero se corta aquí mismo
 * cualquier petición que no sea de solo lectura (todo lo que no sea GET/HEAD/
 * OPTIONS) con un 403, sin llegar a los controladores: la demo no puede
 * modificar ni inscribirse en nada.
 *
 * <p>{@code OncePerRequestFilter} garantiza que se ejecuta una sola vez por
 * petición aunque Spring haga reenvíos internos.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    /** Métodos de solo lectura, los únicos que la cuenta demo puede hacer. */
    private static final Set<String> METODOS_LECTURA =
            Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name());

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        Optional<UsuarioPrincipal> principal = Optional.empty();
        if (header != null && header.startsWith(PREFIJO)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            principal = jwtService.verificar(header.substring(PREFIJO.length()));
            principal.ifPresent(p -> {
                var authorities = p.esSuperadmin()
                        ? List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN"))
                        : List.<SimpleGrantedAuthority>of();
                var auth = new UsernamePasswordAuthenticationToken(p, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }

        if (principal.map(UsuarioPrincipal::esDemo).orElse(false)
                && !METODOS_LECTURA.contains(request.getMethod())) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return;
        }
        chain.doFilter(request, response);
    }
}
