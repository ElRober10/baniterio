package com.baniterio.api.inventario;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.inventario.dto.ActualizarArticuloRequest;
import com.baniterio.api.inventario.dto.ArticuloDto;
import com.baniterio.api.inventario.dto.CrearArticuloRequest;
import com.baniterio.api.inventario.dto.EnviarAEventoRequest;
import com.baniterio.api.inventario.dto.EnviarCategoriaRequest;
import com.baniterio.api.inventario.dto.InventarioEventoResponse;
import com.baniterio.api.inventario.dto.InventarioResponse;
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
 * Sección Inventario: consultar lo almacenado (cualquier peñista) y ajustar un
 * artículo (área {@code INVENTARIO}, se comprueba en el servicio). De momento
 * solo se editan artículos que ya existen; el alta/baja llega después.
 */
@Tag(name = "Inventario", description = "Material y bebida almacenados por la peña, por categorías.")
@RestController
@RequestMapping("/api/v1/inventario")
public class InventarioController {

    private final InventarioService service;

    public InventarioController(InventarioService service) {
        this.service = service;
    }

    /** El inventario completo agrupado por categoría. {@code puedoEditar} dice si quien pregunta puede tocar. */
    @GetMapping
    public InventarioResponse ver(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return service.ver(principal.id());
    }

    /** Da de alta un artículo en una categoría. Área {@code INVENTARIO}. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ArticuloDto crear(@AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody CrearArticuloRequest req) {
        return service.crear(principal.id(), req);
    }

    /** Cambia nombre, tamaño y cantidad de un artículo. Área {@code INVENTARIO}. */
    @PutMapping("/{id}")
    public ArticuloDto actualizar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody ActualizarArticuloRequest req) {
        return service.actualizar(principal.id(), id, req);
    }

    /** Da de baja un artículo. Área {@code INVENTARIO}. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id) {
        service.borrar(principal.id(), id);
    }

    /** Mueve toda la cantidad de un artículo al inventario de un evento. Área {@code INVENTARIO}. */
    @PostMapping("/{id}/enviar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enviar(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody EnviarAEventoRequest req) {
        service.enviar(principal.id(), id, req.eventoId());
    }

    /** "Enviar todo": mueve al evento todas las filas de una categoría con cantidad &gt; 0. Área {@code INVENTARIO}. */
    @PostMapping("/enviar-categoria")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enviarCategoria(@AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody EnviarCategoriaRequest req) {
        service.enviarCategoria(principal.id(), req.categoria(), req.eventoId());
    }

    /** El inventario que se ha enviado a un evento, agrupado por categoría. Cualquier usuario logueado. */
    @GetMapping("/evento/{eventoId}")
    public InventarioEventoResponse verEvento(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId) {
        return service.verEvento(principal.id(), eventoId);
    }

    /** Devuelve una línea entera del inventario de la fiesta al inventario general. Área {@code INVENTARIO}. */
    @PostMapping("/evento/{eventoId}/{articuloEventoId}/devolver")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void devolver(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @PathVariable Long articuloEventoId) {
        service.devolver(principal.id(), eventoId, articuloEventoId);
    }
}
