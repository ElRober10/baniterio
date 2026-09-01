package com.baniterio.api.perfil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.baniterio.api.identidad.EstadoVinculo;
import com.baniterio.api.identidad.Hijo;
import com.baniterio.api.identidad.HijoRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.TelefonoAutorizado;
import com.baniterio.api.identidad.TelefonoAutorizadoRepository;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.identidad.VinculoPareja;
import com.baniterio.api.identidad.VinculoParejaRepository;
import com.baniterio.api.perfil.dto.HijoRequest;
import com.baniterio.api.push.Audiencia;
import com.baniterio.api.push.AvisoPushEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Reconcilia la lista de hijos del editor con lo que había en {@code hijo}:
 * altas ({@code id} nulo), ediciones ({@code id} presente), bajas (los que
 * estaban y ya no vienen), y las altas/bajas de sus teléfonos en
 * {@code telefono_autorizado}. Además:
 *
 * <ul>
 *   <li>{@link #alAceptarVinculo}/{@link #alRomperVinculo}: cuando dos miembros
 *       forman (o deshacen) un vínculo {@code ACEPTADO}, sus hijos pasan a
 *       colgar del vínculo (o vuelven a su creador). Lo llama
 *       {@link VinculoParejaService}.
 *   <li>{@link #enlazarSiEsHijo}: si el que entra tiene un {@code hijo} con su
 *       teléfono y sin cuenta, se le enlaza y —si es menor— se avisa a los
 *       padres. <b>El enlace ocurre en el primer {@code GET /perfil} del hijo</b>
 *       (lo llama {@link PerfilService#miPerfil}, junto a
 *       {@code reconciliarAlEntrar}), no en el registro: así {@code AuthService}
 *       no necesita saber de hijos ni publicar eventos.
 * </ul>
 *
 * <p>Un {@code hijo} con {@code usuario_id != null} (ya registrado, tiene tarjeta
 * propia) está protegido: desde el editor del padre no se borra ni se le cambia
 * {@code nombre}/{@code telefono}; solo {@code visible}. En la práctica
 * {@code miPerfil} no se los lista, pero se protege igualmente.
 */
@Service
public class HijosReconciliador {

    /** Peña piloto (alcance de una sola peña); se resuelve por slug, como en {@link VinculoParejaService}. */
    private static final String SLUG_PENA = "baniterio";

    private static final String TITULO_PUSH_HIJO_REGISTRADO = "Tu hijo se ha registrado";

    private final HijoRepository hijos;
    private final VinculoParejaRepository vinculos;
    private final UsuarioRepository usuarios;
    private final TelefonoAutorizadoRepository telefonosAutorizados;
    private final PenaRepository penas;
    private final ApplicationEventPublisher eventos;

    public HijosReconciliador(HijoRepository hijos, VinculoParejaRepository vinculos,
            UsuarioRepository usuarios, TelefonoAutorizadoRepository telefonosAutorizados,
            PenaRepository penas, ApplicationEventPublisher eventos) {
        this.hijos = hijos;
        this.vinculos = vinculos;
        this.usuarios = usuarios;
        this.telefonosAutorizados = telefonosAutorizados;
        this.penas = penas;
        this.eventos = eventos;
    }

    /**
     * Aplica la lista de hijos del editor (parte del {@code PUT /perfil}) sobre lo
     * que había: altas, ediciones y bajas, más las altas/bajas de teléfonos en
     * {@code telefono_autorizado}.
     */
    @Transactional
    public void aplicar(Long usuarioId, List<HijoRequest> peticion) {
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow();
        VinculoPareja vinculoAceptado = vinculoAceptadoDe(usuarioId).orElse(null);

        Map<Long, Hijo> actuales = new LinkedHashMap<>();
        for (Hijo h : hijos.findByCreadorId(usuarioId)) {
            actuales.put(h.getId(), h);
        }
        if (vinculoAceptado != null) {
            for (Hijo h : hijos.findByVinculoParejaId(vinculoAceptado.getId())) {
                actuales.putIfAbsent(h.getId(), h);
            }
        }

        Set<Long> tocados = altasYEdiciones(usuario, vinculoAceptado, peticion, actuales);
        bajas(actuales, tocados);
    }

    /** Procesa las altas ({@code id} nulo) y ediciones; devuelve los ids de {@code actuales} que se editaron. */
    private Set<Long> altasYEdiciones(Usuario usuario, VinculoPareja vinculoAceptado,
            List<HijoRequest> peticion, Map<Long, Hijo> actuales) {
        Set<Long> tocados = new LinkedHashSet<>();
        for (HijoRequest r : peticion) {
            if (r.id() == null) {
                crear(usuario, vinculoAceptado, r);
            } else {
                Hijo h = actuales.get(r.id());
                // h == null: id ajeno o inexistente, se ignora (el editor no debería mandarlo).
                if (h != null) {
                    tocados.add(h.getId());
                    editar(usuario, h, r);
                }
            }
        }
        return tocados;
    }

    /** Borra los hijos que estaban y ya no vienen (salvo los registrados) y poda sus teléfonos. */
    private void bajas(Map<Long, Hijo> actuales, Set<Long> tocados) {
        List<String> telefonosLiberados = new ArrayList<>();
        for (Hijo h : actuales.values()) {
            // Un hijo tocado en el diff, o ya registrado (tiene tarjeta propia), no se borra.
            boolean baja = !tocados.contains(h.getId()) && h.getUsuario() == null;
            if (baja) {
                if (h.getTelefono() != null) {
                    telefonosLiberados.add(h.getTelefono());
                }
                hijos.delete(h);
            }
        }
        if (!telefonosLiberados.isEmpty()) {
            hijos.flush();
            telefonosLiberados.forEach(this::podarTelefono);
        }
    }

    /**
     * Al aceptarse un vínculo entre A ({@code solicitante}) y B
     * ({@code parejaUsuario}): los hijos que cada uno creó y que aún no cuelgan de
     * ningún vínculo pasan a colgar de este. No se fusionan duplicados.
     */
    @Transactional
    public void alAceptarVinculo(VinculoPareja vinculo) {
        reparentar(vinculo.getSolicitante().getId(), vinculo);
        if (vinculo.getParejaUsuario() != null) {
            reparentar(vinculo.getParejaUsuario().getId(), vinculo);
        }
    }

    /** Al romperse el vínculo, cada hijo vuelve a {@code vinculoPareja = null} (conserva su {@code creador}). */
    @Transactional
    public void alRomperVinculo(VinculoPareja vinculo) {
        for (Hijo h : hijos.findByVinculoParejaId(vinculo.getId())) {
            h.setVinculoPareja(null);
            hijos.save(h);
        }
    }

    /**
     * Si el usuario que acaba de entrar tiene algún {@code hijo} declarado con su
     * teléfono y sin cuenta enlazada, se le enlaza. Si ese hijo es menor de edad,
     * se avisa por push a los padres (el creador y, si hay vínculo
     * {@code ACEPTADO}, también la pareja).
     *
     * <p>Se invoca desde {@link PerfilService#miPerfil} (primer {@code GET /perfil}
     * del hijo).
     */
    @Transactional
    public void enlazarSiEsHijo(Usuario recienRegistrado) {
        String telefono = recienRegistrado.getTelefono();
        if (telefono == null) {
            return;
        }
        for (Hijo h : hijos.findAllByTelefono(telefono)) {
            if (h.getUsuario() != null) {
                continue;
            }
            h.setUsuario(recienRegistrado);
            hijos.save(h);
            if (!h.isMayorDeEdad()) {
                avisarPadres(h);
            }
        }
    }

    // --- Helpers ---

    private void crear(Usuario usuario, VinculoPareja vinculoAceptado, HijoRequest r) {
        String telefono = normalizarOpcional(r.telefono());
        Hijo h = new Hijo();
        h.setCreador(usuario);
        h.setVinculoPareja(vinculoAceptado);
        h.setNombre(r.nombre().trim());
        h.setMayorDeEdad(r.mayorDeEdad());
        h.setTelefono(telefono);
        h.setVisible(r.visible());
        hijos.save(h);
        if (telefono != null) {
            altaTelefonoAutorizado(telefono, usuario);
        }
    }

    private void editar(Usuario usuario, Hijo h, HijoRequest r) {
        h.setVisible(r.visible());
        if (h.getUsuario() != null) {
            // Hijo registrado: solo se le puede tocar 'visible' desde el editor del padre.
            hijos.save(h);
            return;
        }
        h.setNombre(r.nombre().trim());
        h.setMayorDeEdad(r.mayorDeEdad());

        String nuevoTelefono = normalizarOpcional(r.telefono());
        String viejoTelefono = h.getTelefono();
        h.setTelefono(nuevoTelefono);
        hijos.save(h);

        if (!Objects.equals(nuevoTelefono, viejoTelefono)) {
            hijos.flush();
            if (viejoTelefono != null) {
                podarTelefono(viejoTelefono);
            }
            if (nuevoTelefono != null) {
                altaTelefonoAutorizado(nuevoTelefono, usuario);
            }
        }
    }

    private String normalizarOpcional(String entrada) {
        if (!StringUtils.hasText(entrada)) {
            return null;
        }
        return NormalizadorTelefono.normalizar(entrada).orElseThrow(TelefonoHijoInvalidoException::new);
    }

    private void reparentar(Long creadorId, VinculoPareja vinculo) {
        for (Hijo h : hijos.findByCreadorId(creadorId)) {
            if (h.getVinculoPareja() == null) {
                h.setVinculoPareja(vinculo);
                hijos.save(h);
            }
        }
    }

    private void avisarPadres(Hijo h) {
        Set<Long> padres = new LinkedHashSet<>();
        padres.add(h.getCreador().getId());
        VinculoPareja v = h.getVinculoPareja();
        if (v != null && v.getEstado() == EstadoVinculo.ACEPTADO) {
            padres.add(v.getSolicitante().getId());
            if (v.getParejaUsuario() != null) {
                padres.add(v.getParejaUsuario().getId());
            }
        }
        padres.remove(h.getUsuario().getId());
        for (Long padreId : padres) {
            eventos.publishEvent(new AvisoPushEvent(new Audiencia.UsuarioUnico(padreId),
                    TITULO_PUSH_HIJO_REGISTRADO, h.getNombre() + " ya tiene cuenta en la peña"));
        }
    }

    private Optional<VinculoPareja> vinculoAceptadoDe(Long usuarioId) {
        return vinculos.findBySolicitanteIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO)
                .filter(v -> v.getEstado() == EstadoVinculo.ACEPTADO)
                .or(() -> vinculos.findByParejaUsuarioIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO)
                        .filter(v -> v.getEstado() == EstadoVinculo.ACEPTADO));
    }

    private void altaTelefonoAutorizado(String tel, Usuario autorizadoPor) {
        // telefono_autorizado.telefono SÍ es único: findByTelefono (Optional) es seguro.
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
     * Al quitar (o cambiar) el teléfono de un hijo: si la fila de
     * {@code telefono_autorizado} sigue sin usar y ya no la referencia ningún otro
     * hijo ni ningún vínculo vivo, se borra. Misma salvaguarda que
     * {@code VinculoParejaService.podarTelefono}.
     */
    private void podarTelefono(String tel) {
        telefonosAutorizados.findByTelefono(tel).ifPresent(ta -> {
            if (ta.isUsado()) {
                return;
            }
            boolean referenciado =
                    !hijos.findAllByTelefono(tel).isEmpty()
                            || !vinculos.findAllByParejaTelefonoAndEstadoNot(tel, EstadoVinculo.RECHAZADO).isEmpty();
            if (!referenciado) {
                telefonosAutorizados.delete(ta);
            }
        });
    }

    private Pena pena() {
        return penas.findBySlug(SLUG_PENA).orElseThrow();
    }
}
