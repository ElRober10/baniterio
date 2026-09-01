package com.baniterio.api.media;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.baniterio.api.perfil.ImagenNoSoportadaException;

/**
 * Recorta una imagen a cuadrado centrado, la escala a {@code lado}×{@code lado}
 * y la re-codifica a JPEG. Usa solo {@code javax.imageio} (incluido en el JDK):
 * lee JPEG y PNG. Al no copiar metadatos, el EXIF (orientación, GPS) se pierde,
 * que es justo lo que queremos.
 */
public final class ProcesadorImagen {

    private ProcesadorImagen() {
    }

    public static byte[] aJpegCuadrado(byte[] original, int lado) {
        BufferedImage src;
        try {
            src = ImageIO.read(new ByteArrayInputStream(original));
        } catch (IOException e) {
            throw new ImagenNoSoportadaException();
        }
        if (src == null) {
            throw new ImagenNoSoportadaException();
        }

        int min = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - min) / 2;
        int y = (src.getHeight() - min) / 2;
        BufferedImage recorte = src.getSubimage(x, y, min, min);

        BufferedImage destino = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_RGB);
        Image escalada = recorte.getScaledInstance(lado, lado, Image.SCALE_SMOOTH);
        destino.getGraphics().drawImage(escalada, 0, 0, null);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(destino, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("no se pudo codificar el JPEG", e);
        }
    }
}
