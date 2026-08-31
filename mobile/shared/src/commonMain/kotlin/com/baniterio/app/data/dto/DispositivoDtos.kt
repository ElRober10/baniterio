package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

/** Cuerpo de `POST /api/v1/dispositivos`. `plataforma` = "ANDROID" | "IOS". */
@Serializable
data class RegistrarDispositivoRequest(val token: String, val plataforma: String)
