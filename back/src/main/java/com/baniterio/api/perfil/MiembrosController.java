package com.baniterio.api.perfil;

import java.util.List;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.perfil.dto.TarjetaMiembroResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sección Miembros: {@code GET /api/v1/miembros} devuelve las tarjetas de todos
 * los miembros con perfil completado, ya ordenadas para quien pregunta (su
 * tarjeta primero, luego su pareja, sus hijos registrados y el resto).
 *
 * <p>No confundir con {@code GET /api/v1/admin/miembros}, que es del panel de
 * administración y sí expone teléfono y rol. Aquí solo datos públicos de la peña.
 */
@RestController
@RequestMapping("/api/v1/miembros")
public class MiembrosController {

    private final MiembrosService miembrosService;

    public MiembrosController(MiembrosService miembrosService) {
        this.miembrosService = miembrosService;
    }

    @GetMapping
    public List<TarjetaMiembroResponse> miembros(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return miembrosService.tarjetas(principal.id());
    }
}
