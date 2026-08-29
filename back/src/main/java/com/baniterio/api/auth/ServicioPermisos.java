package com.baniterio.api.auth;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.PermisoAreaRepository;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Punto único de verdad de "¿este usuario puede acceder a esta área del panel?".
 *
 * <p>Regla: {@code es_superadmin} o rol {@code ADMIN} activo en la peña → todas
 * las áreas. Cualquier otro → solo las que tenga concedidas en
 * {@code permiso_area}. Los controladores de {@code admin/} preguntan aquí, no
 * se fían de lo que diga el cliente.
 *
 * <p>Un usuario desactivado ({@code usuario.activo = false}) no puede nada:
 * aunque su JWT siga siendo válido durante 7 días, {@link #puede},
 * {@link #areasDe}, {@link #rolDe} y {@link #esAdministrador} le responden como
 * si no tuviera ningún acceso. Así, desactivar a alguien le corta el acceso al
 * panel de inmediato sin esperar a que caduque su token.
 */
@Service
public class ServicioPermisos {

    private static final String SLUG_PENA = "baniterio";

    private final MembresiaRepository membresias;
    private final PermisoAreaRepository permisos;
    private final PenaRepository penas;
    private final UsuarioRepository usuarios;

    public ServicioPermisos(MembresiaRepository membresias, PermisoAreaRepository permisos,
                            PenaRepository penas, UsuarioRepository usuarios) {
        this.membresias = membresias;
        this.permisos = permisos;
        this.penas = penas;
        this.usuarios = usuarios;
    }

    /** El usuario existe y está activo. Un usuario desactivado no puede nada. */
    private Optional<Usuario> usuarioActivo(Long usuarioId) {
        return usuarios.findById(usuarioId).filter(u -> u.isActivo());
    }

    @Transactional(readOnly = true)
    public boolean esAdministrador(Long usuarioId) {
        Optional<Usuario> usuario = usuarioActivo(usuarioId);
        if (usuario.isEmpty()) {
            return false;
        }
        // Un superadmin sin membresía sigue siendo administrador (red de seguridad).
        return usuario.get().isEsSuperadmin() || rolDe(usuarioId) == RolMembresia.ADMIN;
    }

    @Transactional(readOnly = true)
    public RolMembresia rolDe(Long usuarioId) {
        if (usuarioActivo(usuarioId).isEmpty()) {
            return null;
        }
        Long penaId = penaId();
        return membresias.findByUsuarioIdAndPenaId(usuarioId, penaId)
                .filter(m -> m.isActiva())
                .map(m -> m.getRol())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean puede(Long usuarioId, AreaProtegida area) {
        if (usuarioActivo(usuarioId).isEmpty()) {
            return false;
        }
        return esAdministrador(usuarioId)
                || permisos.existsByUsuarioIdAndArea(usuarioId, area);
    }

    @Transactional(readOnly = true)
    public Set<AreaProtegida> areasDe(Long usuarioId) {
        if (usuarioActivo(usuarioId).isEmpty()) {
            return Set.of();
        }
        if (esAdministrador(usuarioId)) {
            return Collections.unmodifiableSet(EnumSet.allOf(AreaProtegida.class));
        }
        Set<AreaProtegida> resultado = EnumSet.noneOf(AreaProtegida.class);
        permisos.findByUsuarioId(usuarioId).forEach(p -> resultado.add(p.getArea()));
        return Collections.unmodifiableSet(resultado);
    }

    /** Id de la peña piloto. Si falta la siembra (V6), es un fallo de arranque legítimo (500). */
    private Long penaId() {
        return penas.findBySlug(SLUG_PENA)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta la peña piloto '" + SLUG_PENA + "'"))
                .getId();
    }
}
