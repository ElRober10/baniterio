package com.baniterio.api.push.dto;

import com.baniterio.api.identidad.PlataformaDispositivo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code POST /api/v1/dispositivos}: el token de push del móvil y su plataforma. */
public record RegistrarDispositivoRequest(
        @NotBlank String token,
        @NotNull PlataformaDispositivo plataforma) {
}
