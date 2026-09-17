package com.baniterio.api.preciobebida;

import java.math.BigDecimal;
import java.util.List;

import com.baniterio.api.admin.SinPermisoException;
import com.baniterio.api.auth.ServicioPermisos;
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
import com.baniterio.api.preciobebida.dto.AnadirTamanoRequest;
import com.baniterio.api.preciobebida.dto.CrearTiendaRequest;
import com.baniterio.api.preciobebida.dto.EventoPrecioBebidaDto;
import com.baniterio.api.preciobebida.dto.GrillaAlcoholResponse;
import com.baniterio.api.preciobebida.dto.GuardarPrecioRequest;
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
    private final ServicioPermisos permisos;

    public PrecioBebidaService(EventoRepository eventos, PenaPilotoService pena, TiendaRepository tiendas,
            TamanoPrecioBebidaEventoRepository tamanos, PrecioBebidaEventoRepository precios,
            BebidaRepository bebidas, ServicioPermisos permisos) {
        this.eventos = eventos;
        this.pena = pena;
        this.tiendas = tiendas;
        this.tamanos = tamanos;
        this.precios = precios;
        this.bebidas = bebidas;
        this.permisos = permisos;
    }

    private Long penaId() {
        return pena.id();
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
        List<String> listaTamanos = tamanos.findByEventoIdOrderByOrdenAscIdAsc(eventoId).stream()
                .map(TamanoPrecioBebidaEvento::getTamano).toList();
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
        return tamanos.findByEventoIdOrderByOrdenAscIdAsc(eventoId).stream()
                .map(TamanoPrecioBebidaEvento::getTamano).toList();
    }
}
