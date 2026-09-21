package com.baniterio.api.preciobebida;

import java.util.List;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.preciobebida.dto.AnadirTamanoRequest;
import com.baniterio.api.preciobebida.dto.CrearTiendaRequest;
import com.baniterio.api.preciobebida.dto.EventoPrecioBebidaDto;
import com.baniterio.api.preciobebida.dto.GrillaAlcoholResponse;
import com.baniterio.api.preciobebida.dto.GrillaArticuloResponse;
import com.baniterio.api.preciobebida.dto.GuardarPrecioArticuloRequest;
import com.baniterio.api.preciobebida.dto.GuardarProductoKiloRequest;
import com.baniterio.api.preciobebida.dto.GuardarTamanoArticuloRequest;
import com.baniterio.api.preciobebida.dto.GuardarPrecioRequest;
import com.baniterio.api.preciobebida.dto.TiendaDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Precio bebidas: lo ve cualquier miembro logueado; solo un admin de verdad edita. */
@Tag(name = "Precio bebidas", description = "Años, eventos y rejilla de precio de las bebidas alcohólicas.")
@RestController
@RequestMapping("/api/v1/precio-bebida")
public class PrecioBebidaController {

    private final PrecioBebidaService service;

    public PrecioBebidaController(PrecioBebidaService service) {
        this.service = service;
    }

    @GetMapping("/eventos")
    public List<EventoPrecioBebidaDto> eventos() {
        return service.eventos();
    }

    @GetMapping("/tiendas")
    public List<TiendaDto> tiendas() {
        return service.tiendas();
    }

    /** Alta de una tienda en el catálogo de la peña. Solo admin. */
    @PostMapping("/tiendas")
    @ResponseStatus(HttpStatus.CREATED)
    public TiendaDto crearTienda(@AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody CrearTiendaRequest req) {
        return service.crearTienda(principal.id(), req);
    }

    /** Rejilla de precios de bebidas alcohólicas de un evento. */
    @GetMapping("/eventos/{eventoId}/alcohol")
    public GrillaAlcoholResponse alcohol(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId) {
        return service.alcohol(principal.id(), eventoId);
    }

    /** Guarda (o borra, con precio null) una celda de la rejilla. Solo admin. */
    @PutMapping("/eventos/{eventoId}/alcohol/precio")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void guardarPrecio(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @Valid @RequestBody GuardarPrecioRequest req) {
        service.guardarPrecio(principal.id(), eventoId, req);
    }

    /** Añade una pestaña de tamaño suelta a la rejilla de un evento. Solo admin. */
    @PostMapping("/eventos/{eventoId}/alcohol/tamanos")
    @ResponseStatus(HttpStatus.CREATED)
    public List<String> anadirTamano(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @Valid @RequestBody AnadirTamanoRequest req) {
        return service.anadirTamano(principal.id(), eventoId, req);
    }

    /**
     * Rejilla de precios de una sección sin tamaños: "refrescos", "cerveza",
     * "limpieza" o "comida" (en mayúsculas).
     */
    @GetMapping("/eventos/{eventoId}/articulos/{categoria}")
    public GrillaArticuloResponse articulos(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @PathVariable String categoria) {
        return service.articulos(principal.id(), eventoId, categoria.toUpperCase());
    }

    /** Guarda (o borra, con precio null) una celda de la rejilla de artículos. Solo admin. */
    @PutMapping("/eventos/{eventoId}/articulos/{categoria}/precio")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void guardarPrecioArticulo(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @PathVariable String categoria,
            @Valid @RequestBody GuardarPrecioArticuloRequest req) {
        service.guardarPrecioArticulo(principal.id(), eventoId, categoria.toUpperCase(), req);
    }

    /** Apunta el tamaño de botella (1, 1.5 o 2 litros) de un refresco. Solo admin. */
    @PutMapping("/eventos/{eventoId}/articulos/{categoria}/tamano")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void guardarTamanoArticulo(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @PathVariable String categoria,
            @Valid @RequestBody GuardarTamanoArticuloRequest req) {
        service.guardarTamanoArticulo(principal.id(), eventoId, categoria.toUpperCase(), req);
    }

    /** Apunta precio por kilo y peso estimado de un embutido de Jamones Duriber. Solo admin. */
    @PutMapping("/eventos/{eventoId}/articulos/COMIDA/kilo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void guardarProductoKilo(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @Valid @RequestBody GuardarProductoKiloRequest req) {
        service.guardarProductoKilo(principal.id(), eventoId, req);
    }
}
