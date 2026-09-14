package com.baniterio.api.evento;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.evento.dto.AnadirAsistenteRequest;
import com.baniterio.api.evento.dto.AsistenciaResumen;
import com.baniterio.api.evento.dto.ConfirmarPagoRequest;
import com.baniterio.api.evento.dto.DeclararPagoRequest;
import com.baniterio.api.evento.dto.EventoDetalle;
import com.baniterio.api.evento.dto.ListadoAsistentesResponse;
import com.baniterio.api.evento.dto.MandarNotificacionRequest;
import com.baniterio.api.evento.dto.PendientesRespuestaResponse;
import com.baniterio.api.evento.dto.ResponderAsistenciaRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asistencia a un evento (pieza 3a): responder, convocar, añadir a mano y
 * pendientes de respuesta. Comparte prefijo con {@link EventoController} pero las
 * rutas no se solapan. Solo traduce HTTP ↔ dominio; el id del usuario sale del token.
 */
@Tag(name = "Asistencia", description = "Respuesta a la convocatoria de un evento, convocatoria, asistentes y pagos.")
@RestController
@RequestMapping("/api/v1/eventos")
public class AsistenciaController {

    private final AsistenciaService asistenciaService;
    private final EventoService eventoService;
    private final PagoDeclaradoService pagoDeclaradoService;

    public AsistenciaController(AsistenciaService asistenciaService, EventoService eventoService,
            PagoDeclaradoService pagoDeclaradoService) {
        this.asistenciaService = asistenciaService;
        this.eventoService = eventoService;
        this.pagoDeclaradoService = pagoDeclaradoService;
    }

    /** Eventos en los que todavía no he dicho si voy. */
    @GetMapping("/pendientes-respuesta")
    public PendientesRespuestaResponse pendientes(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return new PendientesRespuestaResponse(asistenciaService.pendientesRespuesta(principal.id()));
    }

    /** Lista de asistentes de un evento con su estado de respuesta y de pago. */
    @GetMapping("/{id}/asistentes")
    public ListadoAsistentesResponse asistentes(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id) {
        return asistenciaService.listadoAsistentes(principal.id(), id);
    }

    /** Responde a la convocatoria (voy / no voy). Con {@code paraUsuarioId} respondo por mi pareja o hijo. */
    @PutMapping("/{id}/asistencia")
    public EventoDetalle responder(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody ResponderAsistenciaRequest req) {
        Long objetivo = req.paraUsuarioId() != null ? req.paraUsuarioId() : principal.id();
        asistenciaService.responder(principal.id(), id, req.paraUsuarioId(), req.estado());
        return eventoService.detalle(objetivo, id);
    }

    /** Convoca el evento: manda notificación push a los miembros, con texto opcional. Solo organizador/admin. */
    @PostMapping("/{id}/notificacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mandarNotificacion(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) MandarNotificacionRequest req) {
        asistenciaService.mandarNotificacion(principal.id(), id, req == null ? null : req.texto());
    }

    /** Añade a mano un asistente suelto (invitado sin cuenta) al evento. Devuelve 201. */
    @PostMapping("/{id}/asistencias")
    @ResponseStatus(HttpStatus.CREATED)
    public AsistenciaResumen anadir(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody AnadirAsistenteRequest req) {
        return asistenciaService.anadirAMano(principal.id(), id, req.nombre(), req.telefono(), req.estado(),
                req.ficha());
    }

    /** Quita un asistente añadido a mano. */
    @DeleteMapping("/{id}/asistencias/{asistenciaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @PathVariable Long asistenciaId) {
        asistenciaService.quitarAMano(principal.id(), id, asistenciaId);
    }

    /** El admin marca el pago de un asistente como confirmado, indicando el método (efectivo, bizum...). */
    @PutMapping("/{id}/asistencias/{asistenciaId}/pago")
    public ListadoAsistentesResponse confirmarPago(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @PathVariable Long asistenciaId,
            @Valid @RequestBody ConfirmarPagoRequest req) {
        return asistenciaService.confirmarPago(principal.id(), id, asistenciaId, req.metodo());
    }

    /** Deshace la confirmación de pago de un asistente. */
    @DeleteMapping("/{id}/asistencias/{asistenciaId}/pago")
    public ListadoAsistentesResponse deshacerPago(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @PathVariable Long asistenciaId) {
        return asistenciaService.deshacerPago(principal.id(), id, asistenciaId);
    }

    /** Declaro que he pagado mi parte del evento (bizum / transferencia / efectivo); queda pendiente de que el admin lo confirme. */
    @PostMapping("/{id}/pagos-declarados")
    public EventoDetalle declararPago(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody DeclararPagoRequest req) {
        pagoDeclaradoService.declarar(principal.id(), id, req);
        return eventoService.detalle(principal.id(), id);
    }

    /** Anula mi propia declaración de pago de este evento. */
    @DeleteMapping("/{id}/pagos-declarados/mia")
    public EventoDetalle anularPagoDeclarado(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id) {
        pagoDeclaradoService.anularMia(principal.id(), id);
        return eventoService.detalle(principal.id(), id);
    }
}
