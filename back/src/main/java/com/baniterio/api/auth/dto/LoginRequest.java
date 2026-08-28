package com.baniterio.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo JSON de {@code POST /api/v1/auth/login}. Solo exige que teléfono y
 * contraseña no vengan vacíos: no valida el formato del teléfono a propósito
 * (un teléfono con formato raro simplemente "no existe" → 401, sin pistas).
 */
public record LoginRequest(@NotBlank String telefono, @NotBlank String password) {
}
