package com.baniterio.api.auth;

import com.baniterio.api.auth.dto.LoginRequest;
import com.baniterio.api.auth.dto.LoginResponse;
import com.baniterio.api.auth.dto.RegistroRequest;
import com.baniterio.api.auth.dto.SolicitudIngresoRequest;
import com.baniterio.api.auth.dto.UsuarioResponse;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Punto de entrada HTTP de la autenticación. Solo traduce entre HTTP y el
 * dominio: valida el cuerpo de la petición ({@code @Valid}) y delega la lógica
 * en {@link AuthService}. Devuelve records (los DTO de {@code auth.dto}) que
 * Spring convierte a JSON.
 *
 * <ul>
 *   <li>{@code POST /api/v1/auth/registro} — alta de usuario (201).
 *   <li>{@code POST /api/v1/auth/login} — devuelve un JWT (200).
 *   <li>{@code GET  /api/v1/auth/yo} — datos del usuario del token; ruta
 *       protegida (necesita {@code Authorization: Bearer}).
 * </ul>
 *
 * <p>Los errores (403/409/400/401) no se manejan aquí: las excepciones que
 * lanza {@link AuthService} las recoge {@link com.baniterio.api.web.ApiExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UsuarioRepository usuarios;
    private final AuthService authService;

    public AuthController(UsuarioRepository usuarios, AuthService authService) {
        this.usuarios = usuarios;
        this.authService = authService;
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registro(@Valid @RequestBody RegistroRequest req) {
        return UsuarioResponse.de(authService.registrar(req));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    /** Solicitud de acceso para un teléfono que no está en la lista de la peña. */
    @PostMapping("/solicitudes")
    @ResponseStatus(HttpStatus.CREATED)
    public void solicitarIngreso(@Valid @RequestBody SolicitudIngresoRequest req) {
        authService.solicitarIngreso(req);
    }

    @GetMapping("/yo")
    public ResponseEntity<UsuarioResponse> yo(@AuthenticationPrincipal UsuarioPrincipal principal) {
        // `activo` es la única palanca de revocación con JWT stateless de 7 días:
        // se comprueba en cada petición, no solo al iniciar sesión.
        return usuarios.findById(principal.id())
                .filter(Usuario::isActivo)
                .map(UsuarioResponse::de)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build());
    }
}
