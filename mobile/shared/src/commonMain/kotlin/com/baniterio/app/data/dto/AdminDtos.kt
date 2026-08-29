package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class SolicitudResumen(
    val id: Long,
    val nombre: String,
    val apellidos: String,
    val telefono: String,
    val email: String,
    val motivo: String,
    val relacion: String,
    val conocidos: String,
    val traeContrasena: Boolean,
    val estado: String,
    val createdAt: String? = null,
)

@Serializable
data class AprobarResponse(val resultado: String) // "CUENTA_CREADA" | "TELEFONO_AUTORIZADO"

@Serializable
data class RechazoRequest(val motivo: String? = null)

@Serializable
data class MiembroResumen(
    val id: Long,
    val nombre: String,
    val apellidos: String,
    val mote: String? = null,
    val telefono: String,
    val rol: String,
    val activo: Boolean,
    val esSuperadmin: Boolean,
    val areas: List<String> = emptyList(),
)

@Serializable
data class RolRequest(val rol: String)

@Serializable
data class ActivoRequest(val activo: Boolean)

@Serializable
data class AreasRequest(val areas: List<String>)
