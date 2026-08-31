package com.baniterio.app.data

/**
 * Registra o da de baja el token de push de este dispositivo en el backend.
 * Devuelve `true` si la llamada fue bien; `false` si falló (se ignora — el
 * backend poda los tokens muertos por su cuenta al enviar).
 */
interface DispositivoRepository {
    suspend fun registrar(token: String, plataforma: String): Boolean
    suspend fun eliminar(token: String): Boolean
}
