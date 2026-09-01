package com.baniterio.api.perfil;

import java.util.Optional;

import com.baniterio.api.identidad.EstadoVinculo;
import com.baniterio.api.identidad.Hijo;
import com.baniterio.api.identidad.HijoRepository;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.TelefonoAutorizado;
import com.baniterio.api.identidad.TelefonoAutorizadoRepository;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.identidad.VinculoPareja;
import com.baniterio.api.identidad.VinculoParejaRepository;
import com.baniterio.api.push.Audiencia;
import com.baniterio.api.push.AvisoPushEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Máquina de estados del vínculo de pareja: declararlo desde el editor, promover
 * {@code SIN_CUENTA → PENDIENTE} cuando la pareja se registra, aceptar/rechazar/
 * romper, y las altas/bajas asociadas en {@code telefono_autorizado}. Los avisos
 * push salen como {@link AvisoPushEvent} (se entregan {@code AFTER_COMMIT}).
 *
 * <p>Ver el spec, "Flujo: vínculo de pareja", para las transiciones completas.
 */
@Service
public class VinculoParejaService {

    /** Peña piloto (alcance de una sola peña); se resuelve por slug, como en {@code AuthService}. */
    private static final String SLUG_PENA = "baniterio";

    private static final String TITULO_PUSH = "Vínculo de pareja";

    private final VinculoParejaRepository vinculos;
    private final UsuarioRepository usuarios;
    private final MembresiaRepository membresias;
    private final PenaRepository penas;
    private final TelefonoAutorizadoRepository telefonosAutorizados;
    private final HijoRepository hijos;
    private final ApplicationEventPublisher eventos;

    public VinculoParejaService(VinculoParejaRepository vinculos, UsuarioRepository usuarios,
            MembresiaRepository membresias, PenaRepository penas,
            TelefonoAutorizadoRepository telefonosAutorizados, HijoRepository hijos,
            ApplicationEventPublisher eventos) {
        this.vinculos = vinculos;
        this.usuarios = usuarios;
        this.membresias = membresias;
        this.penas = penas;
        this.telefonosAutorizados = telefonosAutorizados;
        this.hijos = hijos;
        this.eventos = eventos;
    }

    /**
     * Al entrar (cada {@code GET /perfil}), si hay un vínculo {@code SIN_CUENTA}
     * dirigido al teléfono de este usuario y lo declaró otra persona, promoverlo
     * a {@code PENDIENTE} y enlazarlo a esta cuenta. Sin push: el usuario está
     * entrando ahora y la UI le muestra el banner "X dice que sois pareja".
     */
    @Transactional
    public void reconciliarAlEntrar(Long usuarioId) {
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow();

        Optional<VinculoPareja> candidato = vinculos
                .findByParejaTelefonoAndEstado(usuario.getTelefono(), EstadoVinculo.SIN_CUENTA);
        if (candidato.isEmpty()) {
            return;
        }
        VinculoPareja v = candidato.get();
        if (v.getSolicitante().getId().equals(usuarioId)) {
            return;
        }
        // NOTE: si este usuario ya tiene un vínculo vivo propio, no lo forzamos a
        // este; se queda SIN_CUENTA y el solicitante puede rehacerlo. (El spec no
        // cubre el choque; esta es la opción mínima y segura.)
        if (tieneVinculoVivo(usuarioId)) {
            return;
        }
        v.setParejaUsuario(usuario);
        v.setEstado(EstadoVinculo.PENDIENTE);
        vinculos.save(v);
    }

    /**
     * Aplica la respuesta del editor sobre la pareja (parte del {@code PUT /perfil}).
     *
     * <p>{@code tienePareja=false}: si el usuario tenía un vínculo vivo declarado
     * por él, se deshace (un {@code SIN_CUENTA}/{@code PENDIENTE} se borra; un
     * {@code ACEPTADO} se trata como romper).
     *
     * <p>{@code tienePareja=true}: crea o ajusta el vínculo con
     * {@code parejaNombre}/{@code parejaTelefono}, decide {@code SIN_CUENTA} vs
     * {@code PENDIENTE} según si el teléfono es de un miembro, publica el push y
     * da de alta el teléfono en {@code telefono_autorizado} si hace falta.
     */
    @Transactional
    public void aplicarDesdePerfil(Long usuarioId, boolean tienePareja, String parejaNombre, String parejaTelefono) {
        if (!tienePareja) {
            deshacerLoDeclarado(usuarioId);
            return;
        }
        declarar(usuarioId, parejaNombre, parejaTelefono);
    }

