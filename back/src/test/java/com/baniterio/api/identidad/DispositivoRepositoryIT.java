package com.baniterio.api.identidad;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class DispositivoRepositoryIT extends IntegrationTest {

    @Autowired
    DispositivoRepository dispositivos;

    @Autowired
    UsuarioRepository usuarios;

    private Usuario nuevoUsuario() {
        String t = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        return usuarios.save(Usuario.builder()
                .telefono(t).email(t + "@disp.test").passwordHash("x")
                .nombre("D").apellidos("T").esSuperadmin(false).activo(true).build());
    }

    @Test
    void guarda_y_busca_por_token() {
        Usuario u = nuevoUsuario();
        dispositivos.save(Dispositivo.builder()
                .usuario(u).token("tok-" + u.getId()).plataforma(PlataformaDispositivo.ANDROID).build());

        assertThat(dispositivos.findByToken("tok-" + u.getId())).isPresent();
        assertThat(dispositivos.findByUsuarioIdIn(List.of(u.getId()))).hasSize(1);
    }

    @Test
    void borra_por_token_en_lote() {
        Usuario u = nuevoUsuario();
        dispositivos.save(Dispositivo.builder()
                .usuario(u).token("a-" + u.getId()).plataforma(PlataformaDispositivo.ANDROID).build());
        dispositivos.save(Dispositivo.builder()
                .usuario(u).token("b-" + u.getId()).plataforma(PlataformaDispositivo.IOS).build());

        dispositivos.deleteByTokenIn(List.of("a-" + u.getId(), "b-" + u.getId()));

        assertThat(dispositivos.findByUsuarioIdIn(List.of(u.getId()))).isEmpty();
    }
}
