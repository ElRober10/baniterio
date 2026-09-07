package com.baniterio.api.evento.dto;

import java.math.BigDecimal;

/** Una persona a la que cubre un pago declarado: su nombre y su cuota. */
public record CubiertoPago(String nombre, BigDecimal cuota) {
}
