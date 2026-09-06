package com.baniterio.app.ui.eventos

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.EventosRepository
import com.baniterio.app.data.ResultadoEvento
import com.baniterio.app.data.dto.EventoResumen
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.ui.comun.relieveDeCarta
import kotlinx.coroutines.launch

private sealed interface EstadoEventosOcultos {
    data object Cargando : EstadoEventosOcultos
    data class Cargada(val eventos: List<EventoResumen>) : EstadoEventosOcultos
    data class Error(val mensaje: String) : EstadoEventosOcultos
}

/**
 * Eventos "borrados" (ocultos, recuperables): solo llega quien tiene el enlace,
 * el backend re-comprueba que sea admin/superadmin (403 si no). Cada fila tiene
 * un botón "Recuperar"; al recuperarlo desaparece de esta lista.
 */
@Composable
fun EventosOcultosScreen(
    eventosRepo: EventosRepository,
    onAbrirEvento: (Long) -> Unit,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoEventosOcultos>(EstadoEventosOcultos.Cargando) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun cargar() {
        estado = EstadoEventosOcultos.Cargando
        estado = when (val r = eventosRepo.listarOcultos()) {
            is ResultadoEvento.Exito -> EstadoEventosOcultos.Cargada(r.dato)
            is ResultadoEvento.Error -> EstadoEventosOcultos.Error(r.mensaje)
        }
    }
    LaunchedEffect(Unit) { cargar() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BaniterioWordmark()
            Text(
                "Volver",
                style = MaterialTheme.typography.bodyMedium,
                color = BaniterioColors.brandBright,
                modifier = Modifier.clickable { onVolver() },
            )
        }
        Text(
            "Eventos ocultos",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text("Eventos \"borrados\". Se pueden recuperar.", color = BaniterioColors.muted)

        aviso?.let { Text(it, color = BaniterioColors.gold) }

        when (val e = estado) {
            is EstadoEventosOcultos.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
            is EstadoEventosOcultos.Error -> {
                Text(e.mensaje, color = BaniterioColors.muted)
                Button(onClick = { scope.launch { cargar() } }) { Text("Reintentar") }
            }
            is EstadoEventosOcultos.Cargada -> {
                if (e.eventos.isEmpty()) {
                    Text("No hay ningún evento oculto.", color = BaniterioColors.muted)
                }
                e.eventos.forEach { ev ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .relieveDeCarta(RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier.weight(1f).clickable { onAbrirEvento(ev.id) },
                        ) {
                            Text(
                                ev.nombre,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                formatoFecha(ev.fecha),
                                style = MaterialTheme.typography.bodySmall,
                                color = BaniterioColors.muted,
                            )
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                when (val r = eventosRepo.recuperar(ev.id)) {
                                    is ResultadoEvento.Exito -> {
                                        aviso = "«${ev.nombre}» recuperado."
                                        cargar()
                                    }
                                    is ResultadoEvento.Error -> aviso = r.mensaje
                                }
                            }
                        }) { Text("Recuperar") }
                    }
                }
            }
        }
    }
}
