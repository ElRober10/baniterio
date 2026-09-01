package com.baniterio.api.media;

import java.time.Duration;
import java.util.regex.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sirve imágenes: avatares del jar y fotos de perfil del disco. Público. */
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private static final Pattern NOMBRE_FOTO = Pattern.compile("^[0-9a-fA-F-]{36}\\.jpg$");

    private final CatalogoAvatares catalogo;
    private final AlmacenImagenes almacen;

    public MediaController(CatalogoAvatares catalogo, AlmacenImagenes almacen) {
        this.catalogo = catalogo;
        this.almacen = almacen;
    }

    @GetMapping("/avatares/{id}.png")
    ResponseEntity<byte[]> avatar(@PathVariable String id) {
        return catalogo.leerPng(id)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                        .body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/fotos/{archivo}")
    ResponseEntity<byte[]> foto(@PathVariable String archivo) {
        if (!NOMBRE_FOTO.matcher(archivo).matches()) {
            // 400 defensivo (anti path traversal): el cliente no debería pedir esto.
            // Respuesta directa en vez de lanzar una excepción: se evita el
            // forward interno a /error y el cuerpo va vacío, sin filtrar detalles.
            return ResponseEntity.badRequest().build();
        }
        return almacen.leerFoto(archivo)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                        .body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
