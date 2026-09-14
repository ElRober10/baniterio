package com.baniterio.api.evento;

import java.util.List;

import com.baniterio.api.admin.SinPermisoException;
import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.evento.dto.BebidaPendiente;
import com.baniterio.api.evento.dto.BebidaRef;
import com.baniterio.api.evento.dto.CatalogoBebidas;
import com.baniterio.api.identidad.Bebida;
import com.baniterio.api.identidad.BebidaRepository;
import com.baniterio.api.identidad.EstadoBebida;
import com.baniterio.api.identidad.TipoBebida;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.push.Audiencia;
import com.baniterio.api.push.AvisoPushEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de bebidas de la ficha de San Miguel: qué sale en los desplegables
 * (solo {@code ACEPTADA}), alta de una bebida nueva desde "Otra…" (queda
 * {@code PENDIENTE} y avisa a los administradores) y aprobación/rechazo.
 */
@Service
public class BebidaService {

    private static final String TITULO_PUSH = "Bebidas";

    private final BebidaRepository bebidas;
    private final UsuarioRepository usuarios;
    private final ServicioPermisos permisos;
    private final ApplicationEventPublisher publisher;

    public BebidaService(BebidaRepository bebidas, UsuarioRepository usuarios,
                         ServicioPermisos permisos, ApplicationEventPublisher publisher) {
        this.bebidas = bebidas;
        this.usuarios = usuarios;
        this.permisos = permisos;
        this.publisher = publisher;
    }

    @Transactional(readOnly = true)
    public CatalogoBebidas catalogo() {
        return new CatalogoBebidas(aceptadas(TipoBebida.ALCOHOL), aceptadas(TipoBebida.REFRESCO));
    }

    private List<BebidaRef> aceptadas(TipoBebida tipo) {
        return bebidas.findByTipoAndEstadoOrderByNombreAsc(tipo, EstadoBebida.ACEPTADA).stream()
                .map(b -> new BebidaRef(b.getId(), b.getNombre()))
                .toList();
    }

    /**
     * Resuelve el nombre que alguien escribió en "Otra…": si ya existe una bebida
     * de ese tipo con ese nombre se reutiliza (aunque esté PENDIENTE); si estaba
     * RECHAZADA se reabre; si no existe se crea PENDIENTE. En los dos últimos
     * casos se avisa a los administradores.
     */
    @Transactional
    public Bebida resolverOtra(TipoBebida tipo, String nombreRaw, Long propuestaPorId) {
        String nombre = nombreRaw.trim().replaceAll("\\s+", " ");
        Bebida existente = bebidas.findByTipoAndNombreIgnoreCase(tipo, nombre).orElse(null);
        if (existente != null) {
            if (existente.getEstado() == EstadoBebida.RECHAZADA) {
                existente.setEstado(EstadoBebida.PENDIENTE);
                bebidas.save(existente);
                avisar(tipo, nombre, propuestaPorId);
            }
            return existente;
        }
        Usuario proponente = propuestaPorId == null ? null
                : usuarios.findById(propuestaPorId).orElse(null);
        Bebida nueva = bebidas.save(Bebida.builder()
                .tipo(tipo).nombre(nombre).estado(EstadoBebida.PENDIENTE)
                .propuestaPor(proponente).build());
        avisar(tipo, nombre, propuestaPorId);
        return nueva;
    }

    private void avisar(TipoBebida tipo, String nombre, Long propuestaPorId) {
        String quien = propuestaPorId == null ? "Alguien"
                : usuarios.findById(propuestaPorId).map(Usuario::getNombre).orElse("Alguien");
        String cual = tipo == TipoBebida.ALCOHOL ? "alcohol" : "refresco";
        publisher.publishEvent(new AvisoPushEvent(new Audiencia.Administradores(),
                TITULO_PUSH, quien + " ha propuesto «" + nombre + "» (" + cual + ")"));
    }

    @Transactional(readOnly = true)
    public List<BebidaPendiente> pendientes(Long usuarioId) {
        exigirAdmin(usuarioId);
        return bebidas.findByEstadoOrderByCreatedAtAsc(EstadoBebida.PENDIENTE).stream()
                .map(b -> new BebidaPendiente(b.getId(), b.getTipo().name(), b.getNombre(),
                        b.getPropuestaPor() == null ? null
                                : new BebidaPendiente.ProponenteBebida(
                                        b.getPropuestaPor().getId(), b.getPropuestaPor().getNombre()),
                        b.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void aceptar(Long usuarioId, Long bebidaId) {
        exigirAdmin(usuarioId);
        Bebida b = bebidas.findById(bebidaId).orElseThrow(BebidaNoEncontradaException::new);
        b.setEstado(EstadoBebida.ACEPTADA);
        bebidas.save(b);
    }

    @Transactional
    public void rechazar(Long usuarioId, Long bebidaId) {
        exigirAdmin(usuarioId);
        Bebida b = bebidas.findById(bebidaId).orElseThrow(BebidaNoEncontradaException::new);
        b.setEstado(EstadoBebida.RECHAZADA);
        bebidas.save(b);
        if (b.getPropuestaPor() != null) {
            publisher.publishEvent(new AvisoPushEvent(
                    new Audiencia.UsuarioUnico(b.getPropuestaPor().getId()),
                    TITULO_PUSH, "Tu propuesta de bebida «" + b.getNombre() + "» no se ha aceptado."));
        }
    }

    private void exigirAdmin(Long usuarioId) {
        permisos.exigirAdmin(usuarioId, SinPermisoException::new);
    }
}
