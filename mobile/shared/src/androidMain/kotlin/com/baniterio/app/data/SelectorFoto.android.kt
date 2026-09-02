package com.baniterio.app.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private class SelectorFotoAndroid : SelectorFoto {
    override val disponible = true

    override fun elegir(onFoto: (FotoElegida?) -> Unit) {
        PuenteNativo.pendienteFoto = onFoto
        PuenteNativo.lanzarFoto?.invoke()
    }
}

@Composable
actual fun rememberSelectorFoto(): SelectorFoto = remember { SelectorFotoAndroid() }
