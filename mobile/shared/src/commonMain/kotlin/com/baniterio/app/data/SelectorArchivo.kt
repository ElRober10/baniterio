package com.baniterio.app.data

import androidx.compose.runtime.Composable

/**
 * Selector de fichero del dispositivo (PDF o imagen), para adjuntar el recibo de
 * un gasto/ingreso de una cuenta. Misma mecánica que [SelectorFoto]: solo
 * disponible en Android por ahora; el de iOS (`UIDocumentPicker`) está aparcado
 * junto al resto del trabajo que necesita Mac (ver memoria
 * `baniterio_push_ios_pendiente`). Cuando [disponible] es `false`, el formulario
 * no ofrece adjuntar recibo.
 */
interface SelectorArchivo {
    val disponible: Boolean

    /** Abre el selector; llama a [onArchivo] con la elección, o con `null` si se cancela. */
    fun elegir(onArchivo: (ArchivoElegido?) -> Unit)
}

@Composable
expect fun rememberSelectorArchivo(): SelectorArchivo
