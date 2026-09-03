package com.baniterio.api.evento;

import java.util.List;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.evento.dto.RechazoEventoRequest;
import com.baniterio.api.evento.dto.SolicitudEventoResumen;
import com.baniterio.api.identidad.EstadoSolicitud;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bloque del panel de administración para las solicitudes de evento. El acceso
 * se comprueba en el servicio ({@code esAdministrador}); no lleva {@code areaGuard}
 * porque no hay área concedible para esto.
 */
@RestController
@RequestMapping("/api/v1/admin/solicitudes-evento")
public class SolicitudEventoAdminController {

    private final SolicitudEventoAdminService service;

    public SolicitudEventoAdminController(SolicitudEventoAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<SolicitudEventoResumen> listar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam(defaultValue = "PENDIENTE") EstadoSolicitud estado) {
        return service.listar(principal.id(), estado);
    }

    @PostMapping("/{id}/aprobar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void aprobar(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id) {
        service.aprobar(id, principal.id());
    }

    @PostMapping("/{id}/rechazar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rechazar(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody(required = false) RechazoEventoRequest req) {
        service.rechazar(id, principal.id(), req == null ? null : req.motivo());
    }
}
