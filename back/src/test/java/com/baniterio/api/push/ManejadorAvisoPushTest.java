package com.baniterio.api.push;

import java.util.List;

import com.baniterio.api.identidad.Dispositivo;
import com.baniterio.api.identidad.DispositivoRepository;
import com.baniterio.api.identidad.PlataformaDispositivo;
import com.baniterio.api.identidad.Usuario;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManejadorAvisoPushTest {

    private final ResolutorAudiencia resolutor = mock(ResolutorAudiencia.class);
    private final DispositivoRepository dispositivos = mock(DispositivoRepository.class);
    private final ServicioPush servicioPush = mock(ServicioPush.class);
    private final DispositivoService dispositivoService = mock(DispositivoService.class);
    private final ManejadorAvisoPush manejador =
            new ManejadorAvisoPush(resolutor, dispositivos, servicioPush, dispositivoService);

    private Dispositivo disp(String token) {
        return Dispositivo.builder().usuario(Usuario.builder().id(1L).build())
                .token(token).plataforma(PlataformaDispositivo.ANDROID).build();
    }

    private AvisoPushEvent evento() {
        return new AvisoPushEvent(new Audiencia.Administradores(), "T", "C");
    }

    @Test
    void envia_a_los_tokens_de_la_audiencia() {
        when(resolutor.resolver(any())).thenReturn(List.of(1L, 2L));
        when(dispositivos.findByUsuarioIdIn(List.of(1L, 2L))).thenReturn(List.of(disp("a"), disp("b")));
        when(servicioPush.enviar(any(), eq("T"), eq("C"))).thenReturn(List.of());

        manejador.alAviso(evento());

        verify(servicioPush).enviar(List.of("a", "b"), "T", "C");
        verify(dispositivoService, never()).podar(any());
    }

    @Test
    void poda_los_tokens_que_el_envio_marca_muertos() {
        when(resolutor.resolver(any())).thenReturn(List.of(1L));
        when(dispositivos.findByUsuarioIdIn(any())).thenReturn(List.of(disp("a"), disp("b")));
        when(servicioPush.enviar(any(), any(), any())).thenReturn(List.of("b"));

        manejador.alAviso(evento());

        verify(dispositivoService).podar(List.of("b"));
    }

    @Test
    void sin_dispositivos_no_llama_al_envio() {
        when(resolutor.resolver(any())).thenReturn(List.of(1L));
        when(dispositivos.findByUsuarioIdIn(any())).thenReturn(List.of());

        manejador.alAviso(evento());

        verify(servicioPush, never()).enviar(any(), any(), any());
    }

    @Test
    void un_fallo_resolviendo_no_propaga() {
        when(resolutor.resolver(any())).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> manejador.alAviso(evento())).doesNotThrowAnyException();

        verify(servicioPush, never()).enviar(any(), any(), any());
    }
}
