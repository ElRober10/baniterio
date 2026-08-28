package com.baniterio.api.auth;

import com.baniterio.api.auth.dto.UsuarioResponse;
import com.baniterio.api.identidad.UsuarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UsuarioRepository usuarios;

    public AuthController(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping("/yo")
    public ResponseEntity<UsuarioResponse> yo(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return usuarios.findById(principal.id())
                .map(UsuarioResponse::de)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build());
    }
}
