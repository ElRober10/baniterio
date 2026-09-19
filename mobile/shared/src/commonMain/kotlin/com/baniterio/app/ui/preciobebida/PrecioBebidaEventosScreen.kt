package com.baniterio.app.ui.preciobebida

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.PrecioBebidaRepository
import com.baniterio.app.data.ResultadoPrecioBebida
import com.baniterio.app.data.dto.EventoPrecioBebidaDto
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.ui.comun.CabeceraPantalla
import com.baniterio.app.ui.comun.EstadoCarga
import com.baniterio.app.ui.comun.PantallaConEstado
import com.baniterio.app.ui.comun.relieveDeCarta

private fun anioDe(fecha: String): Int = fecha.take(4).toIntOrNull() ?: 0

/**
 * Primera pantalla de "Precio compras": todos los eventos, agrupados por año
 * (más reciente primero), cada uno lleva a la rejilla de precios de alcohol
 * de ese evento. Equivalente al `años → eventos` de la web, en una sola
 * pantalla porque en móvil no aporta nada partirlo en dos pasos.
 */
@Composable
fun PrecioBebidaEventosScreen(
    precioBebidaRepo: PrecioBebidaRepository,
    onAbrirEvento: (Long) -> Unit,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoCarga<List<EventoPrecioBebidaDto>>>(EstadoCarga.Cargando) }
    var intento by remember { mutableStateOf(0) }

    LaunchedEffect(intento) {
        estado = EstadoCarga.Cargando
        estado = when (val r = precioBebidaRepo.eventos()) {
            is ResultadoPrecioBebida.Exito -> EstadoCarga.Cargado(r.dato)
            is ResultadoPrecioBebida.Error -> EstadoCarga.Error(r.mensaje)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CabeceraPantalla(onVolver)
        Text(
            "Precio compras",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Precios de bebidas por súper, evento a evento, para comprar lo más barato.",
            style = MaterialTheme.typography.bodyMedium,
            color = BaniterioColors.muted,
        )

        PantallaConEstado(estado, onReintentar = { intento++ }) { eventos ->
            if (eventos.isEmpty()) {
                Text("Todavía no hay eventos.", color = BaniterioColors.muted)
            }
            val porAnio = eventos.groupBy { anioDe(it.fecha) }
            porAnio.keys.sortedDescending().forEach { anio ->
                Text(
                    anio.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BaniterioColors.gold,
                )
                porAnio.getValue(anio).sortedBy { it.fecha }.forEach { e ->
                    TarjetaEvento(e) { onAbrirEvento(e.id) }
                }
            }
        }
    }
}

@Composable
private fun TarjetaEvento(e: EventoPrecioBebidaDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .relieveDeCarta(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            e.nombre,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text("→", color = BaniterioColors.muted)
    }
}
