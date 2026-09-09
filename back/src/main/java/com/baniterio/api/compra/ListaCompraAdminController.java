package com.baniterio.api.compra;

import java.util.List;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.compra.dto.AjustarReglaRequest;
import com.baniterio.api.compra.dto.CrearReglaRequest;
import com.baniterio.api.compra.dto.EventoListaCompraDto;
import com.baniterio.api.compra.dto.ListaCompraAdminResponse;
import com.baniterio.api.compra.dto.ReglaCompraEventoDto;
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

/** "Cantidades para eventos": el admin ajusta cantidades y añade/quita reglas. Área INVENTARIO. */
@Tag(name = "Lista de la compra (admin)",
        description = "Ajuste de cantidades y reglas de la lista de la compra por evento.")
@RestController
@RequestMapping("/api/v1/admin/lista-compra")
public class ListaCompraAdminController {

    private final ListaCompraService service;

    public ListaCompraAdminController(ListaCompraService service) {
        this.service = service;
    }

    @GetMapping("/eventos")
    public List<EventoListaCompraDto> eventos(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return service.eventos(principal.id());
    }

    @GetMapping("/eventos/{eventoId}")
    public ListaCompraAdminResponse verEvento(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId) {
        return service.verAdmin(principal.id(), eventoId);
    }

    @PutMapping("/eventos/{eventoId}/reglas/{reglaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ajustar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @PathVariable Long reglaId,
            @Valid @RequestBody AjustarReglaRequest req) {
        service.ajustarRegla(principal.id(), eventoId, reglaId, req);
    }

    @PostMapping("/eventos/{eventoId}/reglas")
    @ResponseStatus(HttpStatus.CREATED)
    public ReglaCompraEventoDto crear(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @Valid @RequestBody CrearReglaRequest req) {
        return service.crearRegla(principal.id(), eventoId, req);
    }

    @DeleteMapping("/eventos/{eventoId}/reglas/{reglaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @PathVariable Long reglaId) {
        service.borrarRegla(principal.id(), eventoId, reglaId);
    }
}
