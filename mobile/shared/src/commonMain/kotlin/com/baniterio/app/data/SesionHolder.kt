package com.baniterio.app.data

import com.baniterio.app.data.dto.UsuarioResponse

/**
 * Estado de sesión en memoria (ámbito de proceso). Lo comparten
 * [AuthRepositoryImpl] (lo rellena al hacer login) y [AdminRepositoryImpl] (lee
 * el token para la cabecera Bearer). NO se persiste: el modelo de sesión del
 * móvil re-loguea en cada arranque tras el desbloqueo biométrico.
 */
class SesionHolder {
    // `usuario` y `token` son `var` llanos, no `State` de Compose. `App.kt` lee
    // `deps.repo.usuarioActual` en las ramas Panel/AdminIndex y hoy funciona solo
    // porque `login()` es la única mutación y siempre precede a esa navegación.
    // Si en el futuro algo muta `usuario` mientras el Panel está visible (p. ej.
    // un refrescarYo()), este campo debe pasar a `mutableStateOf` o el nav no se
    // recompondrá.
    var token: String? = null
    var usuario: UsuarioResponse? = null

    fun limpiar() {
        token = null
        usuario = null
    }
}
