package com.baniterio.api.auth;

import java.util.EnumSet;
import java.util.Set;

import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.PermisoAreaRepository;
import com.baniterio.api.identidad.RolMembresia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Punto único de verdad de "¿este usuario puede acceder a esta área del panel?".
 *
 * <p>Regla: {@code es_superadmin} o rol {@code ADMIN} activo en la peña → todas
 * las áreas. Cualquier otro → solo las que tenga concedidas en
 * {@code permiso_area}. Los controladores de {@code admin/} preguntan aquí, no
 * se fían de lo que diga el cliente.
 */
@Service
public class ServicioPermisos {

    private static final String SLUG_PENA = "baniterio";

    private final MembresiaRepository membresias;
    private final PermisoAreaRepository permisos;
    private final PenaRepository penas;

    public ServicioPermisos(MembresiaRepository membresias, PermisoAreaRepository permisos,
                            PenaRepository penas) {
        this.membresias = membresias;
        this.permisos = permisos;
        this.penas = penas;
    }

    @Transactional(readOnly = true)
    public boolean esAdministrador(Long usuarioId) {
        return rolDe(usuarioId) == RolMembresia.ADMIN;
    }
    // OJO: un superadmin del proyecto SIEMPRE se registra con membresía ADMIN
    // (ver AuthService), así que basta con mirar el rol. Si en el futuro pudiera
    // haber un superadmin sin membresía, añadir aquí la comprobación de
    // usuario.esSuperadmin.

    @Transactional(readOnly = true)
    public RolMembresia rolDe(Long usuarioId) {
        Long penaId = penas.findBySlug(SLUG_PENA).orElseThrow().getId();
        return membresias.findByUsuarioIdAndPenaId(usuarioId, penaId)
                .filter(m -> m.isActiva())
                .map(m -> m.getRol())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean puede(Long usuarioId, AreaProtegida area) {
        return esAdministrador(usuarioId)
                || permisos.existsByUsuarioIdAndArea(usuarioId, area);
    }

    @Transactional(readOnly = true)
    public Set<AreaProtegida> areasDe(Long usuarioId) {
        if (esAdministrador(usuarioId)) {
            return EnumSet.allOf(AreaProtegida.class);
        }
        Set<AreaProtegida> resultado = EnumSet.noneOf(AreaProtegida.class);
        permisos.findByUsuarioId(usuarioId).forEach(p -> resultado.add(p.getArea()));
        return resultado;
    }
}
