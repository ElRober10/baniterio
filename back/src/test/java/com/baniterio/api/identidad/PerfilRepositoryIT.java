package com.baniterio.api.identidad;

import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba que las entidades nuevas (perfil, vínculo, hijo) mapean contra el
 * esquema real que crea Flyway (si no cuadraran, el contexto ni arrancaría por
 * ddl-auto: validate) y que los repos derivan bien sus consultas.
 *
 * <p>La BBDD de Testcontainers se comparte entre todos los {@code *IT} sin
 * rollback, así que cada test siembra teléfonos aleatorios únicos para no chocar
 * con otros ITs.
 */
class PerfilRepositoryIT extends IntegrationTest {

    @Autowired UsuarioRepository usuarios;
    @Autowired PerfilRepository perfiles;
    @Autowired VinculoParejaRepository vinculos;
    @Autowired HijoRepository hijos;

    /** Teléfono aleatorio de 9 dígitos, único por ejecución. */
    private static String tel() {
        return (ThreadLocalRandom.current().nextBoolean() ? "6" : "7")
                + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    private Usuario nuevoUsuario(String tel) {
        return usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@repo.test").passwordHash("x")
                .nombre("N").apellidos("A").esSuperadmin(false).activo(true).build());
    }

    @Test
    void guarda_y_recupera_perfil_por_usuario() {
        Usuario u = nuevoUsuario(tel());
        perfiles.save(Perfil.builder()
                .usuario(u).imagenTipo(ImagenPerfil.AVATAR).imagenRef("01_chico")
                .completado(true).build());

        assertThat(perfiles.findByUsuarioId(u.getId()))
                .get().extracting(Perfil::isCompletado, Perfil::getImagenRef)
                .containsExactly(true, "01_chico");
    }

    @Test
    void el_vinculo_vivo_se_encuentra_por_solicitante_y_por_pareja() {
        Usuario a = nuevoUsuario(tel());
        Usuario b = nuevoUsuario(tel());
        vinculos.save(VinculoPareja.builder()
                .solicitante(a).parejaUsuario(b).parejaNombre("B").parejaTelefono(b.getTelefono())
                .estado(EstadoVinculo.PENDIENTE).build());

        assertThat(vinculos.findBySolicitanteIdAndEstadoNot(a.getId(), EstadoVinculo.RECHAZADO)).isPresent();
        assertThat(vinculos.findByParejaUsuarioIdAndEstadoNot(b.getId(), EstadoVinculo.RECHAZADO)).isPresent();
    }

    @Test
    void el_indice_parcial_impide_dos_vinculos_vivos_del_mismo_solicitante() {
        Usuario a = nuevoUsuario(tel());
        vinculos.save(VinculoPareja.builder().solicitante(a).parejaNombre("X").parejaTelefono(tel())
                .estado(EstadoVinculo.SIN_CUENTA).build());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                vinculos.saveAndFlush(VinculoPareja.builder().solicitante(a).parejaNombre("Y")
                        .parejaTelefono(tel()).estado(EstadoVinculo.SIN_CUENTA).build()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void hijo_por_creador_y_por_telefono() {
        Usuario a = nuevoUsuario(tel());
        String telHijo = tel();
        hijos.save(Hijo.builder().creador(a).nombre("Peque").telefono(telHijo).visible(false).build());

        assertThat(hijos.findByCreadorId(a.getId())).hasSize(1);
        assertThat(hijos.findByTelefono(telHijo)).isPresent();
    }
}
