package com.baniterio.api.preciobebida.dto;

import java.util.List;

import com.baniterio.api.evento.dto.BebidaRef;

/** Rejilla de precios de bebidas alcohólicas de un evento. */
public record GrillaAlcoholResponse(boolean puedoEditar, List<TiendaDto> tiendas, List<String> tamanos,
                                    List<BebidaRef> bebidas, List<PrecioCeldaDto> precios) {
}
