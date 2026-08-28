package com.baniterio.api.auth.dto;

public record LoginResponse(String token, UsuarioResponse usuario) {
}
