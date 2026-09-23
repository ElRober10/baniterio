package com.baniterio.api.logs;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.logs.dto.LogClienteRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Registro de eventos y errores, solo para depuración interna (ver spec 2026-09-23). */
@Tag(name = "Logs", description = "Registro de acciones y errores de la app.")
@RestController
@RequestMapping("/api/v1/logs")
public class LogEventoController {

    private final LogEventoService service;

    public LogEventoController(LogEventoService service) {
        this.service = service;
    }

    /** Público: un error de cliente puede ocurrir antes de tener sesión. Siempre 202. */
    @PostMapping("/cliente")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void logCliente(@AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestBody LogClienteRequest req) {
        service.registrarCliente(principal == null ? null : principal.id(), req.origen(), req.pantalla(),
                req.mensaje());
    }
}
