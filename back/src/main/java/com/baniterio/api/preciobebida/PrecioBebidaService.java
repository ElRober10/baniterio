package com.baniterio.api.preciobebida;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.baniterio.api.admin.SinPermisoException;
import java.util.LinkedHashSet;

import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.compra.LineaCompraEvento;
import com.baniterio.api.compra.LineaCompraEventoRepository;
import com.baniterio.api.compra.OptimizadorPrecioBebida;
import com.baniterio.api.evento.BebidaNoEncontradaException;
import com.baniterio.api.evento.EventoNoEncontradoException;
import com.baniterio.api.evento.dto.BebidaRef;
import com.baniterio.api.identidad.Bebida;
import com.baniterio.api.identidad.BebidaRepository;
import com.baniterio.api.identidad.EstadoBebida;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.PenaPilotoService;
import com.baniterio.api.identidad.TipoBebida;
import com.baniterio.api.inventario.ArticuloInventario;
import com.baniterio.api.inventario.ArticuloInventarioRepository;
import com.baniterio.api.inventario.CategoriaInventario;
import com.baniterio.api.preciobebida.dto.AnadirTamanoRequest;
import com.baniterio.api.preciobebida.dto.CrearTiendaRequest;
import com.baniterio.api.preciobebida.dto.EventoPrecioBebidaDto;
import com.baniterio.api.preciobebida.dto.GrillaAlcoholResponse;
import com.baniterio.api.preciobebida.dto.GrillaArticuloResponse;
import com.baniterio.api.preciobebida.dto.GuardarPrecioArticuloRequest;
import com.baniterio.api.preciobebida.dto.GuardarPrecioRequest;
import com.baniterio.api.preciobebida.dto.GuardarTamanoArticuloRequest;
import com.baniterio.api.preciobebida.dto.GuardarProductoKiloRequest;
import com.baniterio.api.preciobebida.dto.PrecioArticuloCeldaDto;
import com.baniterio.api.preciobebida.dto.ProductoKiloDto;
import com.baniterio.api.preciobebida.dto.TamanoArticuloDto;
import com.baniterio.api.preciobebida.dto.PrecioCeldaDto;
import com.baniterio.api.preciobebida.dto.TiendaDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Precio bebidas: años → eventos, catálogo de tiendas de la peña y la rejilla
 * de precios de bebidas alcohólicas por evento. Lo ve cualquier miembro
 * logueado; solo un administrador de verdad ({@link ServicioPermisos#esAdministrador})
 * edita — no hace falta un área concedida.
 */
@Service
public class PrecioBebidaService {

    private static final List<String> TAMANOS_POR_DEFECTO = List.of("70 cl", "1 L");

    private final EventoRepository eventos;
    private final PenaPilotoService pena;
    private final TiendaRepository tiendas;
    private final TamanoPrecioBebidaEventoRepository tamanos;
    private final PrecioBebidaEventoRepository precios;
    private final BebidaRepository bebidas;
    private final PrecioArticuloEventoRepository precioArticulo;
    private final TamanoArticuloEventoRepository tamanosArticulo;
    private final TamanosArticulo tamanosHeredados;
    private final ProductoKiloEventoRepository productosKilo;
    private final ProductosPorKilo productosPorKilo;
    private final ArticuloInventarioRepository articulosInventario;
    private final LineaCompraEventoRepository lineasCompra;
    private final ServicioPermisos permisos;

    public PrecioBebidaService(EventoRepository eventos, PenaPilotoService pena, TiendaRepository tiendas,
            TamanoPrecioBebidaEventoRepository tamanos, PrecioBebidaEventoRepository precios,
            BebidaRepository bebidas, PrecioArticuloEventoRepository precioArticulo,
            TamanoArticuloEventoRepository tamanosArticulo, TamanosArticulo tamanosHeredados,
            ProductoKiloEventoRepository productosKilo, ProductosPorKilo productosPorKilo,
            ArticuloInventarioRepository articulosInventario, LineaCompraEventoRepository lineasCompra,
            ServicioPermisos permisos) {
        this.eventos = eventos;
        this.pena = pena;
        this.tiendas = tiendas;
        this.tamanos = tamanos;
        this.precios = precios;
        this.bebidas = bebidas;
        this.precioArticulo = precioArticulo;
        this.tamanosArticulo = tamanosArticulo;
        this.tamanosHeredados = tamanosHeredados;
        this.productosKilo = productosKilo;
        this.productosPorKilo = productosPorKilo;
        this.articulosInventario = articulosInventario;
        this.lineasCompra = lineasCompra;
        this.permisos = permisos;
    }

    private Long penaId() {
        return pena.id();
    }

    /** De más pequeño a más grande; los que no se reconocen van al final, en su orden original. */
    private List<String> ordenarTamanos(List<String> lista) {
        return lista.stream()
                .sorted(java.util.Comparator.comparing(
                        t -> OptimizadorPrecioBebida.parseCl(t).orElse(Integer.MAX_VALUE)))
                .toList();
    }

    private void exigirAdmin(Long usuarioId) {
        if (usuarioId == null || !permisos.esAdministrador(usuarioId)) {
            throw new SinPermisoException();
        }
    }

    private Evento eventoAbierto(Long eventoId) {
        return eventos.findById(eventoId)
                .filter(e -> e.getPena().getId().equals(penaId()) && !e.isOculto())
                .orElseThrow(EventoNoEncontradoException::new);
    }

    @Transactional(readOnly = true)
    public List<EventoPrecioBebidaDto> eventos() {
        return eventos.noOcultos(penaId()).stream()
                .map(e -> new EventoPrecioBebidaDto(e.getId(), e.getNombre(), e.getFecha(), e.getFechaFin()))
                .toList();
    }

    // --- Tiendas ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TiendaDto> tiendas() {
        return tiendas.findByPenaIdOrderByOrdenAscNombreAsc(penaId()).stream()
                .map(t -> new TiendaDto(t.getId(), t.getNombre()))
                .toList();
    }

    @Transactional
    public TiendaDto crearTienda(Long usuarioId, CrearTiendaRequest req) {
        exigirAdmin(usuarioId);
        String nombre = req.nombre().trim();
        if (tiendas.existsByPenaIdAndNombreIgnoreCase(penaId(), nombre)) {
            throw new TiendaDuplicadaException();
        }
        int orden = tiendas.findByPenaIdOrderByOrdenAscNombreAsc(penaId()).stream()
                .mapToInt(Tienda::getOrden).max().orElse(0) + 1;
        Tienda t = tiendas.save(Tienda.builder().pena(pena.entidad()).nombre(nombre).orden(orden).build());
        return new TiendaDto(t.getId(), t.getNombre());
    }

    // --- Rejilla de alcohol ------------------------------------------------------

    /** Materializa las pestañas de tamaño del evento (70 cl + 1 L) si aún no tiene ninguna. */
    private void materializarTamanos(Long eventoId) {
        if (tamanos.existsByEventoId(eventoId)) {
            return;
        }
        Evento e = eventoAbierto(eventoId);
        int orden = 1;
        for (String t : TAMANOS_POR_DEFECTO) {
            tamanos.save(TamanoPrecioBebidaEvento.builder().evento(e).tamano(t).orden(orden++).build());
        }
    }

    @Transactional
    public GrillaAlcoholResponse alcohol(Long usuarioId, Long eventoId) {
        eventoAbierto(eventoId);
        materializarTamanos(eventoId);

        List<TiendaDto> listaTiendas = tiendas();
        List<String> listaTamanos = ordenarTamanos(tamanos.findByEventoIdOrderByOrdenAscIdAsc(eventoId).stream()
                .map(TamanoPrecioBebidaEvento::getTamano).toList());
        List<BebidaRef> listaBebidas = bebidas.findByTipoAndEstadoOrderByNombreAsc(TipoBebida.ALCOHOL, EstadoBebida.ACEPTADA)
                .stream().map(b -> new BebidaRef(b.getId(), b.getNombre())).toList();
        List<PrecioCeldaDto> listaPrecios = precios.findByEventoId(eventoId).stream()
                .map(p -> new PrecioCeldaDto(p.getBebida().getId(), p.getTamano(), p.getTienda().getId(), p.getPrecio()))
                .toList();

        boolean puedoEditar = permisos.esAdministrador(usuarioId);
        return new GrillaAlcoholResponse(puedoEditar, listaTiendas, listaTamanos, listaBebidas, listaPrecios);
    }

    @Transactional
    public void guardarPrecio(Long usuarioId, Long eventoId, GuardarPrecioRequest req) {
        exigirAdmin(usuarioId);
        Evento evento = eventoAbierto(eventoId);
        Bebida bebida = bebidas.findById(req.bebidaId())
                .filter(b -> b.getTipo() == TipoBebida.ALCOHOL && b.getEstado() == EstadoBebida.ACEPTADA)
                .orElseThrow(BebidaNoEncontradaException::new);
        Tienda tienda = tiendas.findById(req.tiendaId())
                .filter(t -> t.getPena().getId().equals(penaId()))
                .orElseThrow(TiendaNoEncontradaException::new);
        if (!tamanos.existsByEventoIdAndTamanoIgnoreCase(eventoId, req.tamano())) {
            throw new TamanoPrecioBebidaNoValidoException();
        }

        var existente = precios.findByEventoIdAndBebidaIdAndTamanoAndTiendaId(
                eventoId, bebida.getId(), req.tamano(), tienda.getId());
        BigDecimal precio = req.precio();
        if (precio == null) {
            existente.ifPresent(precios::delete);
            return;
        }
        PrecioBebidaEvento fila = existente.orElseGet(() -> PrecioBebidaEvento.builder()
                .evento(evento).bebida(bebida).tamano(req.tamano()).tienda(tienda).build());
        fila.setPrecio(precio);
        precios.save(fila);
    }

    @Transactional
    public List<String> anadirTamano(Long usuarioId, Long eventoId, AnadirTamanoRequest req) {
        exigirAdmin(usuarioId);
        Evento evento = eventoAbierto(eventoId);
        materializarTamanos(eventoId);
        String tamano = req.tamano().trim();
        if (tamanos.existsByEventoIdAndTamanoIgnoreCase(eventoId, tamano)) {
            throw new TamanoPrecioBebidaDuplicadoException();
        }
        int orden = tamanos.findByEventoIdOrderByOrdenAscIdAsc(eventoId).stream()
                .mapToInt(TamanoPrecioBebidaEvento::getOrden).max().orElse(0) + 1;
        tamanos.save(TamanoPrecioBebidaEvento.builder().evento(evento).tamano(tamano).orden(orden).build());
        return ordenarTamanos(tamanos.findByEventoIdOrderByOrdenAscIdAsc(eventoId).stream()
                .map(TamanoPrecioBebidaEvento::getTamano).toList());
    }

    // --- Rejilla de artículos sin tamaños (refrescos, cerveza, limpieza, comida) ------------

    /** Un nombre de artículo de una sección, con su categoría real de inventario (para guardar/buscar precio). */
    private record NombreArticulo(String nombre, CategoriaInventario categoriaReal) {
    }

    /**
     * El catálogo de nombres de una sección. "CERVEZA" mezcla a propósito el
     * "Tinto de verano" (categoría real REFRESCOS en el inventario/lista de la
     * compra) porque en la ficha de bebida se elige junto a la cerveza como
     * alternativa al alcohol.
     */
    private List<NombreArticulo> nombresDe(Long eventoId, String seccion) {
        return switch (seccion) {
            case "REFRESCOS" -> bebidas.findByTipoAndEstadoOrderByNombreAsc(TipoBebida.REFRESCO, EstadoBebida.ACEPTADA)
                    .stream().map(b -> new NombreArticulo(b.getNombre(), CategoriaInventario.REFRESCOS)).toList();
            case "CERVEZA" -> List.of(
                    new NombreArticulo("Cerveza", CategoriaInventario.CERVEZA),
                    new NombreArticulo("Cerveza sin alcohol", CategoriaInventario.CERVEZA),
                    new NombreArticulo("Cerveza sin gluten", CategoriaInventario.CERVEZA),
                    new NombreArticulo("Tinto de verano", CategoriaInventario.REFRESCOS));
            case "LIMPIEZA" -> nombresCatalogoYLista(eventoId, CategoriaInventario.LIMPIEZA, true);
            case "COMIDA" -> nombresCatalogoYLista(eventoId, CategoriaInventario.COMIDA, false);
            default -> throw new CategoriaArticuloNoValidaException();
        };
    }

    /**
     * Nombres de una categoría de inventario: los del catálogo de la peña (si
     * {@code incluirInventario}) más los que ya haya en la lista de la compra de
     * este evento para esa categoría, sin duplicar, alfabético.
     */
    private List<NombreArticulo> nombresCatalogoYLista(Long eventoId, CategoriaInventario cat,
                                                        boolean incluirInventario) {
        LinkedHashSet<String> nombres = new LinkedHashSet<>();
        if (incluirInventario) {
            articulosInventario.findByPenaIdOrderByCategoriaAscNombreAsc(penaId()).stream()
                    .filter(a -> a.getCategoria() == cat).map(ArticuloInventario::getNombre).forEach(nombres::add);
        }
        lineasCompra.findByEventoIdOrderByCategoriaAscNombreAsc(eventoId).stream()
                .filter(l -> l.getCategoria() == cat).map(LineaCompraEvento::getNombre).forEach(nombres::add);
        return nombres.stream().sorted(String.CASE_INSENSITIVE_ORDER)
                .map(n -> new NombreArticulo(n, cat)).toList();
    }

    @Transactional(readOnly = true)
    public GrillaArticuloResponse articulos(Long usuarioId, Long eventoId, String seccion) {
        eventoAbierto(eventoId);
        List<NombreArticulo> catalogo = nombresDe(eventoId, seccion);
        List<String> nombresList = catalogo.stream().map(NombreArticulo::nombre).toList();
        List<PrecioArticuloCeldaDto> listaPrecios = nombresList.isEmpty() ? List.of()
                : precioArticulo.findByEventoIdAndNombreArticuloIn(eventoId, nombresList).stream()
                        .map(p -> new PrecioArticuloCeldaDto(p.getNombreArticulo(), p.getTienda().getId(), p.getPrecio()))
                        .toList();
        boolean puedoEditar = permisos.esAdministrador(usuarioId);
        List<TamanoArticuloDto> listaTamanos = tamanosHeredados.apuntados(eventoId).entrySet().stream()
                .filter(t -> nombresList.contains(t.getKey()))
                .map(t -> new TamanoArticuloDto(t.getKey(), t.getValue())).toList();
        Map<String, ProductosPorKilo.Kilo> kilos = productosPorKilo.apuntados(eventoId);
        List<ProductoKiloDto> listaKilo = !"COMIDA".equals(seccion) ? List.of()
                : nombresList.stream().filter(ProductosPorKilo.NOMBRES::contains)
                        .map(n -> new ProductoKiloDto(n,
                                kilos.containsKey(n) ? kilos.get(n).precioKilo() : null,
                                kilos.containsKey(n) ? kilos.get(n).pesoKg() : null))
                        .toList();
        return new GrillaArticuloResponse(puedoEditar, tiendas(), nombresList, listaPrecios, listaTamanos, listaKilo);
    }

    @Transactional
    public void guardarPrecioArticulo(Long usuarioId, Long eventoId, String seccion, GuardarPrecioArticuloRequest req) {
        exigirAdmin(usuarioId);
        Evento evento = eventoAbierto(eventoId);
        NombreArticulo articulo = nombresDe(eventoId, seccion).stream()
                .filter(n -> n.nombre().equals(req.nombreArticulo()))
                .findFirst().orElseThrow(NombreArticuloNoValidoException::new);
        Tienda tienda = tiendas.findById(req.tiendaId())
                .filter(t -> t.getPena().getId().equals(penaId()))
                .orElseThrow(TiendaNoEncontradaException::new);

        var existente = precioArticulo.findByEventoIdAndCategoriaAndNombreArticuloAndTiendaId(
                eventoId, articulo.categoriaReal(), articulo.nombre(), tienda.getId());
        BigDecimal precio = req.precio();
        if (precio == null) {
            existente.ifPresent(precioArticulo::delete);
            return;
        }
        PrecioArticuloEvento fila = existente.orElseGet(() -> PrecioArticuloEvento.builder()
                .evento(evento).categoria(articulo.categoriaReal()).nombreArticulo(articulo.nombre()).tienda(tienda).build());
        fila.setPrecio(precio);
        precioArticulo.save(fila);
    }

    /** Apunta el tamaño de botella de un refresco (1, 1,5 o 2 litros). Solo admin. */
    @Transactional
    public void guardarTamanoArticulo(Long usuarioId, Long eventoId, String seccion, GuardarTamanoArticuloRequest req) {
        exigirAdmin(usuarioId);
        Evento evento = eventoAbierto(eventoId);
        boolean admiteTamano = "REFRESCOS".equals(seccion)
                || ("CERVEZA".equals(seccion) && "Tinto de verano".equals(req.nombreArticulo()));
        if (!admiteTamano || TAMANOS_ARTICULO.stream().noneMatch(t -> t.compareTo(req.litros()) == 0)) {
            throw new TamanoArticuloNoValidoException();
        }
        NombreArticulo articulo = nombresDe(eventoId, seccion).stream()
                .filter(n -> n.nombre().equals(req.nombreArticulo()))
                .findFirst().orElseThrow(NombreArticuloNoValidoException::new);
        TamanoArticuloEvento fila = tamanosArticulo.findByEventoIdAndNombreArticulo(eventoId, articulo.nombre())
                .orElseGet(() -> TamanoArticuloEvento.builder().evento(evento).nombreArticulo(articulo.nombre()).build());
        fila.setLitros(req.litros());
        tamanosArticulo.save(fila);
    }

    /** Apunta precio por kilo y peso estimado de un embutido de Jamones Duriber. Solo admin. */
    @Transactional
    public void guardarProductoKilo(Long usuarioId, Long eventoId, GuardarProductoKiloRequest req) {
        exigirAdmin(usuarioId);
        Evento evento = eventoAbierto(eventoId);
        if (!ProductosPorKilo.NOMBRES.contains(req.nombreArticulo())) {
            throw new NombreArticuloNoValidoException();
        }
        ProductoKiloEvento fila = productosKilo.findByEventoIdAndNombreArticulo(eventoId, req.nombreArticulo())
                .orElseGet(() -> ProductoKiloEvento.builder().evento(evento).nombreArticulo(req.nombreArticulo()).build());
        fila.setPrecioKilo(req.precioKilo());
        fila.setPesoKg(req.pesoKg());
        productosKilo.save(fila);
    }

    private static final List<BigDecimal> TAMANOS_ARTICULO =
            List.of(new BigDecimal("1"), new BigDecimal("1.5"), new BigDecimal("2"));
}
