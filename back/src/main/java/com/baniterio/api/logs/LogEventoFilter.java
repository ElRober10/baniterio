package com.baniterio.api.logs;

import java.io.IOException;
import java.util.Set;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.web.ApiExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Registra automáticamente toda petición de escritura de la API en
 * {@code log_evento}: quién la hizo, qué endpoint, y si salió bien o mal (el
 * código de error, si lo hay, lo deja {@link ApiExceptionHandler} en un
 * atributo de la petición). Se engancha DESPUÉS del filtro JWT
 * ({@code SecurityConfig}) para poder leer el usuario autenticado.
 */
@Component
public class LogEventoFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_ESCRITURA = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final LogEventoService service;

    public LogEventoFilter(LogEventoService service) {
        this.service = service;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        return !METODOS_ESCRITURA.contains(request.getMethod())
                || !ruta.startsWith("/api/v1/")
                || ruta.startsWith("/api/v1/logs")
                || ruta.equals("/api/v1/auth/login")
                || ruta.equals("/api/v1/auth/registro");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        String codigo = (String) request.getAttribute(ApiExceptionHandler.ATRIBUTO_CODIGO_LOG);
        service.registrarAccion(usuarioIdActual(), request.getMethod(), request.getRequestURI(),
                response.getStatus(), codigo);
    }

    private Long usuarioIdActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal p) {
            return p.id();
        }
        return null;
    }
}
