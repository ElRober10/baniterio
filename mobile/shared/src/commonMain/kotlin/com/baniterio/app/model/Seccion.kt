package com.baniterio.app.model

import com.baniterio.app.nav.Screen

data class Seccion(
    val nombre: String,
    val descripcion: String,
    val destino: Screen? = null,
)

fun seccionesPanel(tieneAdmin: Boolean): List<Seccion> = buildList {
    add(Seccion("Historia", "Cómo nació el Bañiterio y qué significa su escudo.", destino = Screen.Historia))
    add(Seccion("Miembros", "Las tarjetas de los socios de la peña.", destino = Screen.Miembros))
    add(Seccion("Eventos", "Calendario y organización de las quedadas y fiestas de la peña.", destino = Screen.Eventos))
    add(Seccion("Cuentas", "Ingresos, gastos y balance de la peña."))
    add(Seccion("Inventario", "Material y enseres que tiene la peña."))
    add(Seccion("Ropa", "Pedidos y tallas del vestuario de la peña."))
    if (tieneAdmin) {
        add(Seccion("Administración", "Solicitudes de acceso y permisos de la peña.", destino = Screen.AdminIndex))
    }
}
