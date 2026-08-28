package com.baniterio.api.auth.dto;

/**
 * Respuesta JSON de un login correcto: el JWT ({@code token}) que el front debe
 * guardar y enviar en cada petición, más los datos del usuario para pintarlos
 * sin tener que llamar a {@code /yo}.
 */
public record LoginResponse(String token, UsuarioResponse usuario) {
}
