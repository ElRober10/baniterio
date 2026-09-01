package com.baniterio.api.media;

import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

class MediaIT extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired AlmacenImagenes almacen;
    @Autowired CatalogoAvatares catalogo;

    private RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void sirve_un_avatar_del_catalogo_sin_token() {
        String id = catalogo.listar().get(0).id();
        byte[] cuerpo = http.get().uri("/api/v1/media/avatares/" + id + ".png")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("image/png")
                .expectBody(byte[].class).returnResult().getResponseBody();
        assertThat(cuerpo).isNotEmpty();
    }

    @Test
    void avatar_inexistente_da_404() {
        http.get().uri("/api/v1/media/avatares/99_marciano.png").exchange().expectStatus().isNotFound();
    }

    @Test
    void sirve_una_foto_guardada_y_da_404_si_no_existe() throws Exception {
        byte[] jpeg = ProcesadorImagen.aJpegCuadrado(pngRojo(), 64);
        String archivo = almacen.guardarFoto(jpeg);

        http.get().uri("/api/v1/media/fotos/" + archivo).exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("image/jpeg");

        http.get().uri("/api/v1/media/fotos/" + java.util.UUID.randomUUID() + ".jpg")
                .exchange().expectStatus().isNotFound();
    }

    @Test
    void nombre_de_foto_con_path_traversal_da_400() {
        http.get().uri("/api/v1/media/fotos/..%2F..%2Fapplication.yml").exchange().expectStatus().isBadRequest();
        http.get().uri("/api/v1/media/fotos/cualquiercosa.txt").exchange().expectStatus().isBadRequest();
    }

    private byte[] pngRojo() throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
