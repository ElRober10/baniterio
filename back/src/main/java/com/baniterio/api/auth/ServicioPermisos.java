package com.baniterio.api.auth;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.PenaPilotoService;
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

    private final MembresiaRepository membresias;
    private final PermisoAreaRepository permisos;
    private final PenaPilotoService pena;
    private final UsuarioRepository usuarios;

    public ServicioPermisos(MembresiaRepository membresias, PermisoAreaRepository permisos,
                            PenaPilotoService pena, UsuarioRepository usuarios) {
        this.membresias = membresias;
        this.permisos = permisos;
        this.pena = pena;
        this.usuarios = usuarios;
    }

    /**
     * Toda la lógica vive en estos privados, que se llaman entre sí directamente
     * (nunca a través de {@code this} hacia un método público): un privado no pasa
     * por el proxy de Spring, así que llamarlo desde otro método de la clase no
     * pierde la demarcación transaccional como sí pasaría con dos públicos
     * {@code @Transactional} llamándose entre sí. Los públicos de abajo son solo
     * la puerta de entrada transaccional.
     */
    private Optional<Usuario> usuarioActivo(Long usuarioId) {
        return usuarios.findById(usuarioId).filter(u -> u.isActivo());
    }

    private RolMembresia rolInterno(Long usuarioId) {
        return membresias.findByUsuarioIdAndPenaId(usuarioId, penaId())
                .filter(m -> m.isActiva())
                .map(m -> m.getRol())
                .orElse(null);
    }

    private boolean esAdministradorInterno(Long usuarioId) {
        return usuarioActivo(usuarioId)
                // Un superadmin sin membresía sigue siendo administrador (red de seguridad).
                .map(u -> u.isEsSuperadmin() || rolInterno(usuarioId) == RolMembresia.ADMIN)
                .orElse(false);
    }

    private boolean puedeInterno(Long usuarioId, AreaProtegida area) {
        if (usuarioActivo(usuarioId).isEmpty()) {
            return false;
        }
        return esAdministradorInterno(usuarioId) || permisos.existsByUsuarioIdAndArea(usuarioId, area);
    }

    @Transactional(readOnly = true)
    public boolean esAdministrador(Long usuarioId) {
        return esAdministradorInterno(usuarioId);
    }

    @Transactional(readOnly = true)
    public RolMembresia rolDe(Long usuarioId) {
        if (usuarioActivo(usuarioId).isEmpty()) {
            return null;
        }
        return rolInterno(usuarioId);
    }

    @Transactional(readOnly = true)
    public boolean puede(Long usuarioId, AreaProtegida area) {
        return puedeInterno(usuarioId, area);
    }

    @Transactional(readOnly = true)
    public Set<AreaProtegida> areasDe(Long usuarioId) {
        if (usuarioActivo(usuarioId).isEmpty()) {
            return Set.of();
        }
        if (esAdministradorInterno(usuarioId)) {
            return Collections.unmodifiableSet(EnumSet.allOf(AreaProtegida.class));
        }
        Set<AreaProtegida> resultado = EnumSet.noneOf(AreaProtegida.class);
        permisos.findByUsuarioId(usuarioId).forEach(p -> resultado.add(p.getArea()));
        return Collections.unmodifiableSet(resultado);
    }

    /**
     * Exige que {@code usuarioId} tenga {@code area}, o lanza la excepción que dé
     * {@code excepcion}. Centraliza el patrón repetido {@code if (!puede(...)) throw ...}
     * sin tocar los tipos de excepción por módulo (los clientes pueden leer el código de error).
     */
    @Transactional(readOnly = true)
    public void exigir(Long usuarioId, AreaProtegida area, Supplier<? extends RuntimeException> excepcion) {
        if (!puedeInterno(usuarioId, area)) {
            throw excepcion.get();
        }
    }

    /** Igual que {@link #exigir}, pero para las operaciones que exigen ser administrador. */
    @Transactional(readOnly = true)
    public void exigirAdmin(Long usuarioId, Supplier<? extends RuntimeException> excepcion) {
        if (!esAdministradorInterno(usuarioId)) {
            throw excepcion.get();
        }
    }

    private Long penaId() {
        return pena.id();
    }
}
