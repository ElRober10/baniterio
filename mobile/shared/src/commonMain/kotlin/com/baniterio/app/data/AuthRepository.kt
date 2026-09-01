package com.baniterio.app.data

import com.baniterio.app.data.dto.RegistroRequest
import com.baniterio.app.data.dto.SolicitudIngresoRequest
import com.baniterio.app.data.dto.UsuarioResponse

interface AuthRepository {
    val usuarioActual: UsuarioResponse?

    /**
     * El JWT en memoria de la sesión (o `null` si no hay). Se expone para poder
     * capturarlo de forma síncrona ANTES de `logout()`: el callback
     * `alCerrarSesion` corre en el mismo frame que `logout()`, así que una
     * corrutina que lo lea después vería ya `null`.
     */
    val tokenSesion: String?
    suspend fun registro(r: RegistroRequest): ResultadoAuth<UsuarioResponse>
    suspend fun login(telefono: String, password: String): ResultadoAuth<UsuarioResponse>
    suspend fun solicitarAcceso(r: SolicitudIngresoRequest): ResultadoAuth<Unit>
    fun logout()
}
