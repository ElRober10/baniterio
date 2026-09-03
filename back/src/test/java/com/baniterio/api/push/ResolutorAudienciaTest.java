package com.baniterio.api.push;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.baniterio.api.identidad.AsistenciaEventoRepository;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResolutorAudienciaTest {

    private final MembresiaRepository membresias = mock(MembresiaRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PenaRepository penas = mock(PenaRepository.class);
    private final AsistenciaEventoRepository asistencias = mock(AsistenciaEventoRepository.class);
    private final ResolutorAudiencia resolutor =
            new ResolutorAudiencia(membresias, usuarios, penas, asistencias);

    {
        when(penas.findBySlug("baniterio")).thenReturn(Optional.of(Pena.builder().id(1L).build()));
        // Por defecto no hay superadmins sueltos; los tests que lo necesiten lo re-stubean.
        when(usuarios.findAll()).thenReturn(List.of());
    }

    private Membresia membresia(long usuarioId, RolMembresia rol, boolean superadmin) {
        return Membresia.builder()
                .usuario(Usuario.builder().id(usuarioId).esSuperadmin(superadmin).build())
                .rol(rol).activa(true).build();
    }

    private Usuario usuario(long id, boolean superadmin) {
        return Usuario.builder().id(id).esSuperadmin(superadmin).build();
    }

    @Test
    void usuario_unico_devuelve_ese_id() {
        assertThat(resolutor.resolver(new Audiencia.UsuarioUnico(42L))).containsExactly(42L);
    }

    @Test
    void toda_la_pena_devuelve_los_miembros_activos() {
        when(membresias.findByPenaIdAndActivaTrue(1L)).thenReturn(List.of(
                membresia(1L, RolMembresia.MIEMBRO, false),
                membresia(2L, RolMembresia.ADMIN, false)));

        assertThat(resolutor.resolver(new Audiencia.TodaLaPena())).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void administradores_une_admins_y_superadmins_sin_duplicar() {
        when(membresias.findByPenaIdAndActivaTrue(1L)).thenReturn(List.of(
                membresia(1L, RolMembresia.MIEMBRO, false),   // fuera
                membresia(2L, RolMembresia.ADMIN, false),      // dentro (rol)
                membresia(3L, RolMembresia.MIEMBRO, true),     // dentro (superadmin)
                membresia(4L, RolMembresia.ADMIN, true)));     // dentro, sin duplicar

        assertThat(resolutor.resolver(new Audiencia.Administradores()))
                .containsExactlyInAnyOrder(2L, 3L, 4L);
    }

    @Test
    void sin_respuesta_evento_excluye_a_quien_ya_respondio() {
        when(membresias.findByPenaIdAndActivaTrue(1L)).thenReturn(List.of(
                membresia(1L, RolMembresia.MIEMBRO, false),
                membresia(2L, RolMembresia.MIEMBRO, false),
                membresia(3L, RolMembresia.ADMIN, false)));
        when(asistencias.idsUsuariosConRespuesta(50L)).thenReturn(Set.of(2L));

        assertThat(resolutor.resolver(new Audiencia.SinRespuestaEvento(50L)))
                .containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void superadmin_sin_membresia_activa_tambien_recibe() {
        when(membresias.findByPenaIdAndActivaTrue(1L)).thenReturn(List.of(
                membresia(2L, RolMembresia.ADMIN, false)));
        when(usuarios.findAll()).thenReturn(List.of(
                usuario(9L, true),      // superadmin sin membresía → dentro
                usuario(2L, false),     // ya está por rol → sin duplicar
                usuario(7L, false)));    // ni admin ni superadmin → fuera

        assertThat(resolutor.resolver(new Audiencia.Administradores()))
                .containsExactlyInAnyOrder(2L, 9L);
    }
}
