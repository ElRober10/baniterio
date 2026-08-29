package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegistroRequest(
    val telefono: String,
    val email: String,
    val password: String,
    val nombre: String,
    val apellidos: String,
    val mote: String,
)

@Serializable
data class LoginRequest(val telefono: String, val password: String)

@Serializable
data class UsuarioResponse(
    val id: Long,
    val nombre: String,
    val apellidos: String,
    val mote: String? = null,
    val esSuperadmin: Boolean,
    val rol: String? = null,
    val areas: List<String> = emptyList(),
)

@Serializable
data class LoginResponse(val token: String, val usuario: UsuarioResponse)

@Serializable
data class SolicitudIngresoRequest(
    val telefono: String,
    val email: String,
    val nombre: String,
    val apellidos: String,
    val motivo: String,
    val relacion: String,
    val conocidos: String,
    val password: String? = null,
)

@Serializable
data class ErrorResponse(val codigo: String? = null)
