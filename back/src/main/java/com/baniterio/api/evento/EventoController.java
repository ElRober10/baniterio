package com.baniterio.api.evento;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.evento.dto.EventoDetalle;
import com.baniterio.api.evento.dto.ListaEventosResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sección Eventos para cualquier miembro: listado paginado y detalle. Crear,
 * editar y borrar se añaden en tareas siguientes. Solo traduce HTTP ↔ dominio;
 * el id del usuario sale del token. Errores → {@link com.baniterio.api.web.ApiExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/eventos")
public class EventoController {

    private final EventoService eventoService;

    public EventoController(EventoService eventoService) {
        this.eventoService = eventoService;
    }

    @GetMapping
    public ListaEventosResponse listar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam(defaultValue = "0") int pagina) {
        return eventoService.listar(principal.id(), pagina);
    }

    @GetMapping("/{id}")
    public EventoDetalle detalle(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id) {
        return eventoService.detalle(principal.id(), id);
    }
}
