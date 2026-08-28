package com.baniterio.api.auth;

/**
 * Identidad del usuario tal y como viaja dentro del JWT (no la entidad de BBDD).
 *
 * <p>{@link JwtService#verificar} construye este record leyendo los datos del
 * token, y {@link JwtAuthenticationFilter} lo guarda como "principal" en el
 * contexto de seguridad de Spring. En los controladores se recibe con
 * {@code @AuthenticationPrincipal UsuarioPrincipal principal}.
 *
 * <p>Solo lleva lo mínimo (id + si es superadmin); si un endpoint necesita más
 * datos del usuario, los busca en BBDD por el id (ver {@code AuthController.yo}).
 */
public record UsuarioPrincipal(Long id, boolean esSuperadmin) {
}
