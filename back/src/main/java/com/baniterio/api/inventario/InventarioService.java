package com.baniterio.api.inventario;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.compra.LineaCompraEventoRepository;
import com.baniterio.api.evento.EventoNoEncontradoException;
import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.PenaPilotoService;
import com.baniterio.api.inventario.dto.ActualizarArticuloRequest;
import com.baniterio.api.inventario.dto.ArticuloDto;
import com.baniterio.api.inventario.dto.CategoriaEventoDto;
import com.baniterio.api.inventario.dto.CategoriaInventarioDto;
import com.baniterio.api.inventario.dto.CrearArticuloRequest;
import com.baniterio.api.inventario.dto.InventarioEventoResponse;
import com.baniterio.api.inventario.dto.InventarioResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sección Inventario: montar la hoja de listado, actualizar un artículo y mover
 * stock a un evento ("inventario de la fiesta"). La peña es la piloto
 * ({@code slug = "baniterio"}), igual que en {@code CuentaConsultaService}. Ver un
 * artículo lo puede cualquier usuario logueado; editarlo, darlo de alta/baja y
 * enviarlo a un evento exige el área {@link AreaProtegida#INVENTARIO}.
 */
@Service
public class InventarioService {

    private final ArticuloInventarioRepository articulos;
    private final ArticuloEventoRepository articulosEvento;
    private final LineaCompraEventoRepository lineasCompra;
    private final EventoRepository eventos;
    private final PenaPilotoService pena;
    private final ServicioPermisos permisos;

    public InventarioService(ArticuloInventarioRepository articulos,
                             ArticuloEventoRepository articulosEvento,
                             LineaCompraEventoRepository lineasCompra, EventoRepository eventos,
                             PenaPilotoService pena, ServicioPermisos permisos) {
        this.articulos = articulos;
        this.articulosEvento = articulosEvento;
        this.lineasCompra = lineasCompra;
        this.eventos = eventos;
        this.pena = pena;
        this.permisos = permisos;
    }

    private Long penaId() {
        return pena.id();
    }

    @Transactional(readOnly = true)
    public InventarioResponse ver(Long usuarioId) {
        boolean puedoEditar = permisos.puede(usuarioId, AreaProtegida.INVENTARIO);
        List<ArticuloInventario> filas =
                articulos.findByPenaIdOrderByCategoriaAscNombreAsc(penaId());

        List<CategoriaInventarioDto> categorias = Arrays.stream(CategoriaInventario.values())
                .map(cat -> new CategoriaInventarioDto(
                        cat.name(),
                        cat.etiqueta(),
                        cat.tamanos(),
                        filas.stream()
                                .filter(a -> a.getCategoria() == cat)
                                .filter(a -> a.getCantidad().signum() > 0)
                                .map(ArticuloDto::de)
                                .toList()))
                .toList();

        return new InventarioResponse(puedoEditar, categorias);
    }

    @Transactional
    public ArticuloDto crear(Long usuarioId, CrearArticuloRequest req) {
        permisos.exigir(usuarioId, AreaProtegida.INVENTARIO, SinPermisoInventarioException::new);
        if (!req.categoria().permiteTamano(req.tamano())) {
            throw new TamanoInventarioNoValidoException();
        }
        Long penaId = penaId();
        String nombre = req.nombre().trim();

        // Si ya hay una fila de ese producto (misma categoría, nombre y tamaño), se
        // ajusta su cantidad en vez de duplicarla. Así "añadir" un nombre que estaba
        // oculto por quedarse a 0 lo repone en la fila que ya existe.
        var existente = articulos.findByPenaIdAndCategoriaAndNombreIgnoreCaseAndTamano(
                penaId, req.categoria(), nombre, req.tamano());
        if (existente.isPresent()) {
            ArticuloInventario art = existente.get();
            art.setCantidad(req.cantidad());
            return ArticuloDto.de(articulos.save(art));
        }

        int orden = articulos.findByPenaIdOrderByCategoriaAscNombreAsc(penaId).stream()
                .filter(a -> a.getCategoria() == req.categoria())
                .mapToInt(ArticuloInventario::getOrden)
                .max().orElse(0) + 1;

        ArticuloInventario art = ArticuloInventario.builder()
                .pena(pena.entidad())
                .categoria(req.categoria())
                .nombre(nombre)
                .tamano(req.tamano())
                .cantidad(req.cantidad())
                .orden(orden)
                .build();
        return ArticuloDto.de(articulos.save(art));
    }

    @Transactional
    public ArticuloDto actualizar(Long usuarioId, Long articuloId, ActualizarArticuloRequest req) {
        permisos.exigir(usuarioId, AreaProtegida.INVENTARIO, SinPermisoInventarioException::new);
        ArticuloInventario art = articulos.findById(articuloId)
                .filter(a -> a.getPena().getId().equals(penaId()))
                .orElseThrow(ArticuloInventarioNoEncontradoException::new);

        if (!art.getCategoria().permiteTamano(req.tamano())) {
            throw new TamanoInventarioNoValidoException();
        }

        art.setNombre(req.nombre().trim());
        art.setTamano(req.tamano());
        art.setCantidad(req.cantidad());
        return ArticuloDto.de(articulos.save(art));
    }

    @Transactional
    public void borrar(Long usuarioId, Long articuloId) {
        permisos.exigir(usuarioId, AreaProtegida.INVENTARIO, SinPermisoInventarioException::new);
        ArticuloInventario art = articulos.findById(articuloId)
                .filter(a -> a.getPena().getId().equals(penaId()))
                .orElseThrow(ArticuloInventarioNoEncontradoException::new);
        articulos.delete(art);
    }

    // --- Inventario de la fiesta -------------------------------------------------

    private Evento eventoAbierto(Long eventoId) {
        return eventos.findById(eventoId)
                .filter(e -> e.getPena().getId().equals(penaId()) && !e.isOculto())
                .orElseThrow(EventoNoEncontradoException::new);
    }

    /** Mueve al evento toda la cantidad de la fila del inventario general (que queda a 0). */
    @Transactional
    public void enviar(Long usuarioId, Long articuloId, Long eventoId) {
        permisos.exigir(usuarioId, AreaProtegida.INVENTARIO, SinPermisoInventarioException::new);
        Evento evento = eventoAbierto(eventoId);
        ArticuloInventario art = articulos.findById(articuloId)
                .filter(a -> a.getPena().getId().equals(penaId()))
                .orElseThrow(ArticuloInventarioNoEncontradoException::new);
        if (art.getCantidad().signum() == 0) {
            throw new NadaQueEnviarException();
        }
        moverAlEvento(evento, art);
    }

    /** "Enviar todo": mueve al evento todas las filas de la categoría con cantidad &gt; 0. */
    @Transactional
    public void enviarCategoria(Long usuarioId, CategoriaInventario categoria, Long eventoId) {
        permisos.exigir(usuarioId, AreaProtegida.INVENTARIO, SinPermisoInventarioException::new);
        Evento evento = eventoAbierto(eventoId);
        articulos.findByPenaIdOrderByCategoriaAscNombreAsc(penaId()).stream()
                .filter(a -> a.getCategoria() == categoria && a.getCantidad().signum() > 0)
                .forEach(a -> moverAlEvento(evento, a));
    }

    private void moverAlEvento(Evento evento, ArticuloInventario art) {
        ArticuloEvento fila = articulosEvento
                .findByEventoIdAndCategoriaAndNombreAndTamano(
                        evento.getId(), art.getCategoria(), art.getNombre(), art.getTamano())
                .orElse(null);
        if (fila == null) {
            int orden = articulosEvento.findByEventoIdOrderByCategoriaAscNombreAsc(evento.getId())
                    .stream()
                    .filter(f -> f.getCategoria() == art.getCategoria())
                    .mapToInt(ArticuloEvento::getOrden)
                    .max().orElse(0) + 1;
            fila = ArticuloEvento.builder()
                    .evento(evento)
                    .articuloInventario(art)
                    .categoria(art.getCategoria())
                    .nombre(art.getNombre())
                    .tamano(art.getTamano())
                    .cantidad(art.getCantidad())
                    .cantidadComprada(BigDecimal.ZERO)
                    .orden(orden)
                    .build();
        } else {
            if (fila.getArticuloInventario() == null) {
                fila.setArticuloInventario(art);
            }
            fila.setCantidad(fila.getCantidad().add(art.getCantidad()));
        }
        articulosEvento.save(fila);
        art.setCantidad(BigDecimal.ZERO);
        articulos.save(art);
    }

    @Transactional(readOnly = true)
    public InventarioEventoResponse verEvento(Long usuarioId, Long eventoId) {
        eventoAbierto(eventoId); // valida existencia / pertenencia / no oculto → 404
        boolean puedoEditar = permisos.puede(usuarioId, AreaProtegida.INVENTARIO);
        List<ArticuloEvento> filas =
                articulosEvento.findByEventoIdOrderByCategoriaAscNombreAsc(eventoId);

        List<CategoriaEventoDto> categorias = Arrays.stream(CategoriaInventario.values())
                .map(cat -> new CategoriaEventoDto(
                        cat.name(),
                        cat.etiqueta(),
                        filas.stream()
                                .filter(f -> f.getCategoria() == cat)
                                .map(ArticuloDto::deEvento)
                                .toList()))
                .filter(c -> !c.articulos().isEmpty())
                .toList();

        return new InventarioEventoResponse(puedoEditar, categorias);
    }

    /** Devuelve la parte de stock de una fila del inventario de la fiesta al inventario general. */
    @Transactional
    public void devolver(Long usuarioId, Long eventoId, Long articuloEventoId) {
        permisos.exigir(usuarioId, AreaProtegida.INVENTARIO, SinPermisoInventarioException::new);
        ArticuloEvento fila = articulosEvento.findById(articuloEventoId)
                .filter(f -> f.getEvento().getId().equals(eventoId))
                .orElseThrow(ArticuloEventoNoEncontradoException::new);
        ArticuloInventario origen = fila.getArticuloInventario();
        if (origen != null && fila.getCantidad().signum() > 0) {
            origen.setCantidad(origen.getCantidad().add(fila.getCantidad()));
            articulos.save(origen);
        }
        fila.setCantidad(BigDecimal.ZERO);
        if (fila.getCantidadComprada().signum() == 0) {
            articulosEvento.delete(fila);
        } else {
            articulosEvento.save(fila);
        }
    }

    /** Devuelve la parte comprada de una fila del inventario de la fiesta a la lista de la compra. */
    @Transactional
    public void devolverALista(Long usuarioId, Long eventoId, Long articuloEventoId) {
        permisos.exigir(usuarioId, AreaProtegida.INVENTARIO, SinPermisoInventarioException::new);
        ArticuloEvento fila = articulosEvento.findById(articuloEventoId)
                .filter(f -> f.getEvento().getId().equals(eventoId))
                .orElseThrow(ArticuloEventoNoEncontradoException::new);
        if (fila.getCantidadComprada().signum() == 0) {
            throw new NadaQueDevolverException();
        }
        lineasCompra.findByEventoIdAndArticuloEventoId(eventoId, fila.getId()).ifPresent(l -> {
            l.setComprada(false);
            l.setArticuloEventoId(null);
            lineasCompra.save(l);
        });
        fila.setCantidadComprada(BigDecimal.ZERO);
        if (fila.getCantidad().signum() == 0) {
            articulosEvento.delete(fila);
        } else {
            articulosEvento.save(fila);
        }
    }
}
