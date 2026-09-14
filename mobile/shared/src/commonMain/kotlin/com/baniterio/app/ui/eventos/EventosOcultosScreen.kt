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
import com.baniterio.app.ui.comun.CabeceraPantalla
import com.baniterio.app.ui.comun.EstadoCarga
import com.baniterio.app.ui.comun.PantallaConEstado
import com.baniterio.app.ui.comun.relieveDeCarta
import kotlinx.coroutines.launch

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
    var estado by remember { mutableStateOf<EstadoCarga<List<EventoResumen>>>(EstadoCarga.Cargando) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun cargar() {
        estado = EstadoCarga.Cargando
        estado = when (val r = eventosRepo.listarOcultos()) {
            is ResultadoEvento.Exito -> EstadoCarga.Cargado(r.dato)
            is ResultadoEvento.Error -> EstadoCarga.Error(r.mensaje)
        }
    }
    LaunchedEffect(Unit) { cargar() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CabeceraPantalla(onVolver)
        Text(
            "Eventos ocultos",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text("Eventos \"borrados\". Se pueden recuperar.", color = BaniterioColors.muted)

        aviso?.let { Text(it, color = BaniterioColors.gold) }

        PantallaConEstado(estado, onReintentar = { scope.launch { cargar() } }) { eventos ->
            if (eventos.isEmpty()) {
                Text("No hay ningún evento oculto.", color = BaniterioColors.muted)
            }
            eventos.forEach { ev ->
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
