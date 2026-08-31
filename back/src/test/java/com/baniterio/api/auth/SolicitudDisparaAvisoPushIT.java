package com.baniterio.api.auth;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.Dispositivo;
import com.baniterio.api.identidad.DispositivoRepository;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.PlataformaDispositivo;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.push.ServicioPush;
import com.baniterio.api.support.IntegrationTest;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test de integración de punta a punta: al crear una solicitud de acceso
 * ({@code POST /api/v1/auth/solicitudes}), {@code AuthService} publica un
 * {@code AvisoPushEvent} para los administradores; tras confirmar la
 * transacción, {@code ManejadorAvisoPush} llama a {@link ServicioPush} con los
 * tokens de los admins que tengan dispositivo registrado.
 *
 * <p>{@link ServicioPush} va mockeado ({@code @MockitoBean}) para espiar el
 * envío sin tocar Firebase. El manejador es {@code AFTER_COMMIT} y la poda de
 * tokens muertos corre en su propia transacción ({@code REQUIRES_NEW}), así que
 * se usa Awaitility para no depender del momento exacto en que terminan esos
 * callbacks respecto a la respuesta HTTP.
 */
class SolicitudDisparaAvisoPushIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    PenaRepository penas;

    @Autowired
    DispositivoRepository dispositivos;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    ServicioPush servicioPush;

    private RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        when(servicioPush.enviar(any(), any(), any())).thenReturn(List.of());
    }

    // --- Helpers ---

    private String telefonoLibre() {
        String numero;
        do {
            numero = (ThreadLocalRandom.current().nextBoolean() ? "6" : "7")
                    + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        } while (usuarios.existsByTelefono(numero));
        return numero;
    }

    /** Crea un admin activo con un dispositivo registrado y devuelve su token push. */
    private String adminConDispositivo() {
        String telefono = telefonoLibre();
        Usuario admin = usuarios.save(Usuario.builder()
                .telefono(telefono)
                .email(telefono + "@admin.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("Admin")
                .apellidos("De Test")
                .esSuperadmin(false)
                .activo(true)
                .build());
        membresias.save(Membresia.builder()
                .usuario(admin)
                .pena(penas.findBySlug("baniterio").orElseThrow())
                .rol(RolMembresia.ADMIN)
                .activa(true)
                .build());
        String tokenPush = "tok-admin-" + admin.getId() + "-" + ThreadLocalRandom.current().nextInt(1_000_000);
        dispositivos.save(Dispositivo.builder()
                .usuario(admin)
                .token(tokenPush)
                .plataforma(PlataformaDispositivo.ANDROID)
                .build());
        return tokenPush;
    }

    private void crearSolicitud(String nombre, String apellidos) {
        String telefono = telefonoLibre();
        http.post().uri("/api/v1/auth/solicitudes").body(Map.of(
                        "telefono", telefono,
                        "email", "sol-" + telefono + "@x.com",
                        "nombre", nombre,
                        "apellidos", apellidos,
                        "motivo", "Soy de la peña de siempre y quiero volver ya.",
                        "relacion", "Tres generaciones de mi familia en la peña.",
                        "conocidos", "Conozco a medio mundo de la peña."))
                .exchange().expectStatus().isCreated();
    }

    // --- Casos ---

    @Test
    void crear_solicitud_envia_push_a_los_admins_con_dispositivo() {
        String tokenPush = adminConDispositivo();

        crearSolicitud("Marta", "García");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> tokens = ArgumentCaptor.forClass(List.class);
        await().atMost(ofSeconds(10)).untilAsserted(() ->
                verify(servicioPush).enviar(
                        tokens.capture(),
                        eq("Nueva solicitud de acceso"),
                        eq("Marta García quiere entrar en la peña")));
        assertThat(tokens.getValue()).contains(tokenPush);
    }

    @Test
    void un_token_que_fcm_marca_muerto_se_poda_tras_el_envio() {
        String tokenPush = adminConDispositivo();
        // FCM responde que ese token está muerto.
        when(servicioPush.enviar(any(), any(), any())).thenReturn(List.of(tokenPush));

        crearSolicitud("Pedro", "Ruiz");

        await().atMost(ofSeconds(10)).untilAsserted(() ->
                assertThat(dispositivos.findByToken(tokenPush)).isEmpty());
    }
}
