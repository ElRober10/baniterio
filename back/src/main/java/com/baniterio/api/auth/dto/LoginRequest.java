package com.baniterio.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String telefono, @NotBlank String password) {
}
