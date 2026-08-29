package com.baniterio.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.baniterio.app.data.AlmacenCredenciales
import com.baniterio.app.data.crearDependencias

fun MainViewController() = ComposeUIViewController {
    val deps = remember {
        crearDependencias(AlmacenCredenciales())
    }
    App(deps)
}
