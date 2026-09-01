package com.baniterio.app.ui.miembros

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.baniterio.app.data.dto.AvatarResumen
import com.baniterio.app.data.urlMedia
import com.baniterio.app.theme.BaniterioColors

/**
 * Rejilla del catálogo de avatares con filtro Todos/Chicos/Chicas. El elegido
 * se marca con un aro dorado. Emite el id al pulsar uno.
 */
@Composable
fun SelectorAvatar(
    avatares: List<AvatarResumen>,
    seleccionado: String?,
    onElegir: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filtro by remember { mutableStateOf("TODOS") } // TODOS | CHICO | CHICA
    val visibles = if (filtro == "TODOS") avatares else avatares.filter { it.genero == filtro }

    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("TODOS" to "Todos", "CHICO" to "Chicos", "CHICA" to "Chicas").forEach { (clave, etiqueta) ->
                FilterChip(
                    selected = filtro == clave,
                    onClick = { filtro = clave },
                    label = { Text(etiqueta) },
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(72.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(visibles, key = { it.id }) { a ->
                val elegido = a.id == seleccionado
                AsyncImage(
                    model = urlMedia("/api/v1/media/avatares/${a.id}.png"),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(BaniterioColors.brandDark)
                        .then(
                            if (elegido) Modifier.border(2.dp, BaniterioColors.gold, CircleShape) else Modifier,
                        )
                        .clickable { onElegir(a.id) },
                )
            }
        }
    }
}
