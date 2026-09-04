package com.baniterio.api.evento;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.AsistenciaEventoRepository;
import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.Dispositivo;
import com.baniterio.api.identidad.DispositivoRepository;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.NotificacionEvento;
import com.baniterio.api.identidad.NotificacionEventoRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.PlataformaDispositivo;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.push.ServicioPush;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * A quién le llega el push de convocatoria de un evento.
 *
 * <p>Regla: el <strong>primer</strong> envío va a toda la peña —incluido quien
 * lo manda y quien ya haya respondido—; los <strong>reenvíos</strong> van solo a
 * quien todavía no ha contestado.
 *
 * <p>{@link ServicioPush} va mockeado para espiar los tokens sin tocar Firebase.
 * El manejador es {@code AFTER_COMMIT}, así que se usa Awaitility.
 */
class NotificacionEventoAudienciaIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    PenaRepository penas;

    @Autowired
    EventoRepository eventos;

    @Autowired
    CuentaRepository cuentas;

    @Autowired
    AsistenciaEventoRepository asistencias;

    @Autowired
    NotificacionEventoRepository notificaciones;

    @Autowired
    DispositivoRepository dispositivos;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    ServicioPush servicioPush;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        when(servicioPush.enviar(any(), any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void limpiar() {
        asistencias.deleteAllInBatch();
        notificaciones.deleteAllInBatch();
        eventos.deleteAll(eventos.findAll().stream()
                .filter(e -> e.getNombre().startsWith("IT-audiencia")).toList());
    }

    private Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    private Cuenta cuenta() {
        return cuentas.findByPenaIdAndNombre(pena().getId(), "San Miguel").orElseThrow();
    }

    record Miembro(Usuario usuario, String token, String tokenPush) {
    }

    /** Miembro activo con sesión iniciada y un dispositivo registrado. */
    private Miembro miembroConDispositivo(RolMembresia rol) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@aud.test").passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Aud").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        String tokenPush = "tok-" + u.getId() + "-" + ThreadLocalRandom.current().nextInt(1_000_000);
        dispositivos.save(Dispositivo.builder()
                .usuario(u).token(tokenPush).plataforma(PlataformaDispositivo.ANDROID).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return new Miembro(u, (String) body.get("token"), tokenPush);
    }

    private Evento sembrarEvento(String nombre, Usuario creador) {
        return eventos.save(Evento.builder().pena(pena()).cuenta(cuenta())
                .nombre(nombre).fecha(LocalDate.now().plusDays(30)).creadoPor(creador).build());
    }

    private void responder(Miembro m, Evento e, String estado) {
        http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencia")
                .header(AUTHORIZATION, "Bearer " + m.token())
                .body(Map.of("estado", estado))
                .exchange().expectStatus().isOk();
    }

    @SuppressWarnings("unchecked")
    private List<String> tokensDelEnvio() {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        await().atMost(ofSeconds(10)).untilAsserted(() ->
                verify(servicioPush).enviar(captor.capture(), any(), any()));
        return captor.getValue();
    }

    @Test
    void primer_envio_llega_a_todos_incluido_quien_organiza_y_ya_respondio() {
        Miembro organizador = miembroConDispositivo(RolMembresia.ADMIN);
        Miembro sinContestar = miembroConDispositivo(RolMembresia.MIEMBRO);
        Evento e = sembrarEvento("IT-audiencia-primera", organizador.usuario());
        responder(organizador, e, "APUNTADO");

        http.post().uri("/api/v1/eventos/" + e.getId() + "/notificacion")
                .header(AUTHORIZATION, "Bearer " + organizador.token())
                .body(Map.of())
                .exchange().expectStatus().isNoContent();

        assertThat(tokensDelEnvio())
                .contains(organizador.tokenPush(), sinContestar.tokenPush());
    }

    @Test
    void el_reenvio_va_solo_a_quien_no_ha_contestado() {
        Miembro admin = miembroConDispositivo(RolMembresia.ADMIN);
        Miembro yaRespondio = miembroConDispositivo(RolMembresia.MIEMBRO);
        Miembro sinContestar = miembroConDispositivo(RolMembresia.MIEMBRO);
        Evento e = sembrarEvento("IT-audiencia-reenvio", admin.usuario());
        responder(yaRespondio, e, "NO_VOY");

        NotificacionEvento vieja = notificaciones.save(NotificacionEvento.builder()
                .evento(e).enviadaPor(admin.usuario()).build());
        vieja.setEnviadaAt(Instant.now().minus(49, ChronoUnit.HOURS));
        notificaciones.save(vieja);

        http.post().uri("/api/v1/eventos/" + e.getId() + "/notificacion")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of())
                .exchange().expectStatus().isNoContent();

        assertThat(tokensDelEnvio())
                .contains(sinContestar.tokenPush())
                .doesNotContain(yaRespondio.tokenPush());
    }
}
