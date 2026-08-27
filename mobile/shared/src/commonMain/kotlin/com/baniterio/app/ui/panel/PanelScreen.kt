package com.baniterio.app.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.model.Seccion
import com.baniterio.app.model.seccionesPanel
import com.baniterio.app.theme.BaniterioColors

@Composable
fun PanelScreen(onAbrirHistoria: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "¡Bienvenido de vuelta a la Bañiterio!",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            Text(
                text = "Desde aquí podrás llevar la historia, los miembros, los eventos, las cuentas, el inventario y la ropa de la peña.",
                style = MaterialTheme.typography.bodyMedium,
                color = BaniterioColors.muted,
            )
        }
        items(seccionesPanel) { seccion ->
            TarjetaSeccion(seccion = seccion, onClick = { if (seccion.tieneContenido) onAbrirHistoria() })
        }
    }
}

@Composable
private fun TarjetaSeccion(seccion: Seccion, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BaniterioColors.panel)
            .clickable(enabled = seccion.tieneContenido, onClick = onClick)
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = seccion.nombre,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            if (!seccion.tieneContenido) {
                Text(
                    text = "PRÓXIMAMENTE",
                    style = MaterialTheme.typography.labelLarge,
                    color = BaniterioColors.muted,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = seccion.descripcion,
            style = MaterialTheme.typography.bodyMedium,
            color = BaniterioColors.muted,
        )
    }
}
