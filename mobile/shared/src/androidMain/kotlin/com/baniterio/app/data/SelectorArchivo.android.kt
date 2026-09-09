package com.baniterio.app.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private class SelectorArchivoAndroid : SelectorArchivo {
    override val disponible = true

    override fun elegir(onArchivo: (ArchivoElegido?) -> Unit) {
        PuenteNativo.pendienteArchivo = onArchivo
        PuenteNativo.lanzarArchivo?.invoke()
    }
}

@Composable
actual fun rememberSelectorArchivo(): SelectorArchivo = remember { SelectorArchivoAndroid() }
