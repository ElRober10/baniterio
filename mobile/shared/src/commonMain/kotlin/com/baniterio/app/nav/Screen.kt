package com.baniterio.app.nav

sealed class Screen {
    data object Desbloqueo : Screen()
    data object Login : Screen()
    data object Registro : Screen()
    data object SolicitarAcceso : Screen()
    data object CargandoSesion : Screen()
    data object Panel : Screen()
    data object Historia : Screen()
    data object Miembros : Screen()
    data object EditorPerfil : Screen()
    data object Eventos : Screen()
    data object ResponderEvento : Screen()
    data object EventoDetalle : Screen()
    data object EditorEvento : Screen()
    data object Cuentas : Screen()
    data object CuentaDetalle : Screen()
    data object AdminIndex : Screen()
    data object AdminSolicitudes : Screen()
    data object AdminPermisos : Screen()
    data object AdminBebidas : Screen()
}
