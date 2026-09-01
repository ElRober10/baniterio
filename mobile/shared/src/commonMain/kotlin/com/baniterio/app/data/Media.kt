package com.baniterio.app.data

/**
 * Convierte la URL relativa de media que devuelve el backend
 * (`/api/v1/media/...`) en una absoluta que Coil pueda cargar. El origen sale de
 * [API_BASE_URL] quitándole el sufijo `/api/v1`. `null` → `null`.
 */
fun urlMedia(imagenUrl: String?): String? {
    if (imagenUrl == null) return null
    val origen = API_BASE_URL.removeSuffix("/api/v1")
    return origen + imagenUrl
}
