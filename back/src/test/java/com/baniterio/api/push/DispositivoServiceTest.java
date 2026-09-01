package com.baniterio.api.push;

import java.util.Optional;

import com.baniterio.api.identidad.Dispositivo;
import com.baniterio.api.identidad.DispositivoRepository;
import com.baniterio.api.identidad.PlataformaDispositivo;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispositivoServiceTest {

    private final DispositivoRepository dispositivos = mock(DispositivoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final DispositivoService servicio = new DispositivoService(dispositivos, usuarios);

    @Test
    void registrar_inserta_si_el_token_es_nuevo() {
        when(dispositivos.findByToken("tk")).thenReturn(Optional.empty());
        when(usuarios.findById(5L)).thenReturn(Optional.of(Usuario.builder().id(5L).build()));

        servicio.registrar(5L, "tk", PlataformaDispositivo.ANDROID);

        verify(dispositivos).save(any(Dispositivo.class));
    }

    @Test
    void registrar_reasigna_usuario_si_el_token_ya_existia() {
        Dispositivo existente = Dispositivo.builder()
                .id(1L).usuario(Usuario.builder().id(9L).build())
                .token("tk").plataforma(PlataformaDispositivo.ANDROID).build();
        when(dispositivos.findByToken("tk")).thenReturn(Optional.of(existente));
        when(usuarios.findById(5L)).thenReturn(Optional.of(Usuario.builder().id(5L).build()));

        servicio.registrar(5L, "tk", PlataformaDispositivo.IOS);

        assertThat(existente.getUsuario().getId()).isEqualTo(5L);
        assertThat(existente.getPlataforma()).isEqualTo(PlataformaDispositivo.IOS);
        verify(dispositivos).save(existente);
    }

    @Test
    void darDeBaja_no_borra_el_dispositivo_de_otro() {
        Dispositivo ajeno = Dispositivo.builder()
                .usuario(Usuario.builder().id(9L).build()).token("tk").build();
        when(dispositivos.findByToken("tk")).thenReturn(Optional.of(ajeno));

        servicio.darDeBaja(5L, "tk");

        verify(dispositivos, never()).deleteByToken(any());
    }
}
