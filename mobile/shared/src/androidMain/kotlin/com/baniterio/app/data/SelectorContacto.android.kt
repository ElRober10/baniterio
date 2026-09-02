package com.baniterio.app.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private class SelectorContactoAndroid : SelectorContacto {
    override val disponible = true

    override fun elegir(onTelefono: (String?) -> Unit) {
        PuenteNativo.pendienteContacto = onTelefono
        PuenteNativo.lanzarContacto?.invoke()
    }
}

@Composable
actual fun rememberSelectorContacto(): SelectorContacto = remember { SelectorContactoAndroid() }
