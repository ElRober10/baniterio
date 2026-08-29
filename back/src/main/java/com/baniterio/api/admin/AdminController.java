package com.baniterio.api.admin;

import java.util.List;

import com.baniterio.api.admin.dto.ActivoRequest;
import com.baniterio.api.admin.dto.AprobarResponse;
import com.baniterio.api.admin.dto.AreasRequest;
import com.baniterio.api.admin.dto.MiembroResumen;
import com.baniterio.api.admin.dto.RechazoRequest;
import com.baniterio.api.admin.dto.RolRequest;
import com.baniterio.api.admin.dto.SolicitudResumen;
import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.EstadoSolicitud;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * Endpoints del panel de administración. Solo traduce HTTP ↔ dominio y comprueba
 * el acceso al área con {@link ServicioPermisos} (nunca se fía de lo que diga el
 * cliente); la lógica vive en {@link AdminService}. Los errores (403/409/...) los
 * traduce {@link com.baniterio.api.web.ApiExceptionHandler}.
 *
 * <p>Dos bloques: solicitudes de ingreso (área {@code ADMIN_SOLICITUDES}) y
 * gestión de miembros — rol, activo y áreas concedidas — (área
 * {@code ADMIN_PERMISOS}).
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final ServicioPermisos permisos;

    public AdminController(AdminService adminService, ServicioPermisos permisos) {
        this.adminService = adminService;
        this.permisos = permisos;
    }

    /** Exige que el usuario del token tenga acceso al área dada, o lanza {@link SinPermisoException}. */
    private void exigirArea(UsuarioPrincipal principal, AreaProtegida area) {
        if (principal == null || !permisos.puede(principal.id(), area)) {
            throw new SinPermisoException();
        }
    }

    @GetMapping("/solicitudes")
    public List<SolicitudResumen> solicitudes(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam(defaultValue = "PENDIENTE") EstadoSolicitud estado) {
        exigirArea(principal, AreaProtegida.ADMIN_SOLICITUDES);
        return adminService.listarSolicitudes(estado);
    }

    @PostMapping("/solicitudes/{id}/aprobar")
    public AprobarResponse aprobar(
            @AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id) {
        exigirArea(principal, AreaProtegida.ADMIN_SOLICITUDES);
        return adminService.aprobarSolicitud(id, principal.id());
    }

    @PostMapping("/solicitudes/{id}/rechazar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rechazar(
            @AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody(required = false) RechazoRequest req) {
        exigirArea(principal, AreaProtegida.ADMIN_SOLICITUDES);
        adminService.rechazarSolicitud(id, principal.id(), req == null ? null : req.motivo());
    }

    // --- Gestión de miembros (área ADMIN_PERMISOS) ---

    @GetMapping("/miembros")
    public List<MiembroResumen> miembros(@AuthenticationPrincipal UsuarioPrincipal principal) {
        exigirArea(principal, AreaProtegida.ADMIN_PERMISOS);
        return adminService.listarMiembros();
    }

    @PutMapping("/miembros/{id}/rol")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rol(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody RolRequest req) {
        exigirArea(principal, AreaProtegida.ADMIN_PERMISOS);
        adminService.cambiarRol(id, principal.id(), req.rol());
    }

    @PutMapping("/miembros/{id}/activo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activo(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody ActivoRequest req) {
        exigirArea(principal, AreaProtegida.ADMIN_PERMISOS);
        adminService.cambiarActivo(id, principal.id(), req.activo());
    }

    @PutMapping("/miembros/{id}/areas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void areas(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody AreasRequest req) {
        exigirArea(principal, AreaProtegida.ADMIN_PERMISOS);
        adminService.reemplazarAreas(id, principal.id(), req.areas());
    }
}
