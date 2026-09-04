package com.baniterio.api.evento;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.evento.dto.FichaBebidaRequest;
import com.baniterio.api.evento.dto.FichaBebidaResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ficha de bebida de un evento (pieza 3b). Comparte prefijo con
 * {@link EventoController} / {@link AsistenciaController} pero las rutas no se
 * solapan. Solo traduce HTTP ↔ dominio.
 */
@RestController
@RequestMapping("/api/v1/eventos")
public class FichaBebidaController {

    private final FichaBebidaService fichaBebidaService;

    public FichaBebidaController(FichaBebidaService fichaBebidaService) {
        this.fichaBebidaService = fichaBebidaService;
    }

    @PutMapping("/{id}/ficha-bebida")
    public FichaBebidaResponse guardar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody FichaBebidaRequest req) {
        return fichaBebidaService.guardar(principal.id(), id, req);
    }
}
