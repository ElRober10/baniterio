package com.baniterio.api.evento.dto;

import java.math.BigDecimal;

/** Una de las cuotas fijadas en el evento, para elegirla al actualizar la cuota de alguien. */
public record OpcionCuota(String texto, BigDecimal importe) {
}