    private void deshacerLoDeclarado(Long usuarioId) {
        Optional<VinculoPareja> propio = vinculos
                .findBySolicitanteIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO);
        if (propio.isEmpty()) {
            return;
        }
        VinculoPareja v = propio.get();
        if (v.getEstado() == EstadoVinculo.ACEPTADO) {
            romperInterno(usuarioId);
            return;
        }
        EstadoVinculo estado = v.getEstado();
        String tel = v.getParejaTelefono();
        vinculos.delete(v);
        vinculos.flush();
        if (estado == EstadoVinculo.SIN_CUENTA) {
            podarTelefono(tel);
        }
    }

    private void declarar(Long usuarioId, String parejaNombre, String parejaTelefono) {
        if (!StringUtils.hasText(parejaNombre)) {
            throw new TelefonoParejaInvalidoException();
        }
        String tel = NormalizadorTelefono.normalizar(parejaTelefono)
                .orElseThrow(TelefonoParejaInvalidoException::new);

        Usuario solicitante = usuarios.findById(usuarioId).orElseThrow();
        if (tel.equals(solicitante.getTelefono())) {
            throw new TelefonoParejaInvalidoException();
        }
        String nombre = parejaNombre.trim();

        Optional<VinculoPareja> existente = vinculos
                .findBySolicitanteIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO);
        if (existente.isPresent()) {
            VinculoPareja v = existente.get();
            if (v.getParejaTelefono().equals(tel)) {
                // Mismo teléfono: solo cambia el nombre; y si era SIN_CUENTA y el
                // teléfono ya es de un miembro, se promueve a PENDIENTE.
                v.setParejaNombre(nombre);
                if (v.getEstado() == EstadoVinculo.SIN_CUENTA) {
                    miembroConTelefono(tel, usuarioId).ifPresent(b -> {
                        exigirMiembroLibre(b);
                        v.setParejaUsuario(b);
                        v.setEstado(EstadoVinculo.PENDIENTE);
                        avisar(b.getId(), solicitante.getNombre() + " dice que sois pareja");
                    });
                }
                vinculos.save(v);
                return;
            }
            // Teléfono distinto: se reemplaza el vínculo anterior. Si el anterior
            // estaba ACEPTADO, cambiar de teléfono en el editor equivale a romper
            // (desreparenta hijos y avisa a la ex pareja) y volver a declarar.
            if (v.getEstado() == EstadoVinculo.ACEPTADO) {
                romperInterno(usuarioId);
            } else {
                EstadoVinculo estadoPrevio = v.getEstado();
                String telPrevio = v.getParejaTelefono();
                vinculos.delete(v);
                vinculos.flush();
                if (estadoPrevio == EstadoVinculo.SIN_CUENTA) {
                    podarTelefono(telPrevio);
                }
            }
        }

        crearVinculo(solicitante, nombre, tel);
    }

    /** {@code POST /perfil/pareja/aceptar}: el destinatario del vínculo {@code PENDIENTE} lo confirma. */
    @Transactional
    public void aceptar(Long usuarioId) {
        VinculoPareja v = vinculos.findByParejaUsuarioIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO)
                .filter(x -> x.getEstado() == EstadoVinculo.PENDIENTE)
                .orElseThrow(VinculoNoEncontradoException::new);

        Usuario solicitante = v.getSolicitante();
        if (otroVinculoVivo(solicitante.getId(), v.getId()) || otroVinculoVivo(usuarioId, v.getId())) {
            throw new VinculoConflictoException("YA_TIENE_PAREJA");
        }

        v.setEstado(EstadoVinculo.ACEPTADO);
        vinculos.save(v);

        reparentarHijos(solicitante.getId(), v);
        reparentarHijos(usuarioId, v);

        Usuario aceptante = usuarios.findById(usuarioId).orElseThrow();
        avisar(solicitante.getId(), aceptante.getNombre() + " ha aceptado el vínculo de pareja");
    }

    /** {@code POST /perfil/pareja/rechazar}: el destinatario del vínculo {@code PENDIENTE} lo rechaza (terminal). */
    @Transactional
    public void rechazar(Long usuarioId) {
        VinculoPareja v = vinculos.findByParejaUsuarioIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO)
                .filter(x -> x.getEstado() == EstadoVinculo.PENDIENTE)
                .orElseThrow(VinculoNoEncontradoException::new);

        v.setEstado(EstadoVinculo.RECHAZADO);
        vinculos.save(v);

        Usuario quienRechaza = usuarios.findById(usuarioId).orElseThrow();
        avisar(v.getSolicitante().getId(), quienRechaza.getNombre() + " ha rechazado el vínculo de pareja");
    }

    /** {@code DELETE /perfil/pareja}: cualquiera de los dos deshace un vínculo vivo. */
    @Transactional
    public void romper(Long usuarioId) {
        romperInterno(usuarioId);
    }

    private void romperInterno(Long usuarioId) {
        VinculoPareja v = vinculos.findBySolicitanteIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO)
                .or(() -> vinculos.findByParejaUsuarioIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO))
                .orElseThrow(VinculoNoEncontradoException::new);

        boolean soySolicitante = v.getSolicitante().getId().equals(usuarioId);
        Long otroLado;
        if (soySolicitante) {
            otroLado = v.getParejaUsuario() != null ? v.getParejaUsuario().getId() : null;
        } else {
            otroLado = v.getSolicitante().getId();
        }

        if (v.getEstado() == EstadoVinculo.ACEPTADO) {
            for (Hijo h : hijos.findByVinculoParejaId(v.getId())) {
                h.setVinculoPareja(null);
                hijos.save(h);
            }
        }

        EstadoVinculo estadoPrevio = v.getEstado();
        String telPrevio = v.getParejaTelefono();
        vinculos.delete(v);
        vinculos.flush();
        if (estadoPrevio == EstadoVinculo.SIN_CUENTA) {
            podarTelefono(telPrevio);
        }

        if (otroLado != null) {
            Usuario quienRompe = usuarios.findById(usuarioId).orElseThrow();
            avisar(otroLado, quienRompe.getNombre() + " ha deshecho el vínculo de pareja");
        }
    }

    // --- Helpers ---

    private void crearVinculo(Usuario solicitante, String nombre, String tel) {
        Optional<Usuario> miembro = miembroConTelefono(tel, solicitante.getId());
        VinculoPareja v = new VinculoPareja();
        v.setSolicitante(solicitante);
        v.setParejaNombre(nombre);
        v.setParejaTelefono(tel);

        if (miembro.isEmpty()) {
            v.setEstado(EstadoVinculo.SIN_CUENTA);
            vinculos.save(v);
            altaTelefonoAutorizado(tel, solicitante);
        } else {
            Usuario b = miembro.get();
            exigirMiembroLibre(b);
            v.setParejaUsuario(b);
            v.setEstado(EstadoVinculo.PENDIENTE);
            vinculos.save(v);
            avisar(b.getId(), solicitante.getNombre() + " dice que sois pareja");
        }
    }

    /** Usuario activo con membresía activa en la peña y ese teléfono, distinto del solicitante. */
    private Optional<Usuario> miembroConTelefono(String tel, Long solicitanteId) {
        return usuarios.findByTelefono(tel)
                .filter(Usuario::isActivo)
                .filter(u -> !u.getId().equals(solicitanteId))
                .filter(u -> membresias.findByUsuarioIdAndPenaId(u.getId(), penaId())
                        .map(m -> m.isActiva())
                        .orElse(false));
    }

    private void exigirMiembroLibre(Usuario b) {
        if (tieneVinculoVivo(b.getId())) {
            throw new VinculoConflictoException("TELEFONO_YA_EMPAREJADO");
        }
    }

    private boolean tieneVinculoVivo(Long usuarioId) {
        return vinculos.findBySolicitanteIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO).isPresent()
                || vinculos.findByParejaUsuarioIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO).isPresent();
    }

    private boolean otroVinculoVivo(Long usuarioId, Long exceptoId) {
        return vinculos.findBySolicitanteIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO)
                        .filter(v -> !v.getId().equals(exceptoId)).isPresent()
                || vinculos.findByParejaUsuarioIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO)
                        .filter(v -> !v.getId().equals(exceptoId)).isPresent();
    }

    private void reparentarHijos(Long creadorId, VinculoPareja vinculo) {
        for (Hijo h : hijos.findByCreadorId(creadorId)) {
            if (h.getVinculoPareja() == null) {
                h.setVinculoPareja(vinculo);
                hijos.save(h);
            }
        }
    }

    private void altaTelefonoAutorizado(String tel, Usuario autorizadoPor) {
        if (telefonosAutorizados.findByTelefono(tel).isPresent()) {
            return;
        }
        TelefonoAutorizado ta = new TelefonoAutorizado();
        ta.setTelefono(tel);
        ta.setPena(pena());
        ta.setUsado(false);
        ta.setAutorizadoPor(autorizadoPor);
        telefonosAutorizados.save(ta);
    }

    /**
     * Al quitar una pareja {@code SIN_CUENTA}: si la fila de
     * {@code telefono_autorizado} sigue sin usar y ya no la referencia ningún
     * otro vínculo vivo ni hijo, se borra.
     */
    private void podarTelefono(String tel) {
        telefonosAutorizados.findByTelefono(tel).ifPresent(ta -> {
            if (ta.isUsado()) {
                return;
            }
            boolean referenciado = !vinculos.findAllByParejaTelefonoAndEstadoNot(tel, EstadoVinculo.RECHAZADO).isEmpty()
                    || hijos.findByTelefono(tel).isPresent();
            if (!referenciado) {
                telefonosAutorizados.delete(ta);
            }
        });
    }

    private void avisar(Long usuarioId, String cuerpo) {
        eventos.publishEvent(new AvisoPushEvent(new Audiencia.UsuarioUnico(usuarioId), TITULO_PUSH, cuerpo));
    }

    private Long penaId() {
        return pena().getId();
    }

    private Pena pena() {
        return penas.findBySlug(SLUG_PENA).orElseThrow();
    }
}
