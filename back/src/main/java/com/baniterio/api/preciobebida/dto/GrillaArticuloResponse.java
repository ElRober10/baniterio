package com.baniterio.api.preciobebida.dto;

import java.util.List;

/** Rejilla de precios de una sección sin tamaños (refrescos, cerveza, limpieza, comida). */
public record GrillaArticuloResponse(boolean puedoEditar, List<TiendaDto> tiendas, List<String> articulos,
                                     List<PrecioArticuloCeldaDto> precios) {
}
