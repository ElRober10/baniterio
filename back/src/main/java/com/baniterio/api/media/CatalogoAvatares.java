package com.baniterio.api.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

import com.baniterio.api.perfil.dto.AvatarResumen;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Catálogo fijo de avatares. Lee {@code classpath:avatares/avatares.json} una
 * vez al arrancar; los PNG se sirven bajo demanda desde el classpath. No hace
 * listado del directorio (incómodo desde un jar): el manifiesto es la fuente
 * de verdad.
 */
@Component
public class CatalogoAvatares {

    private final ObjectMapper mapper;
    private List<AvatarResumen> avatares = List.of();

    public CatalogoAvatares(ObjectMapper mapper) {
        this.mapper = mapper;
        cargar();
    }

    private void cargar() {
        try (InputStream in = new ClassPathResource("avatares/avatares.json").getInputStream()) {
            this.avatares = List.of(mapper.readValue(in, AvatarResumen[].class));
        } catch (IOException e) {
            throw new UncheckedIOException("no se pudo leer avatares/avatares.json", e);
        }
    }

    public List<AvatarResumen> listar() {
        return avatares;
    }

    public boolean existe(String id) {
        return avatares.stream().anyMatch(a -> a.id().equals(id));
    }

    public Optional<byte[]> leerPng(String id) {
        if (!existe(id)) {
            return Optional.empty();
        }
        ClassPathResource png = new ClassPathResource("avatares/" + id + ".png");
        if (!png.exists()) {
            return Optional.empty();
        }
        try (InputStream in = png.getInputStream()) {
            return Optional.of(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
