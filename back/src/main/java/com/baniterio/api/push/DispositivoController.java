package com.baniterio.api.push;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.push.dto.RegistrarDispositivoRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro del token de push del móvil del usuario que tiene sesión. Ruta
 * autenticada (sin token JWT → 401, lo pone {@code SecurityConfig}). El
 * {@code DELETE} es idempotente y solo afecta al dispositivo del propio usuario.
 */
@Tag(name = "Dispositivos (push)", description = "Alta y baja del token de notificaciones push del dispositivo.")
@RestController
@RequestMapping("/api/v1/dispositivos")
public class DispositivoController {

    private final DispositivoService dispositivos;

    public DispositivoController(DispositivoService dispositivos) {
        this.dispositivos = dispositivos;
    }

    /** Registra el token de push (FCM/APNs) del dispositivo del usuario con sesión. */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrar(@AuthenticationPrincipal UsuarioPrincipal principal,
                          @Valid @RequestBody RegistrarDispositivoRequest req) {
        dispositivos.registrar(principal.id(), req.token(), req.plataforma());
    }

    /** Da de baja el token de push indicado (idempotente; solo del propio usuario). */
    @DeleteMapping("/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void darDeBaja(@AuthenticationPrincipal UsuarioPrincipal principal,
                          @PathVariable String token) {
        dispositivos.darDeBaja(principal.id(), token);
    }
}
