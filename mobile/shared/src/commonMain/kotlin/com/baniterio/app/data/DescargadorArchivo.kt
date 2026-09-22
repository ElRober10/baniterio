package com.baniterio.app.data

import androidx.compose.runtime.Composable

/** Guarda bytes ya descargados en la carpeta de Descargas del dispositivo (o su equivalente). */
interface DescargadorArchivo {
    /** Si la plataforma sabe guardar. En Android sí; en iOS de momento no (ver el `actual`). */
    val disponible: Boolean

    fun guardar(bytes: ByteArray, nombreArchivo: String, mimeType: String, onResultado: (Boolean) -> Unit)
}

@Composable
expect fun rememberDescargadorArchivo(): DescargadorArchivo
