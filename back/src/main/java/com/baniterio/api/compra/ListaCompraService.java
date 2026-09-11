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
import com.baniterio.api.compra.dto.AjustarReglaRequest;
import com.baniterio.api.compra.dto.CategoriaListaCompraDto;
import com.baniterio.api.compra.dto.CrearReglaRequest;
import com.baniterio.api.compra.dto.EventoListaCompraDto;
import com.baniterio.api.compra.dto.LineaCompraDto;
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
import com.baniterio.api.identidad.PenaPilotoService;
import com.baniterio.api.inventario.ArticuloEvento;
import com.baniterio.api.inventario.ArticuloEventoRepository;
import com.baniterio.api.inventario.CategoriaInventario;
import com.baniterio.api.inventario.SinPermisoInventarioException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lista de la compra por evento: montar la lista calculada (cualquier peñista) y
 * el editor de "Cantidades para eventos" (área INVENTARIO). Las reglas de cada
 * evento se materializan copiando la plantilla global la primera vez que se pide
 * la lista. Bloque 1: cálculo bruto con redondeo hacia arriba; el descuento del
 * inventario de la fiesta es el bloque 2.
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
    private final PenaPilotoService pena;
    private final ServicioPermisos permisos;

    public ListaCompraService(ReglaCompraRepository plantilla, ReglaCompraEventoRepository reglasEvento,
                              LineaCompraEventoRepository lineas, ArticuloEventoRepository articulosEvento,
                              EventoRepository eventos, AsistenciaEventoRepository asistencias,
                              FichaBebidaRepository fichas, PenaPilotoService pena, ServicioPermisos permisos) {
        this.plantilla = plantilla;
        this.reglasEvento = reglasEvento;
        this.lineas = lineas;
        this.articulosEvento = articulosEvento;
        this.eventos = eventos;
        this.asistencias = asistencias;
        this.fichas = fichas;
        this.pena = pena;
        this.permisos = permisos;
    }

    /** Una línea del cálculo con su clave de identidad. */
    private record LineaCalc(CategoriaInventario categoria, String nombre, String tamano,
                             BigDecimal cantidad, int orden, boolean dinamica,
                             boolean necesitaFicha, boolean ajustada) {
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
                    f != null ? f.getRefresco().getNombre() : null,
                    f != null ? f.getAlternativa() : null));
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

        Map<CategoriaInventario, List<LineaCompraDto>> porCategoria = new LinkedHashMap<>();
        for (CategoriaInventario cat : CategoriaInventario.values()) {
            porCategoria.put(cat, new ArrayList<>());
        }
        for (LineaCompraEvento l : lineas.findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(eventoId)) {
            porCategoria.get(l.getCategoria()).add(new LineaCompraDto(
                    l.getId(), l.getNombre(), l.getTamano(), l.getCantidad(), BigDecimal.ZERO,
                    l.isAjustada(), l.isDinamica(), l.isNecesitaFicha(), l.isComprada()));
        }

        List<CategoriaListaCompraDto> categorias = new ArrayList<>();
        porCategoria.forEach((cat, ls) -> {
            if (!ls.isEmpty()) {
                categorias.add(new CategoriaListaCompraDto(cat.name(), cat.etiqueta(), ls));
            }
        });
        return new ListaCompraResponse(puedoEditar, datos.llevaFicha(), e.isListaCompraBloqueada(),
                datos.apuntados(), datos.diasFiesta(), categorias);
    }

    /** Todas las líneas que el cálculo produce ahora mismo para el evento. */
    private List<LineaCalc> calcular(Long eventoId, DatosEvento datos) {
        List<ReglaCompraEvento> activas = reglasEvento
                .findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(eventoId).stream()
                .filter(ReglaCompraEvento::isActiva).toList();
        List<LineaCalc> out = new ArrayList<>();
        for (ReglaCompraEvento r : activas) {
            for (LineaCalculada lc : CalculadoraListaCompra.lineasDe(r, datos)) {
                BigDecimal calculada = CalculadoraListaCompra.ceil(lc.bruto());
                boolean ajustada = !r.getTipoFormula().esDinamica() && r.getCantidadAjustada() != null;
                BigDecimal cantidad = ajustada ? r.getCantidadAjustada() : calculada;
                out.add(new LineaCalc(r.getCategoria(), lc.nombre(), lc.tamano(), cantidad,
                        r.getOrden(), lc.dinamica(), lc.necesitaFicha(), ajustada));
            }
        }
        return out;
    }

    /** Alinea linea_compra_evento con el cálculo actual. No toca las líneas compradas. */
    private void sincronizar(Evento e, DatosEvento datos) {
        List<LineaCalc> calculadas = calcular(e.getId(), datos);
        Map<String, LineaCompraEvento> existentes = new LinkedHashMap<>();
        for (LineaCompraEvento l : lineas.findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(e.getId())) {
            existentes.put(clave(l.getCategoria(), l.getNombre(), l.getTamano()), l);
        }
        Set<String> vistas = new HashSet<>();
        for (LineaCalc c : calculadas) {
            String k = clave(c.categoria(), c.nombre(), c.tamano());
            vistas.add(k);
            LineaCompraEvento fila = existentes.get(k);
            if (fila == null) {
                lineas.save(LineaCompraEvento.builder()
                        .evento(e).categoria(c.categoria()).nombre(c.nombre()).tamano(c.tamano())
                        .cantidad(c.cantidad()).orden(c.orden()).dinamica(c.dinamica())
                        .necesitaFicha(c.necesitaFicha()).ajustada(c.ajustada())
                        .comprada(false).build());
            } else if (!fila.isComprada()) {
                fila.setCantidad(c.cantidad());
                fila.setOrden(c.orden());
                fila.setDinamica(c.dinamica());
                fila.setNecesitaFicha(c.necesitaFicha());
                fila.setAjustada(c.ajustada());
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
            int orden = articulosEvento.findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(eventoId).stream()
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
        r.setCantidadAjustada(r.getTipoFormula().esDinamica() ? null : req.cantidadAjustada());
        r.setActiva(req.activa());
        reglasEvento.save(r);
    }

    @Transactional
    public ReglaCompraEventoDto crearRegla(Long usuarioId, Long eventoId, CrearReglaRequest req) {
        exigirArea(usuarioId);
        Evento e = eventoAbierto(eventoId);
        materializar(e);
        if (req.tipoFormula().esDinamica()) {
            throw new FormulaNoCreableException();
        }
        boolean esPorCada = req.tipoFormula() == TipoFormulaCompra.POR_CADA_N_PENISTAS;
        if (esPorCada == (req.porCada() == null) || (req.porCada() != null && req.porCada() <= 0)) {
            throw new PorCadaNoAplicaException();
        }
        int orden = reglasEvento.findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(eventoId).stream()
                .filter(x -> x.getCategoria() == req.categoria())
                .mapToInt(ReglaCompraEvento::getOrden).max().orElse(0) + 1;
        ReglaCompraEvento r = ReglaCompraEvento.builder()
                .evento(e).categoria(req.categoria()).nombre(req.nombre().trim()).tamano(req.tamano().trim())
                .tipoFormula(req.tipoFormula()).factor(req.factor()).porCada(esPorCada ? req.porCada() : null)
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
