package com.baniterio.app.nav

sealed class Screen {
    data object Login : Screen()
    data object Registro : Screen()
    data object Panel : Screen()
    data object Historia : Screen()
}
