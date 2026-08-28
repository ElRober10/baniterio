package com.baniterio.api.auth;

import java.util.UUID;

public record UsuarioPrincipal(UUID id, boolean esSuperadmin) {
}
