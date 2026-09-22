package com.baniterio.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Guarda en la carpeta pública de Descargas vía `MediaStore` (API 29+, sin pedir ningún
 * permiso). Por debajo de Android 10 no está disponible: el permiso `WRITE_EXTERNAL_STORAGE`
 * y su flujo de solicitud en tiempo de ejecución complicaban bastante algo tan residual (son
 * ya muy pocos los dispositivos con una versión tan vieja).
 */
private class DescargadorArchivoAndroid(private val context: Context) : DescargadorArchivo {
    override val disponible: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    override fun guardar(bytes: ByteArray, nombreArchivo: String, mimeType: String, onResultado: (Boolean) -> Unit) {
        if (!disponible) {
            onResultado(false)
            return
        }
        try {
            val resolver = context.contentResolver
            val valores = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, nombreArchivo)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
            if (uri == null) {
                onResultado(false)
                return
            }
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            onResultado(true)
        } catch (e: Exception) {
            onResultado(false)
        }
    }
}

@Composable
actual fun rememberDescargadorArchivo(): DescargadorArchivo {
    val context = LocalContext.current
    return remember(context) { DescargadorArchivoAndroid(context) }
}
