package com.baniterio.api.perfil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.baniterio.api.identidad.EstadoVinculo;
import com.baniterio.api.identidad.Hijo;
import com.baniterio.api.identidad.HijoRepository;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.PenaPilotoService;
import com.baniterio.api.identidad.Perfil;
import com.baniterio.api.identidad.PerfilRepository;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.VinculoPareja;
import com.baniterio.api.identidad.VinculoParejaRepository;
import com.baniterio.api.perfil.OrdenadorTarjetas.Candidato;
import com.baniterio.api.perfil.OrdenadorTarjetas.ContextoOrden;
import com.baniterio.api.perfil.dto.TarjetaMiembroResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Construye la lista de tarjetas de {@code GET /api/v1/miembros} para el usuario
 * autenticado: todos los miembros de la peña con el perfil completado, mapeados a
 * {@link TarjetaMiembroResponse} (sin teléfono ni email) y ordenados con
 * {@link OrdenadorTarjetas} (yo → mi pareja → mis hijos registrados → el resto).
 *
 * <p>Todo dentro de una transacción de solo lectura: la app corre con
 * {@code open-in-view: false}, así que las entidades se mapean aquí.
 */
@Service
public class MiembrosService {

    private final PenaPilotoService pena;
    private final MembresiaRepository membresias;
    private final PerfilRepository perfiles;
    private final VinculoParejaRepository vinculos;
    private final HijoRepository hijos;

    public MiembrosService(PenaPilotoService pena, MembresiaRepository membresias,
            PerfilRepository perfiles, VinculoParejaRepository vinculos, HijoRepository hijos) {
        this.pena = pena;
        this.membresias = membresias;
        this.perfiles = perfiles;
        this.vinculos = vinculos;
        this.hijos = hijos;
    }

    @Transactional(readOnly = true)
    public List<TarjetaMiembroResponse> tarjetas(Long usuarioId) {
        Long penaId = pena.id();

        ContextoOrden ctx = new ContextoOrden(parejaUsuarioId(usuarioId), hijosRegistradosIds(usuarioId));

        Map<Long, TarjetaMiembroResponse> porUsuario = new HashMap<>();
        List<Candidato> candidatos = new ArrayList<>();
        for (Membresia m : membresias.findByPenaIdAndActivaTrue(penaId)) {
            Usuario u = m.getUsuario();
            if (!u.isActivo()) {
                continue;
            }
            Optional<Perfil> perfil = perfiles.findByUsuarioId(u.getId());
            if (perfil.map(Perfil::isCompletado).orElse(false) && !porUsuario.containsKey(u.getId())) {
                porUsuario.put(u.getId(), tarjeta(u, perfil.get()));
                candidatos.add(new Candidato(u.getId(), u.getNombre(), u.getApellidos()));
            }
        }

        return OrdenadorTarjetas.ordenar(usuarioId, candidatos, ctx).stream()
                .map(c -> porUsuario.get(c.id()))
                .toList();
    }

    private TarjetaMiembroResponse tarjeta(Usuario u, Perfil perfil) {
        return new TarjetaMiembroResponse(
                u.getId(),
                u.getNombre(),
                u.getApellidos(),
                u.getMote(),
                StringUtils.hasText(perfil.getSobreMi()) ? perfil.getSobreMi() : null,
                imagenUrl(perfil),
                parejaNombreDe(u.getId()),
                hijosVisiblesDe(u.getId()));
    }

    /** Copia exacta de {@code PerfilService.imagenUrl}. */
    private String imagenUrl(Perfil perfil) {
        return switch (perfil.getImagenTipo()) {
            case AVATAR -> "/api/v1/media/avatares/" + perfil.getImagenRef() + ".png";
            case FOTO -> "/api/v1/media/fotos/" + perfil.getImagenRef();
        };
    }

    /** El otro lado de mi vínculo {@code ACEPTADO} con cuenta; {@code null} si no hay. */
    private Long parejaUsuarioId(Long usuarioId) {
        return comoSolicitante(usuarioId)
                .filter(v -> v.getEstado() == EstadoVinculo.ACEPTADO)
                .map(v -> v.getParejaUsuario() != null ? v.getParejaUsuario().getId() : null)
                .or(() -> comoParejaAceptada(usuarioId).map(v -> v.getSolicitante().getId()))
                .orElse(null);
    }

    /**
     * El nombre de la pareja para la tarjeta pública de este miembro. Solo si el
     * vínculo está confirmado en la práctica: {@code SIN_CUENTA} (la pareja no
     * tiene cuenta, se asume cierto — así lo indica el spec) o {@code ACEPTADO}.
     * Un {@code PENDIENTE} (declarado pero aún sin confirmar por la otra persona)
     * NO se difunde. Si este miembro es el lado que aceptó, el nombre real del
     * solicitante (igual que {@code PerfilService}).
     */
    private String parejaNombreDe(Long usuarioId) {
        return comoSolicitante(usuarioId)
                .filter(v -> v.getEstado() == EstadoVinculo.SIN_CUENTA
                        || v.getEstado() == EstadoVinculo.ACEPTADO)
                .map(VinculoPareja::getParejaNombre)
                .or(() -> comoParejaAceptada(usuarioId).map(v -> v.getSolicitante().getNombre()))
                .orElse(null);
    }

    /**
     * Ids de usuario de mis hijos ya registrados: los {@code hijo} que creé yo (o
     * que cuelgan de mi vínculo aceptado) con {@code usuario_id} no nulo.
     */
    private Set<Long> hijosRegistradosIds(Long usuarioId) {
        Set<Long> ids = new HashSet<>();
        for (Hijo h : misHijos(usuarioId).values()) {
            if (h.getUsuario() != null) {
                ids.add(h.getUsuario().getId());
            }
        }
        return ids;
    }

    /** Nombres de los hijos visibles y sin cuenta de este miembro (y de su vínculo aceptado). */
    private List<String> hijosVisiblesDe(Long usuarioId) {
        List<String> nombres = new ArrayList<>();
        for (Hijo h : misHijos(usuarioId).values()) {
            if (h.isVisible() && h.getUsuario() == null) {
                nombres.add(h.getNombre());
            }
        }
        return nombres;
    }

    /**
     * Los hijos que este usuario ve: los que creó y —si tiene vínculo
     * {@code ACEPTADO}— los que cuelgan de ese vínculo. Sin duplicar por id.
     * Mismo criterio que {@code PerfilService.misHijos}.
     */
    private Map<Long, Hijo> misHijos(Long usuarioId) {
        Map<Long, Hijo> porId = new LinkedHashMap<>();
        for (Hijo h : hijos.findByCreadorId(usuarioId)) {
            porId.put(h.getId(), h);
        }
        vinculoAceptadoId(usuarioId).ifPresent(vinculoId -> {
            for (Hijo h : hijos.findByVinculoParejaId(vinculoId)) {
                porId.putIfAbsent(h.getId(), h);
            }
        });
        return porId;
    }

    private Optional<Long> vinculoAceptadoId(Long usuarioId) {
        return comoSolicitante(usuarioId)
                .filter(v -> v.getEstado() == EstadoVinculo.ACEPTADO)
                .or(() -> comoParejaAceptada(usuarioId))
                .map(VinculoPareja::getId);
    }

    private Optional<VinculoPareja> comoSolicitante(Long usuarioId) {
        return vinculos.findBySolicitanteIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO);
    }

    private Optional<VinculoPareja> comoParejaAceptada(Long usuarioId) {
        return vinculos.findByParejaUsuarioIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO)
                .filter(v -> v.getEstado() == EstadoVinculo.ACEPTADO);
    }
}
