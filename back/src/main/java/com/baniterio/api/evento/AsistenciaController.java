package com.baniterio.api.evento;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.evento.dto.EventoDetalle;
import com.baniterio.api.evento.dto.ResponderAsistenciaRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asistencia a un evento (pieza 3a): responder, convocar, añadir a mano y
 * pendientes de respuesta. Comparte prefijo con {@link EventoController} pero las
 * rutas no se solapan. Solo traduce HTTP ↔ dominio; el id del usuario sale del token.
 */
@RestController
@RequestMapping("/api/v1/eventos")
public class AsistenciaController {

    private final AsistenciaService asistenciaService;
    private final EventoService eventoService;

    public AsistenciaController(AsistenciaService asistenciaService, EventoService eventoService) {
        this.asistenciaService = asistenciaService;
        this.eventoService = eventoService;
    }

    @PutMapping("/{id}/asistencia")
    public EventoDetalle responder(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody ResponderAsistenciaRequest req) {
        asistenciaService.responder(principal.id(), id, req.estado());
        return eventoService.detalle(principal.id(), id);
    }
}
