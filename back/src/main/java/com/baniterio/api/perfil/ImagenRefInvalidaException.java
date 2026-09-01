package com.baniterio.api.perfil;

/**
 * El {@code imagenRef} enviado con {@code imagenTipo=FOTO} no tiene la forma
 * {@code {uuid}.jpg} o no corresponde a ningún fichero del almacén (típicamente:
 * el cliente hizo el {@code PUT} sin subir antes la foto, o con una ref caducada).
 */
public class ImagenRefInvalidaException extends RuntimeException {
}
