package com.baniterio.api.preciobebida.dto;

import java.util.List;

/**
 * Rejilla de precios de una sección sin variantes de tamaño de precio (refrescos,
 * cerveza, limpieza, comida). {@code tamanos} trae los tamaños de botella apuntados
 * (o heredados de un evento anterior); el resto se entiende de 2 litros.
 * {@code porKilo} solo viene en comida: los embutidos de Jamones Duriber.
 */
public record GrillaArticuloResponse(boolean puedoEditar, List<TiendaDto> tiendas, List<String> articulos,
                                     List<PrecioArticuloCeldaDto> precios, List<TamanoArticuloDto> tamanos,
                                     List<ProductoKiloDto> porKilo) {
}
