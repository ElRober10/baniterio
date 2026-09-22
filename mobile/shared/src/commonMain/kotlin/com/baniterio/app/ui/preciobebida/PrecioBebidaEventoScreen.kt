package com.baniterio.app.ui.preciobebida

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.ui.comun.CabeceraPantalla
import com.baniterio.app.ui.comun.relieveDeCarta

/** Categorías de la rejilla de artículos sin tamaños (`REFRESCOS/CERVEZA/LIMPIEZA/COMIDA`). */
data class CategoriaPrecio(val etiqueta: String, val categoria: String?)

private val CATEGORIAS = listOf(
    CategoriaPrecio("Bebidas alcohólicas", null),
    CategoriaPrecio("Refrescos", "REFRESCOS"),
    CategoriaPrecio("Para alternar", "CERVEZA"),
    CategoriaPrecio("Limpieza y utensilios", "LIMPIEZA"),
    CategoriaPrecio("Comida", "COMIDA"),
)

/**
 * Segunda pantalla de "Precio compras": un botón por tipo de artículo del
 * evento. `categoria == null` = bebidas alcohólicas (rejilla con tamaños);
 * el resto van a la rejilla sin tamaños. Equivalente a
 * `front/.../precio-bebidas/evento/`.
 */
@Composable
fun PrecioBebidaEventoScreen(
    onAbrirAlcohol: () -> Unit,
    onAbrirArticulo: (String) -> Unit,
    onVolver: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CabeceraPantalla(onVolver)
        Text(
            "Precio de las bebidas",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text("Elige una categoría.", style = MaterialTheme.typography.bodyMedium)

        CATEGORIAS.forEach { c ->
            Box(
                modifier = Modifier.fillMaxWidth().height(64.dp)
                    .relieveDeCarta(RoundedCornerShape(16.dp))
                    .clickable {
                        if (c.categoria == null) onAbrirAlcohol() else onAbrirArticulo(c.categoria)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    c.etiqueta,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
