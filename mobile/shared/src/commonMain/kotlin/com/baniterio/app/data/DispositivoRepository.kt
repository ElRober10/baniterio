package com.baniterio.app.data

/**
 * Registra o da de baja el token de push de este dispositivo en el backend.
 * Devuelve `true` si la llamada fue bien; `false` si falló (se ignora — el
 * backend poda los tokens muertos por su cuenta al enviar).
 */
interface DispositivoRepository {
    suspend fun registrar(token: String, plataforma: String): Boolean

    /**
     * [bearer] permite pasar el JWT explícitamente cuando la sesión ya se ha
     * limpiado (baja de token al cerrar sesión): se captura de forma síncrona
     * antes de `logout()` y se pasa aquí. Si es `null`, se usa el de la sesión.
     */
    suspend fun eliminar(token: String, bearer: String? = null): Boolean
}
