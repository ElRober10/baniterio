package com.baniterio.app.data

import androidx.compose.runtime.Composable

// iOS: guardar en Archivos necesitaría UIDocumentPicker (necesita Mac para
// compilar/probar). Ver memoria baniterio_push_ios_pendiente. De momento el botón
// de "Descargar" del visor de recibos no aparece en iOS (disponible = false).
private object DescargadorArchivoIos : DescargadorArchivo {
    override val disponible = false
    override fun guardar(bytes: ByteArray, nombreArchivo: String, mimeType: String, onResultado: (Boolean) -> Unit) =
        onResultado(false)
}

@Composable
actual fun rememberDescargadorArchivo(): DescargadorArchivo = DescargadorArchivoIos
