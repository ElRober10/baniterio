package com.baniterio.api.evento;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.evento.dto.AnadirAsistenteRequest;
import com.baniterio.api.evento.dto.AsistenciaResumen;
import com.baniterio.api.evento.dto.EventoDetalle;
import com.baniterio.api.evento.dto.ListadoAsistentesResponse;
import com.baniterio.api.evento.dto.MandarNotificacionRequest;
import com.baniterio.api.evento.dto.PendientesRespuestaResponse;
import com.baniterio.api.evento.dto.ResponderAsistenciaRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @GetMapping("/pendientes-respuesta")
    public PendientesRespuestaResponse pendientes(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return new PendientesRespuestaResponse(asistenciaService.pendientesRespuesta(principal.id()));
    }

    @GetMapping("/{id}/asistentes")
    public ListadoAsistentesResponse asistentes(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id) {
        return asistenciaService.listadoAsistentes(principal.id(), id);
    }

    @PutMapping("/{id}/asistencia")
    public EventoDetalle responder(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody ResponderAsistenciaRequest req) {
        Long objetivo = req.paraUsuarioId() != null ? req.paraUsuarioId() : principal.id();
        asistenciaService.responder(principal.id(), id, req.paraUsuarioId(), req.estado());
        return eventoService.detalle(objetivo, id);
    }

    @PostMapping("/{id}/notificacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mandarNotificacion(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) MandarNotificacionRequest req) {
        asistenciaService.mandarNotificacion(principal.id(), id, req == null ? null : req.texto());
    }

    @PostMapping("/{id}/asistencias")
    @ResponseStatus(HttpStatus.CREATED)
    public AsistenciaResumen anadir(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody AnadirAsistenteRequest req) {
        return asistenciaService.anadirAMano(principal.id(), id, req.nombre(), req.estado(), req.ficha());
    }

    @DeleteMapping("/{id}/asistencias/{asistenciaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @PathVariable Long asistenciaId) {
        asistenciaService.quitarAMano(principal.id(), id, asistenciaId);
    }
}
