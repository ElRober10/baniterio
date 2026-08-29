package com.baniterio.app.data

import com.baniterio.app.data.dto.RegistroRequest
import com.baniterio.app.data.dto.SolicitudIngresoRequest
import com.baniterio.app.data.dto.UsuarioResponse

interface AuthRepository {
    val usuarioActual: UsuarioResponse?
    suspend fun registro(r: RegistroRequest): ResultadoAuth<UsuarioResponse>
    suspend fun login(telefono: String, password: String): ResultadoAuth<UsuarioResponse>
    suspend fun solicitarAcceso(r: SolicitudIngresoRequest): ResultadoAuth<Unit>
    fun logout()
}
