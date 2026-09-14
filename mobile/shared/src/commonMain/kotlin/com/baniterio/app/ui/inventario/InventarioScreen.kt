package com.baniterio.app.ui.inventario

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.ui.comun.CabeceraPantalla
import com.baniterio.app.ui.comun.relieveDeCarta

/** Las cinco categorías fijas del inventario: clave de backend + rótulo para la UI. */
enum class CategoriaInventario(val clave: String, val etiqueta: String) {
    ALCOHOL("ALCOHOL", "Alcohol"),
    CERVEZA("CERVEZA", "Cerveza"),
    REFRESCOS("REFRESCOS", "Refrescos"),
    LIMPIEZA("LIMPIEZA", "Limpieza y utensilios"),
    COMIDA("COMIDA", "Comida"),
}

/**
 * Portada del inventario: un botón por categoría. Al pulsar uno se abre el
 * listado de esa categoría ([InventarioCategoriaScreen]). Igual que la web:
 * cualquier peñista entra, el permiso de área se comprueba dentro.
 */
@Composable
fun InventarioScreen(
    onAbrirCategoria: (CategoriaInventario) -> Unit,
    onVolver: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CabeceraPantalla(onVolver)
        Text(
            "Inventario",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Lo que la peña tiene almacenado. Elige una categoría.",
            style = MaterialTheme.typography.bodyMedium,
            color = BaniterioColors.muted,
        )

        CategoriaInventario.entries.forEach { cat ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .relieveDeCarta(RoundedCornerShape(16.dp))
                    .clickable { onAbrirCategoria(cat) }
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    cat.etiqueta,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Text("→", color = BaniterioColors.muted)
            }
        }
    }
}
