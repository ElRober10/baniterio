package com.baniterio.app.data

import com.baniterio.app.data.dto.UsuarioResponse

/**
 * Estado de sesión en memoria (ámbito de proceso). Lo comparten
 * [AuthRepositoryImpl] (lo rellena al hacer login) y [AdminRepositoryImpl] (lee
 * el token para la cabecera Bearer). NO se persiste: el modelo de sesión del
 * móvil re-loguea en cada arranque tras el desbloqueo biométrico.
 */
class SesionHolder {
    var token: String? = null
    var usuario: UsuarioResponse? = null

    fun limpiar() {
        token = null
        usuario = null
    }
}
