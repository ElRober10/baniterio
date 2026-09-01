package com.baniterio.api.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import com.baniterio.api.perfil.ImagenNoSoportadaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcesadorImagenTest {

    private byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void una_imagen_rectangular_sale_cuadrada_de_512_y_en_jpeg() throws Exception {
        byte[] jpeg = ProcesadorImagen.aJpegCuadrado(png(1200, 800), 512);

        BufferedImage salida = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(salida.getWidth()).isEqualTo(512);
        assertThat(salida.getHeight()).isEqualTo(512);
        // firma JPEG
        assertThat(jpeg[0] & 0xFF).isEqualTo(0xFF);
        assertThat(jpeg[1] & 0xFF).isEqualTo(0xD8);
    }

    @Test
    void bytes_que_no_son_imagen_lanzan_ImagenNoSoportadaException() {
        assertThatThrownBy(() -> ProcesadorImagen.aJpegCuadrado("no soy una imagen".getBytes(), 512))
                .isInstanceOf(ImagenNoSoportadaException.class);
    }
}
