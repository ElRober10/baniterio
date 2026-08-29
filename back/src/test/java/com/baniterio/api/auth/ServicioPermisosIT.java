package com.baniterio.api.auth;

import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.PermisoArea;
import com.baniterio.api.identidad.PermisoAreaRepository;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integración de {@link ServicioPermisos}: comprueba, contra la app y el
 * Postgres reales, quién puede acceder a cada área del panel.
 *
 * <p>Casos: superadmin y admin (no superadmin) ven todas las áreas; un miembro
 * sin concesiones no ve ninguna; un miembro con una concesión ve solo esa; y
 * {@code rolDe} devuelve {@code null} si el usuario no tiene membresía en la peña.
 *
 * <p>Cada usuario se crea con {@code telefono} y {@code email} únicos (ambos
 * tienen restricción UNIQUE en {@code usuario}), así los tests no se pisan.
 */
class ServicioPermisosIT extends IntegrationTest {

    @Autowired
    ServicioPermisos servicioPermisos;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    PermisoAreaRepository permisos;

    @Autowired
    PenaRepository penas;

    @Autowired
    PasswordEncoder passwordEncoder;

    /** Número con formato válido ({@code ^[67]\d{8}$}) y único en {@code usuario}. */
    private String tel() {
        String numero;
        do {
            numero = (ThreadLocalRandom.current().nextBoolean() ? "6" : "7")
                    + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        } while (usuarios.existsByTelefono(numero));
        return numero;
    }

    /** Crea un usuario activo (superadmin param) con membresía activa (rol param) en la peña piloto. */
    private Long crearUsuario(boolean esSuperadmin, RolMembresia rol) {
        String telefono = tel();
        Usuario usuario = usuarios.save(Usuario.builder()
                .telefono(telefono)
                .email(telefono + "@baniterio.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N")
                .apellidos("A")
                .esSuperadmin(esSuperadmin)
                .activo(true)
                .build());

        Pena pena = penas.findBySlug("baniterio").orElseThrow();
        membresias.save(Membresia.builder()
                .usuario(usuario)
                .pena(pena)
                .rol(rol)
                .activa(true)
                .build());

        return usuario.getId();
    }

    @Test
    void superadmin_puede_todas_las_areas() {
        Long id = crearUsuario(true, RolMembresia.ADMIN);
        assertThat(servicioPermisos.puede(id, AreaProtegida.ADMIN_SOLICITUDES)).isTrue();
        assertThat(servicioPermisos.areasDe(id))
                .containsExactlyInAnyOrder(AreaProtegida.values());
        assertThat(servicioPermisos.esAdministrador(id)).isTrue();
    }

    @Test
    void admin_no_superadmin_puede_todas_las_areas() {
        Long id = crearUsuario(false, RolMembresia.ADMIN);
        assertThat(servicioPermisos.areasDe(id)).containsExactlyInAnyOrder(AreaProtegida.values());
    }

    @Test
    void miembro_sin_concesiones_no_puede_ninguna() {
        Long id = crearUsuario(false, RolMembresia.MIEMBRO);
        assertThat(servicioPermisos.puede(id, AreaProtegida.ADMIN_PERMISOS)).isFalse();
        assertThat(servicioPermisos.areasDe(id)).isEmpty();
        assertThat(servicioPermisos.esAdministrador(id)).isFalse();
    }

    @Test
    void miembro_con_una_concesion_puede_solo_esa() {
        Long id = crearUsuario(false, RolMembresia.MIEMBRO);
        permisos.save(PermisoArea.builder()
                .usuario(usuarios.findById(id).orElseThrow())
                .area(AreaProtegida.ADMIN_SOLICITUDES)
                .build());
        assertThat(servicioPermisos.puede(id, AreaProtegida.ADMIN_SOLICITUDES)).isTrue();
        assertThat(servicioPermisos.puede(id, AreaProtegida.ADMIN_PERMISOS)).isFalse();
        assertThat(servicioPermisos.areasDe(id)).containsExactly(AreaProtegida.ADMIN_SOLICITUDES);
    }

    @Test
    void rolDe_es_null_si_no_es_miembro() {
        Usuario u = usuarios.save(Usuario.builder().telefono(tel()).email(tel() + "@x.com")
                .passwordHash("x").nombre("N").apellidos("A").esSuperadmin(false).activo(true).build());
        assertThat(servicioPermisos.rolDe(u.getId())).isNull();
    }
}
