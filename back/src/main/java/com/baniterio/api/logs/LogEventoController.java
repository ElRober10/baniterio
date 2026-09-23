package com.baniterio.api.logs;

import com.baniterio.api.auth.UsuarioPrincipal;
import java.time.LocalDate;

import com.baniterio.api.logs.dto.LogClienteRequest;
import com.baniterio.api.logs.dto.LogEventoPageDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /** Consulta paginada y filtrable del registro. Solo administrador (lo comprueba el servicio). */
    @GetMapping
    public LogEventoPageDto listar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) String origen,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamano) {
        return service.listar(principal == null ? null : principal.id(), usuarioId, origen, desde, hasta, pagina,
                tamano);
    }
}
