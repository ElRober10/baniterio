package com.baniterio.app.nav

/**
 * Datos que Registro puede pasar a SolicitarAcceso cuando el teléfono no está
 * autorizado, para precargar el formulario de solicitud de ingreso.
 */
data class SolicitudPrecarga(
    val nombre: String,
    val apellidos: String,
    val telefono: String,
    val email: String,
    val password: String = "",
)
