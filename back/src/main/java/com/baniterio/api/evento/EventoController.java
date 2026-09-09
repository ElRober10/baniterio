package com.baniterio.api.evento;

import java.util.Map;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.evento.dto.CrearSolicitudEventoRequest;
import com.baniterio.api.evento.dto.EventoDetalle;
import com.baniterio.api.evento.dto.EventosOcultosResponse;
import com.baniterio.api.evento.dto.GuardarEventoRequest;
import com.baniterio.api.evento.dto.ListaEventosResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sección Eventos para cualquier miembro: listado paginado y detalle. Solo
 * traduce HTTP ↔ dominio; el id del usuario sale del token. Errores →
 * {@link com.baniterio.api.web.ApiExceptionHandler}.
 */
@Tag(name = "Eventos", description = "Listado, detalle, alta/edición de eventos y solicitudes para organizarlos.")
@RestController
@RequestMapping("/api/v1/eventos")
public class EventoController {

    private final EventoService eventoService;

    public EventoController(EventoService eventoService) {
        this.eventoService = eventoService;
    }

    /** Listado paginado de eventos visibles (próximos por fecha y luego pasados). */
    @GetMapping
    public ListaEventosResponse listar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam(defaultValue = "0") int pagina) {
        return eventoService.listar(principal.id(), pagina);
    }

    /** Los eventos "borrados" (ocultos, recuperables). Solo admin/superadmin. */
    @GetMapping("/ocultos")
    public EventosOcultosResponse ocultos(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return new EventosOcultosResponse(eventoService.listarOcultos(principal.id()));
    }

    /** Detalle completo de un evento. */
    @GetMapping("/{id}")
    public EventoDetalle detalle(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id) {
        return eventoService.detalle(principal.id(), id);
    }

    /** Crea un evento. Devuelve 201 con el detalle. Solo admin/superadmin. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventoDetalle crear(@AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody GuardarEventoRequest req) {
        return eventoService.crear(principal.id(), req);
    }

    /** Edita un evento existente. Solo admin/superadmin. */
    @PutMapping("/{id}")
    public EventoDetalle editar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody GuardarEventoRequest req) {
        return eventoService.editar(principal.id(), id, req);
    }

    /** "Borra" el evento ocultándolo (no lo quita de la BBDD). Ver {@link #recuperar}. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ocultar(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id) {
        eventoService.ocultar(principal.id(), id);
    }

    /** Deshace {@link #ocultar}. */
    @PutMapping("/{id}/recuperar")
    public EventoDetalle recuperar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id) {
        return eventoService.recuperar(principal.id(), id);
    }

    /** Un miembro pide crédito/permiso para organizar un evento; queda PENDIENTE de que lo apruebe un admin. */
    @PostMapping("/solicitudes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> solicitarCredito(@AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody(required = false) CrearSolicitudEventoRequest req) {
        Long id = eventoService.solicitarCredito(principal.id(), req == null ? null : req.mensaje());
        return Map.of("id", id, "estado", "PENDIENTE");
    }
}
