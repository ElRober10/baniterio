package com.baniterio.api.auth;

import com.baniterio.api.auth.dto.LoginRequest;
import com.baniterio.api.auth.dto.LoginResponse;
import com.baniterio.api.auth.dto.RegistroRequest;
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
