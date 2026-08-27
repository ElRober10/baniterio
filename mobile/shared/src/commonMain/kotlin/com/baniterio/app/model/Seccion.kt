package com.baniterio.app.model

data class Seccion(
    val nombre: String,
    val descripcion: String,
    val tieneContenido: Boolean = false,
)

val seccionesPanel = listOf(
    Seccion("Historia", "Cómo nació el Bañiterio y qué significa su escudo.", tieneContenido = true),
    Seccion("Miembros", "Socios de la peña y sus datos de contacto."),
    Seccion("Eventos", "Calendario y organización de las quedadas y fiestas de la peña."),
    Seccion("Cuentas", "Ingresos, gastos y balance de la peña."),
    Seccion("Inventario", "Material y enseres que tiene la peña."),
    Seccion("Ropa", "Pedidos y tallas del vestuario de la peña."),
)
