package com.baniterio.api.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import com.baniterio.api.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Guarda/lee/borra los recibos de los gastos en {@code ${app.media.dir}/recibos/}.
 * Acepta PDF y foto (JPG/PNG). Nombres = UUID + extensión (no adivinables).
 * Mismo patrón que {@link AlmacenImagenes}, pero varios tipos.
 */
@Component
public class AlmacenRecibos {

    private final Path dir;

    public AlmacenRecibos(AppProperties props) {
        this.dir = Path.of(props.media().dir(), "recibos");
    }

    @PostConstruct
    void crearCarpeta() {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("no se pudo crear " + dir, e);
        }
    }

    /** {@code null} si el content-type no es uno de los aceptados. */
    public static String extensionDe(String contentType) {
        if (contentType == null) {
            return null;
        }
        return switch (contentType.toLowerCase()) {
            case "application/pdf" -> "pdf";
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            default -> null;
        };
    }

    public static String contentTypeDe(String archivo) {
        if (archivo.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (archivo.endsWith(".png")) {
            return "image/png";
        }
        return "image/jpeg";
    }

    /** Guarda los bytes con la extensión que toque; devuelve el nombre del fichero. */
    public String guardar(byte[] contenido, String extension) {
        String archivo = UUID.randomUUID() + "." + extension;
        try {
            Files.write(dir.resolve(archivo), contenido);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return archivo;
    }

    public Optional<byte[]> leer(String archivo) {
        Path p = dir.resolve(archivo);
        if (!Files.isRegularFile(p)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(p));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void borrar(String archivo) {
        try {
            Files.deleteIfExists(dir.resolve(archivo));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
