package com.baniterio.app.model

import com.baniterio.app.nav.Screen

data class Seccion(
    val nombre: String,
    val descripcion: String,
    val destino: Screen? = null,
)

val seccionesPanel = listOf(
    Seccion("Historia", "Cómo nació el Bañiterio y qué significa su escudo.", destino = Screen.Historia),
    Seccion("Miembros", "Socios de la peña y sus datos de contacto."),
    Seccion("Eventos", "Calendario y organización de las quedadas y fiestas de la peña."),
    Seccion("Cuentas", "Ingresos, gastos y balance de la peña."),
    Seccion("Inventario", "Material y enseres que tiene la peña."),
    Seccion("Ropa", "Pedidos y tallas del vestuario de la peña."),
)
