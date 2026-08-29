package com.baniterio.app.nav

sealed class Screen {
    data object Desbloqueo : Screen()
    data object Login : Screen()
    data object Registro : Screen()
    data object SolicitarAcceso : Screen()
    data object Panel : Screen()
    data object Historia : Screen()
}
