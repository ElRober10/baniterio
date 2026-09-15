package com.baniterio.api.auth;

/**
 * Identidad del usuario tal y como viaja dentro del JWT (no la entidad de BBDD).
 *
 * <p>{@link JwtService#verificar} construye este record leyendo los datos del
 * token, y {@link JwtAuthenticationFilter} lo guarda como "principal" en el
 * contexto de seguridad de Spring. En los controladores se recibe con
 * {@code @AuthenticationPrincipal UsuarioPrincipal principal}.
 *
 * <p>Solo lleva lo mínimo (id + si es superadmin + si es la cuenta demo
 * pública); si un endpoint necesita más datos del usuario, los busca en BBDD
 * por el id (ver {@code AuthController.yo}).
 *
 * <p>{@code esDemo}: la cuenta demo pública (teléfono fijo, ver
 * {@code AuthService}) puede navegar toda la app pero no puede escribir nada
 * — lo corta {@link JwtAuthenticationFilter} antes de llegar a los
 * controladores.
 */
public record UsuarioPrincipal(Long id, boolean esSuperadmin, boolean esDemo) {
}
