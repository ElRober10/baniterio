package com.baniterio.app.data

import androidx.compose.runtime.Composable

// iOS: sin UIDocumentPicker todavía (necesita Mac para compilar/probar). El
// formulario de gasto/ingreso no ofrece adjuntar recibo. Ver memoria
// baniterio_push_ios_pendiente.
private object SelectorArchivoIos : SelectorArchivo {
    override val disponible = false
    override fun elegir(onArchivo: (ArchivoElegido?) -> Unit) = onArchivo(null)
}

@Composable
actual fun rememberSelectorArchivo(): SelectorArchivo = SelectorArchivoIos
