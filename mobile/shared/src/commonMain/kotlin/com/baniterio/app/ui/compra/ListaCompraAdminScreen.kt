package com.baniterio.app.ui.compra

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.ListaCompraRepository
import com.baniterio.app.data.ResultadoListaCompra
import com.baniterio.app.data.dto.EventoListaCompraDto
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.ui.comun.CabeceraPantalla
import com.baniterio.app.ui.comun.EstadoCarga
import com.baniterio.app.ui.comun.PantallaConEstado

/** "Cantidades para eventos": lista de eventos para ajustar su lista de la compra. Área INVENTARIO. */
@Composable
fun ListaCompraAdminScreen(
    listaCompraRepo: ListaCompraRepository,
    onAbrirEvento: (Long) -> Unit,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoCarga<List<EventoListaCompraDto>>>(EstadoCarga.Cargando) }
    var intento by remember { mutableStateOf(0) }

    LaunchedEffect(intento) {
        estado = EstadoCarga.Cargando
        estado = when (val r = listaCompraRepo.adminEventos()) {
            is ResultadoListaCompra.Exito -> EstadoCarga.Cargado(r.dato)
            is ResultadoListaCompra.Error -> EstadoCarga.Error(r.mensaje)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        CabeceraPantalla(onVolver)
        Spacer(Modifier.height(16.dp))
        Text(
            "Cantidades para eventos",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))

        PantallaConEstado(estado, onReintentar = { intento++ }) { eventos ->
            if (eventos.isEmpty()) {
                Text("No hay eventos.", color = BaniterioColors.muted)
            }
            eventos.forEach { ev ->
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BaniterioColors.panel)
                        .clickable { onAbrirEvento(ev.id) }
                        .padding(20.dp),
                ) {
                    Text(
                        ev.nombre,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        ev.fecha + (ev.fechaFin?.let { " – $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BaniterioColors.muted,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
