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
 * Guarda/lee/borra las fotos de perfil en {@code ${app.media.dir}/fotos/}.
 * Los nombres son UUID + ".jpg" (no adivinables). Al arrancar crea la carpeta
 * si no existe.
 */
@Component
public class AlmacenImagenes {

    private final Path fotos;

    public AlmacenImagenes(AppProperties props) {
        this.fotos = Path.of(props.media().dir(), "fotos");
    }

    @PostConstruct
    void crearCarpeta() {
        try {
            Files.createDirectories(fotos);
        } catch (IOException e) {
            throw new UncheckedIOException("no se pudo crear " + fotos, e);
        }
    }

    public Path rutaFotos() {
        return fotos;
    }

    public String guardarFoto(byte[] jpeg) {
        String archivo = UUID.randomUUID() + ".jpg";
        try {
            Files.write(fotos.resolve(archivo), jpeg);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return archivo;
    }

    public void borrarFoto(String archivo) {
        try {
            Files.deleteIfExists(fotos.resolve(archivo));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** ¿Existe ya ese fichero de foto? (barato: no lee el contenido). */
    public boolean existeFoto(String archivo) {
        return Files.isRegularFile(fotos.resolve(archivo));
    }

    public Optional<byte[]> leerFoto(String archivo) {
        Path p = fotos.resolve(archivo);
        if (!Files.isRegularFile(p)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(p));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
