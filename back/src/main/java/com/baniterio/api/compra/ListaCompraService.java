package com.baniterio.api.compra;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.compra.CalculadoraListaCompra.DatosEvento;
import com.baniterio.api.compra.CalculadoraListaCompra.LineaCalculada;
import com.baniterio.api.compra.CalculadoraListaCompra.PersonaCompra;
import com.baniterio.api.compra.dto.AjustarLineaRequest;
import com.baniterio.api.compra.dto.AjustarReglaRequest;
import com.baniterio.api.compra.dto.CategoriaListaCompraDto;
import com.baniterio.api.compra.dto.CrearReglaRequest;
import com.baniterio.api.compra.dto.EventoListaCompraDto;
import com.baniterio.api.compra.dto.InfoBebidaDto;
import com.baniterio.api.compra.dto.LineaCompraDto;
import com.baniterio.api.compra.dto.OpcionTamanoDto;
import com.baniterio.api.compra.dto.ListaCompraAdminResponse;
import com.baniterio.api.compra.dto.ListaCompraResponse;
import com.baniterio.api.compra.dto.ReglaCompraEventoDto;
import com.baniterio.api.evento.EventoNoEncontradoException;
import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.AsistenciaEvento;
import com.baniterio.api.identidad.AsistenciaEventoRepository;
import com.baniterio.api.identidad.EstadoAsistencia;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.FichaBebida;
import com.baniterio.api.identidad.FichaBebidaRepository;
import com.baniterio.api.identidad.MovimientoCuentaRepository;
import com.baniterio.api.identidad.PenaPilotoService;
import com.baniterio.api.inventario.ArticuloEvento;
import com.baniterio.api.inventario.ArticuloEventoRepository;
import com.baniterio.api.inventario.CategoriaInventario;
import com.baniterio.api.inventario.SinPermisoInventarioException;
import com.baniterio.api.compra.OptimizadorPrecioBebida.ItemCompra;
import com.baniterio.api.compra.OptimizadorPrecioBebida.OpcionPrecio;
import com.baniterio.api.preciobebida.PrecioArticuloEvento;
import com.baniterio.api.preciobebida.PrecioArticuloEventoRepository;
import com.baniterio.api.preciobebida.PrecioBebidaEvento;
import com.baniterio.api.preciobebida.ProductosPorKilo;
import com.baniterio.api.preciobebida.TamanosArticulo;
import com.baniterio.api.preciobebida.PrecioBebidaEventoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lista de la compra por evento: montar la lista calculada (cualquier peñista) y
 * el editor de "Cantidades para eventos" (área INVENTARIO). Las reglas de cada
 * evento se materializan copiando la plantilla global la primera vez que se pide
 * la lista. Bloque 1: cálculo bruto con redondeo hacia arriba. Bloque 2: se resta
 * lo que ya está en el inventario de la fiesta de ESE evento ({@link #stockCubierto}
 * — el inventario general de la peña no cuenta aquí, solo lo enviado a este evento)
 * — se recalcula cada vez que se abre la lista, así que cualquier "enviar"/"devolver"
 * en el inventario de la fiesta se refleja solo. Si la cantidad queda a 0 la línea
 * no se muestra (salvo el placeholder de "necesita ficha de bebida"). Una vez
 * marcada {@code comprada}, la línea queda congelada y ya no se recalcula (ni al
 * bajar ni al subir el stock disponible); lo mismo pasa con toda la lista mientras
 * esté {@code listaCompraBloqueada}.
 */
@Service
public class ListaCompraService {

    private final ReglaCompraRepository plantilla;
    private final ReglaCompraEventoRepository reglasEvento;
    private final LineaCompraEventoRepository lineas;
    private final ArticuloEventoRepository articulosEvento;
    private final EventoRepository eventos;
    private final AsistenciaEventoRepository asistencias;
    private final FichaBebidaRepository fichas;
    private final PrecioBebidaEventoRepository preciosBebida;
    private final PrecioArticuloEventoRepository preciosArticulo;
    private final TamanosArticulo tamanosArticulo;
    private final ProductosPorKilo productosPorKilo;
    private final MovimientoCuentaRepository movimientosCuenta;
    private final PenaPilotoService pena;
    private final ServicioPermisos permisos;

    public ListaCompraService(ReglaCompraRepository plantilla, ReglaCompraEventoRepository reglasEvento,
                              LineaCompraEventoRepository lineas, ArticuloEventoRepository articulosEvento,
                              EventoRepository eventos, AsistenciaEventoRepository asistencias,
                              FichaBebidaRepository fichas, PrecioBebidaEventoRepository preciosBebida,
                              PrecioArticuloEventoRepository preciosArticulo, TamanosArticulo tamanosArticulo,
                              ProductosPorKilo productosPorKilo, MovimientoCuentaRepository movimientosCuenta,
                              PenaPilotoService pena, ServicioPermisos permisos) {
        this.plantilla = plantilla;
        this.reglasEvento = reglasEvento;
        this.lineas = lineas;
        this.articulosEvento = articulosEvento;
        this.eventos = eventos;
        this.asistencias = asistencias;
        this.fichas = fichas;
        this.preciosBebida = preciosBebida;
        this.preciosArticulo = preciosArticulo;
        this.tamanosArticulo = tamanosArticulo;
        this.productosPorKilo = productosPorKilo;
        this.movimientosCuenta = movimientosCuenta;
        this.pena = pena;
        this.permisos = permisos;
    }

    /** Una línea del cálculo con su clave de identidad. */
    private record LineaCalc(CategoriaInventario categoria, String nombre, String tamano, String tienda,
                             BigDecimal precioUnitario, BigDecimal cantidad, int orden, boolean dinamica,
                             boolean necesitaFicha, boolean ajustada, String detalle) {
        LineaCalc(CategoriaInventario categoria, String nombre, String tamano, String tienda,
                  BigDecimal precioUnitario, BigDecimal cantidad, int orden, boolean dinamica,
                  boolean necesitaFicha, boolean ajustada) {
            this(categoria, nombre, tamano, tienda, precioUnitario, cantidad, orden, dinamica,
                    necesitaFicha, ajustada, null);
        }
    }

    private Long penaId() {
        return pena.id();
    }

    private Evento eventoAbierto(Long eventoId) {
        return eventos.findById(eventoId)
                .filter(e -> e.getPena().getId().equals(penaId()) && !e.isOculto())
                .orElseThrow(EventoNoEncontradoException::new);
    }

    private void exigirArea(Long usuarioId) {
        permisos.exigir(usuarioId, AreaProtegida.INVENTARIO, SinPermisoInventarioException::new);
    }

    /** Copia la plantilla al evento si aún no tiene reglas propias. */
    @Transactional
    void materializar(Evento evento) {
        if (reglasEvento.existsByEventoId(evento.getId())) {
            return;
        }
        for (ReglaCompra r : plantilla.findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(penaId())) {
            reglasEvento.save(ReglaCompraEvento.builder()
                    .evento(evento).categoria(r.getCategoria()).nombre(r.getNombre()).tamano(r.getTamano())
                    .tipoFormula(r.getTipoFormula()).factor(r.getFactor()).porCada(r.getPorCada())
                    .orden(r.getOrden()).origen(OrigenReglaCompra.PLANTILLA).cantidadAjustada(null)
                    .activa(true).build());
        }
    }

    private DatosEvento datosDe(Evento e) {
        boolean llevaFicha = e.getCuenta().isLlevaFichaBebida();
        int diasFiesta = e.getFechaFin() != null
                ? (int) ChronoUnit.DAYS.between(e.getFecha(), e.getFechaFin()) + 1 : 1;
        boolean dosDiasFicha = llevaFicha && diasFiesta == 2;

        List<AsistenciaEvento> apuntadas = asistencias.findByEventoIdAndEstadoIn(
                e.getId(), List.of(EstadoAsistencia.APUNTADO));
        Map<Long, FichaBebida> fichaPorAsistencia = fichas.findByEventoId(e.getId()).stream()
                .collect(Collectors.toMap(FichaBebida::getAsistenciaId, Function.identity()));

        List<PersonaCompra> personas = new ArrayList<>();
        for (AsistenciaEvento a : apuntadas) {
            FichaBebida f = fichaPorAsistencia.get(a.getId());
            int diasQueVa;
            if (dosDiasFicha && f != null) {
                diasQueVa = (f.isAsisteDia1() ? 1 : 0) + (f.isAsisteDia2() ? 1 : 0);
            } else {
                diasQueVa = diasFiesta;
            }
            personas.add(new PersonaCompra(
                    diasQueVa,
                    f != null,
                    f != null && f.getAlcohol() != null ? f.getAlcohol().getNombre() : null,
                    f != null && f.getRefresco() != null ? f.getRefresco().getNombre() : null,
                    f != null ? f.getAlternativa() : null,
                    f != null ? f.getCervezaEspecial() : null));
        }
        return new DatosEvento(apuntadas.size(), diasFiesta, llevaFicha, personas);
    }

    @Transactional
    public ListaCompraResponse verLista(Long usuarioId, Long eventoId) {
        Evento e = eventoAbierto(eventoId);
        materializar(e);
        DatosEvento datos = datosDe(e);
        if (!e.isListaCompraBloqueada()) {
            sincronizar(e, datos);
        }
        boolean puedoEditar = permisos.puede(usuarioId, AreaProtegida.INVENTARIO);
        Map<String, BigDecimal> personasPorMarca = personasPorNombre(datos, PersonaCompra::alcohol);
        Map<String, BigDecimal> personasPorRefresco = personasPorNombre(datos, PersonaCompra::refresco);
        StockCubierto stockFiesta = stockCubierto(eventoId);
        Map<String, List<OpcionPrecio>> preciosAlcohol = preciosPorMarca(eventoId);

        // "Para alternar" agrupa lo que se bebe en vez del alcohol: la cerveza (y las especiales)
        // y el tinto de verano, que en el inventario y en los precios sigue siendo un refresco.
        // Es solo una agrupación para mostrar: cada línea conserva su categoría real.
        Map<String, List<LineaCompraDto>> porSeccion = new LinkedHashMap<>();
        Map<String, String> etiquetas = new LinkedHashMap<>();
        for (CategoriaInventario cat : CategoriaInventario.values()) {
            String clave = cat == CategoriaInventario.CERVEZA ? SECCION_PARA_ALTERNAR : cat.name();
            porSeccion.put(clave, new ArrayList<>());
            etiquetas.put(clave, cat == CategoriaInventario.CERVEZA ? "Para alternar" : cat.etiqueta());
        }
        for (LineaCompraEvento l : lineas.findByEventoIdOrderByCategoriaAscNombreAsc(eventoId)) {
            // Si no hace falta comprar nada (cantidad 0), no se muestra; salvo la línea
            // placeholder de "necesita ficha de bebida", que siempre se ve a 0.
            if (l.getCantidad().signum() == 0 && !l.isNecesitaFicha()) {
                continue;
            }
            porSeccion.get(seccionDe(l)).add(new LineaCompraDto(
                    l.getId(), l.getNombre(), l.getTamano(), l.getTienda(), l.getPrecioUnitario(),
                    l.getCantidad(), BigDecimal.ZERO,
                    l.isAjustada(), l.isDinamica(), l.isNecesitaFicha(), l.isComprada(), l.getDetalle(),
                    infoDe(l, personasPorMarca, personasPorRefresco, stockFiesta, preciosAlcohol)));
        }

        List<CategoriaListaCompraDto> categorias = new ArrayList<>();
        porSeccion.forEach((clave, ls) -> {
            if (!ls.isEmpty()) {
                categorias.add(new CategoriaListaCompraDto(clave, etiquetas.get(clave), ls));
            }
        });
        return new ListaCompraResponse(puedoEditar, datos.llevaFicha(), e.isListaCompraBloqueada(),
                datos.apuntados(), datos.diasFiesta(), categorias,
                movimientosCuenta.sumImporteAnio(e.getCuenta().getId(), e.getCuenta().getAnioActual()));
    }

    /** Personas que beben cada marca (de alcohol o de refresco), cada una como fracción de los días de la fiesta. */
    private static Map<String, BigDecimal> personasPorNombre(DatosEvento datos,
                                                             java.util.function.Function<PersonaCompra, String> marca) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        if (datos.diasFiesta() <= 0) {
            return out;
        }
        for (PersonaCompra p : datos.personas()) {
            if (p.tieneFicha() && marca.apply(p) != null) {
                out.merge(marca.apply(p), BigDecimal.valueOf(p.diasQueVa())
                        .divide(BigDecimal.valueOf(datos.diasFiesta()), 2, java.math.RoundingMode.HALF_UP),
                        BigDecimal::add);
            }
        }
        return out;
    }

    /**
     * Las líneas de bebida elegida en la ficha (alcohol y refrescos) llevan quién la bebe y el stock
     * para ajustar la cantidad; solo el alcohol lleva además los tamaños de botella con precio.
     */
    private static InfoBebidaDto infoDe(LineaCompraEvento l, Map<String, BigDecimal> personasPorMarca,
                                        Map<String, BigDecimal> personasPorRefresco, StockCubierto stock,
                                        Map<String, List<OpcionPrecio>> precios) {
        boolean alcohol = l.getCategoria() == CategoriaInventario.ALCOHOL;
        if (!l.isDinamica() || (!alcohol && l.getCategoria() != CategoriaInventario.REFRESCOS)) {
            return null;
        }
        return new InfoBebidaDto(
                (alcohol ? personasPorMarca : personasPorRefresco)
                        .getOrDefault(l.getNombre(), BigDecimal.ZERO).stripTrailingZeros(),
                stock.porNombre().getOrDefault(claveNombre(l.getCategoria(), l.getNombre()), BigDecimal.ZERO)
                        .stripTrailingZeros(),
                alcohol ? opcionesPorTamano(precios.get(l.getNombre())) : null);
    }

    /** Por cada tamaño con precio, la tienda más barata y el precio por litro; de menor a mayor tamaño. */
    private static List<OpcionTamanoDto> opcionesPorTamano(List<OpcionPrecio> opciones) {
        if (opciones == null) {
            return List.of();
        }
        Map<String, OpcionPrecio> barata = new LinkedHashMap<>();
        for (OpcionPrecio o : opciones) {
            barata.merge(o.tamano(), o, (a, b) -> b.precio().compareTo(a.precio()) < 0 ? b : a);
        }
        return barata.values().stream()
                .sorted(java.util.Comparator.comparingInt(OpcionPrecio::cl))
                .map(o -> new OpcionTamanoDto(o.tamano(), o.tienda(), o.precio(),
                        o.precio().multiply(BigDecimal.valueOf(100))
                                .divide(BigDecimal.valueOf(o.cl()), 2, java.math.RoundingMode.HALF_UP)))
                .toList();
    }

    private static final String SECCION_PARA_ALTERNAR = "PARA_ALTERNAR";

    private static String seccionDe(LineaCompraEvento l) {
        boolean esTinto = l.getCategoria() == CategoriaInventario.REFRESCOS && "Tinto de verano".equals(l.getNombre());
        return l.getCategoria() == CategoriaInventario.CERVEZA || esTinto
                ? SECCION_PARA_ALTERNAR : l.getCategoria().name();
    }

    /**
     * Stock ya cubierto: por clave exacta, por categoría entera, por marca (sin
     * tamaño) y por marca en cl (solo ALCOHOL, cada botella convertida a cl).
     */
    private record StockCubierto(Map<String, BigDecimal> porClave, Map<CategoriaInventario, BigDecimal> porCategoria,
                                  Map<String, BigDecimal> porNombre, Map<String, Integer> clPorMarcaAlcohol) {
    }

    private static final BigDecimal LITRO = BigDecimal.ONE;
    private static final BigDecimal SETENTA_CL = new BigDecimal("0.7");
    private static final int CL_POR_UNIDAD = 70;

    private static String claveNombre(CategoriaInventario cat, String nombre) {
        return cat.name() + " " + nombre;
    }

    /**
     * Stock ya cubierto de un producto: solo lo que ya está en el inventario de la
     * fiesta (enviado + comprado para ella). El inventario general de la peña NO
     * cuenta aquí: solo lo que se ha enviado a ESTE evento reduce su lista.
     * {@code CERVEZA} es la categoría rara: la plantilla tiene una única línea
     * genérica "Cerveza" pero el inventario guarda una fila por marca (Mahou
     * Clásica, Coronita...), así que ahí se descuenta por categoría entera en vez
     * de por nombre exacto. {@code porNombre} suma por marca sin mirar el tamaño
     * (botella de 1L o de 70cl): lo usa la regla de la botella del alcohol.
     */
    private StockCubierto stockCubierto(Long eventoId) {
        Map<String, BigDecimal> porClave = new LinkedHashMap<>();
        Map<CategoriaInventario, BigDecimal> porCategoria = new LinkedHashMap<>();
        Map<String, BigDecimal> porNombre = new LinkedHashMap<>();
        Map<String, Integer> clPorMarcaAlcohol = new LinkedHashMap<>();
        for (ArticuloEvento a : articulosEvento.findByEventoIdOrderByCategoriaAscNombreAsc(eventoId)) {
            BigDecimal total = a.getCantidad().add(a.getCantidadComprada());
            porClave.merge(clave(a.getCategoria(), a.getNombre(), a.getTamano()), total, BigDecimal::add);
            porCategoria.merge(a.getCategoria(), total, BigDecimal::add);
            porNombre.merge(claveNombre(a.getCategoria(), a.getNombre()), total, BigDecimal::add);
            if (a.getCategoria() == CategoriaInventario.ALCOHOL) {
                // Botellas abiertas cuentan a fracción (0,8 de 1 L = 80 cl): nada de truncar a enteros.
                OptimizadorPrecioBebida.parseCl(a.getTamano()).ifPresent(cl -> clPorMarcaAlcohol.merge(
                        a.getNombre(), BigDecimal.valueOf(cl).multiply(total)
                                .setScale(0, java.math.RoundingMode.HALF_UP).intValue(), Integer::sum));
            }
        }
        return new StockCubierto(porClave, porCategoria, porNombre, clPorMarcaAlcohol);
    }

    /** Precios de la rejilla de alcohol del evento, agrupados por marca. */
    private Map<String, List<OpcionPrecio>> preciosPorMarca(Long eventoId) {
        Map<String, List<OpcionPrecio>> out = new LinkedHashMap<>();
        for (PrecioBebidaEvento p : preciosBebida.findByEventoId(eventoId)) {
            OptimizadorPrecioBebida.parseCl(p.getTamano()).ifPresent(cl -> out
                    .computeIfAbsent(p.getBebida().getNombre(), k -> new ArrayList<>())
                    .add(new OpcionPrecio(p.getTamano(), cl, p.getTienda().getNombre(), p.getPrecio())));
        }
        return out;
    }

    /**
     * Precios de artículos sin tamaños (refrescos, cerveza/tinto de verano,
     * limpieza, comida) del evento, agrupados por categoría+nombre.
     */
    private Map<String, List<PrecioArticuloEvento>> preciosPorArticulo(Long eventoId) {
        Map<String, List<PrecioArticuloEvento>> out = new LinkedHashMap<>();
        for (PrecioArticuloEvento p : preciosArticulo.findByEventoId(eventoId)) {
            out.computeIfAbsent(claveNombre(p.getCategoria(), p.getNombreArticulo()).toLowerCase(), k -> new ArrayList<>()).add(p);
        }
        return out;
    }

    /**
     * La opción (tienda + pack) más barata para cubrir {@code necesarias} unidades comprando
     * packs enteros: coste = packs × precio del pack. A igual coste, la que deja menos sobrante.
     */
    private PrecioArticuloEvento mejorPack(List<PrecioArticuloEvento> opciones, BigDecimal necesarias) {
        java.util.function.ToLongFunction<PrecioArticuloEvento> packs = o -> necesarias
                .divide(BigDecimal.valueOf(o.getCantidad()), 0, java.math.RoundingMode.CEILING).longValue();
        return opciones.stream().min(java.util.Comparator
                .comparing((PrecioArticuloEvento o) -> o.getPrecio().multiply(BigDecimal.valueOf(packs.applyAsLong(o))))
                .thenComparingLong(o -> packs.applyAsLong(o) * o.getCantidad())).orElseThrow();
    }

    /**
     * Todas las líneas que el cálculo produce ahora mismo para el evento (bloque 2:
     * resta el stock ya cubierto). Lo que sobra tras restar se redondea siempre
     * hacia arriba a una unidad entera (no se compran fracciones de rollo, litro o
     * botella); si no sobra nada, la línea no se compra (cantidad 0).
     */
    private List<LineaCalc> calcular(Long eventoId, DatosEvento datos) {
        List<ReglaCompraEvento> activas = reglasEvento
                .findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(eventoId).stream()
                .filter(ReglaCompraEvento::isActiva).toList();
        StockCubierto stock = stockCubierto(eventoId);
        Map<String, List<OpcionPrecio>> precios = preciosPorMarca(eventoId);
        Map<String, List<PrecioArticuloEvento>> preciosArt = preciosPorArticulo(eventoId);
        Map<String, BigDecimal> litrosBotella = tamanosArticulo.apuntados(eventoId);
        Map<String, ProductosPorKilo.Kilo> porKilo = productosPorKilo.apuntados(eventoId);
        List<LineaCalc> out = new ArrayList<>();
        for (ReglaCompraEvento r : activas) {
            for (LineaCalculada lc : CalculadoraListaCompra.lineasDe(r, datos)) {
                boolean ajustada = !r.getTipoFormula().esDinamica() && r.getCantidadAjustada() != null;
                if (ajustada) {
                    out.add(new LineaCalc(r.getCategoria(), lc.nombre(), lc.tamano(), null, null,
                            r.getCantidadAjustada(), r.getOrden(), lc.dinamica(), lc.necesitaFicha(), true));
                } else if (r.getCategoria() == CategoriaInventario.ALCOHOL && lc.dinamica()) {
                    out.addAll(lineasAlcohol(r, lc, stock, precios.get(lc.nombre())));
                } else {
                    BigDecimal cubierto = r.getCategoria() == CategoriaInventario.CERVEZA
                            && r.getTipoFormula() != TipoFormulaCompra.CERVEZA_ESPECIAL_SELECCIONADA
                            ? stock.porCategoria().getOrDefault(CategoriaInventario.CERVEZA, BigDecimal.ZERO)
                            : stock.porClave().getOrDefault(
                                    clave(r.getCategoria(), lc.nombre(), lc.tamano()), BigDecimal.ZERO);
                    BigDecimal bruto = lc.bruto();
                    boolean esBotellaDeLitros = (r.getTipoFormula() == TipoFormulaCompra.REFRESCO_SELECCIONADO
                            || r.getTipoFormula() == TipoFormulaCompra.TINTO_ALTERNATIVA) && "botella".equals(lc.tamano());
                    if (esBotellaDeLitros) {
                        // El rango son botellas de 2 L por peñista y día: se pasa a litros y se
                        // pide el número entero de botellas del tamaño que hay en la rejilla de precios.
                        BigDecimal litros = bruto.multiply(TamanosArticulo.POR_DEFECTO);
                        bruto = CalculadoraListaCompra.ceil(
                                litros.divide(tamanosArticulo.de(litrosBotella, lc.nombre()), 0, java.math.RoundingMode.CEILING));
                    }
                    BigDecimal restante = bruto.subtract(cubierto).max(BigDecimal.ZERO);
                    BigDecimal cantidad = restante.signum() > 0 ? CalculadoraListaCompra.ceil(restante) : BigDecimal.ZERO;
                    String tienda = null;
                    BigDecimal precioUnitario = null;
                    String detalle = null;
                    if (cantidad.signum() > 0) {
                        List<PrecioArticuloEvento> opcionesArt =
                                preciosArt.get(claveNombre(r.getCategoria(), lc.nombre()).toLowerCase());
                        ProductosPorKilo.Kilo kilo = r.getCategoria() == CategoriaInventario.COMIDA
                                ? porKilo.get(lc.nombre()) : null;
                        if (kilo != null) {
                            // Embutido de Jamones Duriber: no se comparan tiendas, precio = kilo x peso estimado.
                            tienda = ProductosPorKilo.PROVEEDOR;
                            precioUnitario = kilo.precioPieza();
                        } else if (opcionesArt != null && !opcionesArt.isEmpty()) {
                            // Cada tienda puede vender un pack distinto: se elige la que sale más barata
                            // comprando packs enteros hasta cubrir lo que falta.
                            PrecioArticuloEvento mejor = mejorPack(opcionesArt, cantidad);
                            int pack = mejor.getCantidad();
                            int packs = cantidad.divide(BigDecimal.valueOf(pack), 0, java.math.RoundingMode.CEILING).intValue();
                            tienda = mejor.getTienda().getNombre();
                            precioUnitario = mejor.getPrecio().divide(BigDecimal.valueOf(pack), 4, java.math.RoundingMode.HALF_UP);
                            cantidad = BigDecimal.valueOf((long) packs * pack);
                            if (pack > 1) {
                                detalle = packs + " × pack de " + pack;
                            }
                        }
                    }
                    out.add(new LineaCalc(r.getCategoria(), lc.nombre(), lc.tamano(), tienda, precioUnitario,
                            cantidad, r.getOrden(), lc.dinamica(), lc.necesitaFicha(), false, detalle));
                }
            }
        }
        return out;
    }

    /**
     * Una marca de alcohol: cuántas botellas de qué tamaño y en qué tienda
     * comprar. Con precios en la rejilla, la combinación más barata que cubra lo
     * que falta (en cl, restado el stock de la fiesta ya convertido a cl); sin
     * ningún precio metido para esa marca, el aviso sin tienda de siempre (n=1 →
     * botella de 1L o 70cl según lo que ya haya; n>1 → n botellas de 70cl).
     */
    private List<LineaCalc> lineasAlcohol(ReglaCompraEvento r, LineaCalculada lc, StockCubierto stock,
                                          List<OpcionPrecio> opciones) {
        if (opciones != null && !opciones.isEmpty()) {
            int stockCl = stock.clPorMarcaAlcohol().getOrDefault(lc.nombre(), 0);
            // Una sola botella de referencia (una persona) equivale a 1 L, no a 70 cl: como en el
            // aviso sin precios, hace falta al menos un litro entre lo que hay y lo que se compre.
            int targetCl = "1 L".equals(lc.tamano()) ? 100
                    : lc.bruto().multiply(BigDecimal.valueOf(CL_POR_UNIDAD)).intValue();
            int restanteCl = Math.max(0, targetCl - stockCl);
            var combinacion = OptimizadorPrecioBebida.combinacionMasBarata(restanteCl, opciones);
            if (combinacion.isPresent()) {
                List<LineaCalc> out = new ArrayList<>();
                for (ItemCompra item : combinacion.get()) {
                    out.add(new LineaCalc(r.getCategoria(), lc.nombre(), item.tamano(), item.tienda(),
                            item.precioUnitario(), BigDecimal.valueOf(item.cantidad()), r.getOrden(), true, false, false));
                }
                return out;
            }
        }

        // Fallback sin precios: mismo criterio de siempre, sin tienda.
        String tamano = lc.tamano();
        BigDecimal cantidad;
        if ("1 L".equals(tamano)) {
            // Una sola persona bebiendo esa marca: hace falta al menos 1 L en total
            // entre lo que ya hay y lo que se compre. Si con una botella de 70cl ya
            // se llega al litro, se compra esa (más barata); si no, la de 1 L.
            BigDecimal stockMarca = stock.porNombre()
                    .getOrDefault(claveNombre(r.getCategoria(), lc.nombre()), BigDecimal.ZERO);
            if (stockMarca.compareTo(LITRO) >= 0) {
                cantidad = BigDecimal.ZERO;
            } else if (stockMarca.add(SETENTA_CL).compareTo(LITRO) >= 0) {
                tamano = "70 cl";
                cantidad = BigDecimal.ONE;
            } else {
                cantidad = BigDecimal.ONE;
            }
        } else {
            BigDecimal cubierto = stock.porClave()
                    .getOrDefault(clave(r.getCategoria(), lc.nombre(), tamano), BigDecimal.ZERO);
            BigDecimal restante = lc.bruto().subtract(cubierto).max(BigDecimal.ZERO);
            cantidad = restante.signum() > 0 ? CalculadoraListaCompra.ceil(restante) : BigDecimal.ZERO;
        }
        return List.of(new LineaCalc(r.getCategoria(), lc.nombre(), tamano, null, null, cantidad,
                r.getOrden(), true, false, false));
    }

    /** Alinea linea_compra_evento con el cálculo actual. No toca las líneas compradas. */
    private void sincronizar(Evento e, DatosEvento datos) {
        List<LineaCalc> calculadas = calcular(e.getId(), datos);
        Map<String, LineaCompraEvento> existentes = new LinkedHashMap<>();
        for (LineaCompraEvento l : lineas.findByEventoIdOrderByCategoriaAscNombreAsc(e.getId())) {
            existentes.put(claveLinea(l.getCategoria(), l.getNombre(), l.getTamano(), l.getTienda()), l);
        }
        Set<String> vistas = new HashSet<>();
        for (LineaCalc c : calculadas) {
            String k = claveLinea(c.categoria(), c.nombre(), c.tamano(), c.tienda());
            vistas.add(k);
            LineaCompraEvento fila = existentes.get(k);
            if (fila == null) {
                lineas.save(LineaCompraEvento.builder()
                        .evento(e).categoria(c.categoria()).nombre(c.nombre()).tamano(c.tamano()).tienda(c.tienda())
                        .precioUnitario(c.precioUnitario()).detalle(c.detalle())
                        .cantidad(c.cantidad()).orden(c.orden()).dinamica(c.dinamica())
                        .necesitaFicha(c.necesitaFicha()).ajustada(c.ajustada())
                        .comprada(false).build());
            } else if (!fila.isComprada()) {
                fila.setCantidad(c.cantidad());
                fila.setOrden(c.orden());
                fila.setDinamica(c.dinamica());
                fila.setNecesitaFicha(c.necesitaFicha());
                fila.setAjustada(c.ajustada());
                fila.setPrecioUnitario(c.precioUnitario());
                fila.setDetalle(c.detalle());
                lineas.save(fila);
            }
        }
        for (Map.Entry<String, LineaCompraEvento> en : existentes.entrySet()) {
            if (!vistas.contains(en.getKey()) && !en.getValue().isComprada()) {
                lineas.delete(en.getValue());
            }
        }
    }

    private static String clave(CategoriaInventario cat, String nombre, String tamano) {
        return cat.name() + "\u0000" + nombre + "\u0000" + tamano;
    }

    /** Como {@link #clave} pero con la tienda: dos botellas del mismo tamano en tiendas distintas son lineas distintas. */
    private static String claveLinea(CategoriaInventario cat, String nombre, String tamano, String tienda) {
        return clave(cat, nombre, tamano) + "\u0000" + (tienda == null ? "" : tienda);
    }

    /** Marca una linea como comprada: su cantidad pasa al inventario de la fiesta y queda congelada. */
    @Transactional
    public void marcarComprada(Long usuarioId, Long eventoId, Long lineaId) {
        exigirArea(usuarioId);
        Evento e = eventoAbierto(eventoId);
        LineaCompraEvento linea = lineas.findByIdAndEventoId(lineaId, eventoId)
                .orElseThrow(LineaCompraNoEncontradaException::new);
        if (linea.isComprada()) {
            return;
        }
        ArticuloEvento fila = articulosEvento
                .findByEventoIdAndCategoriaAndNombreAndTamano(
                        eventoId, linea.getCategoria(), linea.getNombre(), linea.getTamano())
                .orElse(null);
        if (fila == null) {
            int orden = articulosEvento.findByEventoIdOrderByCategoriaAscNombreAsc(eventoId).stream()
                    .filter(f -> f.getCategoria() == linea.getCategoria())
                    .mapToInt(ArticuloEvento::getOrden).max().orElse(0) + 1;
            fila = ArticuloEvento.builder()
                    .evento(e).articuloInventario(null).categoria(linea.getCategoria())
                    .nombre(linea.getNombre()).tamano(linea.getTamano())
                    .cantidad(BigDecimal.ZERO).cantidadComprada(linea.getCantidad())
                    .orden(orden).build();
        } else {
            fila.setCantidadComprada(fila.getCantidadComprada().add(linea.getCantidad()));
        }
        fila = articulosEvento.save(fila);
        linea.setComprada(true);
        linea.setArticuloEventoId(fila.getId());
        lineas.save(linea);
    }

    /**
     * Ajusta a mano la cantidad final de una línea. Solo con la lista bloqueada
     * (si no, {@link #sincronizar} la pisaría en la próxima lectura) y la línea
     * aún no comprada.
     */
    @Transactional
    public void ajustarLinea(Long usuarioId, Long eventoId, Long lineaId, AjustarLineaRequest req) {
        exigirArea(usuarioId);
        Evento e = eventoAbierto(eventoId);
        if (!e.isListaCompraBloqueada()) {
            throw new LineaCompraNoAjustableException();
        }
        LineaCompraEvento linea = lineas.findByIdAndEventoId(lineaId, eventoId)
                .orElseThrow(LineaCompraNoEncontradaException::new);
        if (linea.isComprada()) {
            throw new LineaCompraNoAjustableException();
        }
        String nuevoTamano = req.tamano() == null || req.tamano().isBlank() ? linea.getTamano() : req.tamano().trim();
        boolean cambiaTamano = !nuevoTamano.equals(linea.getTamano());
        boolean cambiaCantidad = linea.getCantidad().compareTo(req.cantidad()) != 0;
        // Solo cuenta como ajuste si de verdad cambia algo: abrir el editor y dejarlo igual no ajusta nada.
        if (!cambiaTamano && !cambiaCantidad) {
            return;
        }
        if (cambiaTamano) {
            cambiarTamano(linea, nuevoTamano, req.cantidad(), eventoId);
            return;
        }
        linea.setCantidad(req.cantidad());
        linea.setAjustada(true);
        lineas.save(linea);
    }

    /**
     * Cambia el tamaño de la botella de una línea de alcohol: pasa a la tienda más barata de ese
     * tamaño según la rejilla (sin tienda ni precio si no hay). Si ya existe otra línea de la misma
     * marca, tamaño y tienda, se suman las cantidades en esa.
     */
    private void cambiarTamano(LineaCompraEvento linea, String nuevoTamano, BigDecimal cantidad, Long eventoId) {
        if (linea.getCategoria() != CategoriaInventario.ALCOHOL
                || OptimizadorPrecioBebida.parseCl(nuevoTamano).isEmpty()) {
            throw new LineaCompraNoAjustableException();
        }
        OpcionPrecio barata = preciosPorMarca(eventoId).getOrDefault(linea.getNombre(), List.of()).stream()
                .filter(o -> o.tamano().equals(nuevoTamano))
                .min(java.util.Comparator.comparing(OpcionPrecio::precio)).orElse(null);
        String tienda = barata == null ? null : barata.tienda();
        LineaCompraEvento igual = lineas.findByEventoIdOrderByCategoriaAscNombreAsc(eventoId).stream()
                .filter(o -> !o.getId().equals(linea.getId()) && !o.isComprada()
                        && o.getCategoria() == linea.getCategoria() && o.getNombre().equals(linea.getNombre())
                        && o.getTamano().equals(nuevoTamano) && java.util.Objects.equals(o.getTienda(), tienda))
                .findFirst().orElse(null);
        if (igual != null) {
            igual.setCantidad(igual.getCantidad().add(cantidad));
            igual.setAjustada(true);
            lineas.save(igual);
            lineas.delete(linea);
            return;
        }
        linea.setTamano(nuevoTamano);
        linea.setTienda(tienda);
        linea.setPrecioUnitario(barata == null ? null : barata.precio());
        linea.setDetalle(null);
        linea.setCantidad(cantidad);
        linea.setAjustada(true);
        lineas.save(linea);
    }

    /** Bloquea o desbloquea el auto-calculo de la lista. Al bloquear, sincroniza una ultima vez. */
    @Transactional
    public void cambiarBloqueo(Long usuarioId, Long eventoId, boolean bloqueada) {
        exigirArea(usuarioId);
        Evento e = eventoAbierto(eventoId);
        materializar(e);
        if (bloqueada && !e.isListaCompraBloqueada()) {
            sincronizar(e, datosDe(e));
        }
        e.setListaCompraBloqueada(bloqueada);
        eventos.save(e);
    }

    // --- Administración -------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EventoListaCompraDto> eventos(Long usuarioId) {
        exigirArea(usuarioId);
        return eventos.noOcultos(penaId()).stream()
                .map(e -> new EventoListaCompraDto(e.getId(), e.getNombre(), e.getFecha(), e.getFechaFin()))
                .toList();
    }

    @Transactional
    public ListaCompraAdminResponse verAdmin(Long usuarioId, Long eventoId) {
        exigirArea(usuarioId);
        Evento e = eventoAbierto(eventoId);
        materializar(e);
        DatosEvento datos = datosDe(e);

        List<ReglaCompraEventoDto> reglas = reglasEvento
                .findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(eventoId).stream()
                .map(r -> aDto(r, datos))
                .toList();

        return new ListaCompraAdminResponse(
                new EventoListaCompraDto(e.getId(), e.getNombre(), e.getFecha(), e.getFechaFin()),
                e.isListaCompraBloqueada(), datos.apuntados(), datos.diasFiesta(), reglas);
    }

    private static ReglaCompraEventoDto aDto(ReglaCompraEvento r, DatosEvento datos) {
        BigDecimal calculada = CalculadoraListaCompra.lineasDe(r, datos).stream()
                .map(lc -> CalculadoraListaCompra.ceil(lc.bruto()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ajustada = r.getTipoFormula().esDinamica() ? null : r.getCantidadAjustada();
        BigDecimal fin = ajustada != null ? ajustada : calculada;
        return new ReglaCompraEventoDto(r.getId(), r.getCategoria().name(), r.getCategoria().etiqueta(),
                r.getNombre(), r.getTamano(), r.getTipoFormula().name(), r.getFactor(), r.getPorCada(),
                r.getOrigen().name(), calculada, ajustada, fin, r.isActiva());
    }

    @Transactional
    public void ajustarRegla(Long usuarioId, Long eventoId, Long reglaId, AjustarReglaRequest req) {
        exigirArea(usuarioId);
        eventoAbierto(eventoId);
        ReglaCompraEvento r = reglasEvento.findByIdAndEventoId(reglaId, eventoId)
                .orElseThrow(ReglaCompraNoEncontradaException::new);
        if (r.getTipoFormula().esDinamica() && req.cantidadAjustada() != null) {
            throw new AjusteNoAplicaException();
        }
        exigirPorCadaValido(r.getTipoFormula(), req.porCada());

        r.setCantidadAjustada(r.getTipoFormula().esDinamica() ? null : req.cantidadAjustada());
        r.setActiva(req.activa());
        r.setFactor(req.factor());
        r.setPorCada(req.porCada());
        reglasEvento.save(r);

        // La regla de este evento viene de la plantilla: se actualiza también ahí
        // para que los próximos eventos nazcan ya con la fórmula nueva.
        if (r.getOrigen() == OrigenReglaCompra.PLANTILLA) {
            plantilla.findByPenaIdAndCategoriaAndNombreAndTipoFormula(
                    penaId(), r.getCategoria(), r.getNombre(), r.getTipoFormula())
                    .ifPresent(p -> {
                        p.setFactor(req.factor());
                        p.setPorCada(req.porCada());
                        plantilla.save(p);
                    });
        }
    }

    /** Fórmulas "por cada N" exigen porCada > 0; el resto lo exige a null. */
    private static void exigirPorCadaValido(TipoFormulaCompra tipo, Integer porCada) {
        boolean esPorCada = tipo == TipoFormulaCompra.POR_CADA_N_PENISTAS
                || tipo == TipoFormulaCompra.POR_CADA_N_PENISTAS_DIA;
        if (esPorCada == (porCada == null) || (porCada != null && porCada <= 0)) {
            throw new PorCadaNoAplicaException();
        }
    }

    @Transactional
    public ReglaCompraEventoDto crearRegla(Long usuarioId, Long eventoId, CrearReglaRequest req) {
        exigirArea(usuarioId);
        Evento e = eventoAbierto(eventoId);
        materializar(e);
        if (req.tipoFormula().esDinamica()) {
            throw new FormulaNoCreableException();
        }
        exigirPorCadaValido(req.tipoFormula(), req.porCada());
        int orden = reglasEvento.findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(eventoId).stream()
                .filter(x -> x.getCategoria() == req.categoria())
                .mapToInt(ReglaCompraEvento::getOrden).max().orElse(0) + 1;
        ReglaCompraEvento r = ReglaCompraEvento.builder()
                .evento(e).categoria(req.categoria()).nombre(req.nombre().trim()).tamano(req.tamano().trim())
                .tipoFormula(req.tipoFormula()).factor(req.factor()).porCada(req.porCada())
                .orden(orden).origen(OrigenReglaCompra.MANUAL).cantidadAjustada(null).activa(true).build();
        try {
            r = reglasEvento.saveAndFlush(r);
        } catch (DataIntegrityViolationException ex) {
            throw new ReglaCompraDuplicadaException();
        }
        return aDto(r, datosDe(e));
    }

    @Transactional
    public void borrarRegla(Long usuarioId, Long eventoId, Long reglaId) {
        exigirArea(usuarioId);
        eventoAbierto(eventoId);
        ReglaCompraEvento r = reglasEvento.findByIdAndEventoId(reglaId, eventoId)
                .orElseThrow(ReglaCompraNoEncontradaException::new);
        if (r.getOrigen() == OrigenReglaCompra.PLANTILLA) {
            throw new ReglaCompraNoBorrableException();
        }
        reglasEvento.delete(r);
    }
}
