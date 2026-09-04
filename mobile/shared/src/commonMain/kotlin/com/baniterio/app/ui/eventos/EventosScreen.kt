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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.EventosRepository
import com.baniterio.app.data.ResultadoEvento
import com.baniterio.app.data.dto.EventoResumen
import com.baniterio.app.data.dto.ListaEventosResponse
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.ui.comun.relieveDeCarta
import kotlinx.coroutines.launch

private sealed interface EstadoEventos {
    data object Cargando : EstadoEventos
    data class Cargada(val datos: ListaEventosResponse) : EstadoEventos
    data class Error(val mensaje: String) : EstadoEventos
}

@Composable
fun EventosScreen(
    eventosRepo: EventosRepository,
    onAbrirEvento: (Long) -> Unit,
    onCrear: () -> Unit,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoEventos>(EstadoEventos.Cargando) }
    var pagina by remember { mutableStateOf(0) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var dialogoSolicitud by remember { mutableStateOf(false) }
    var mensajeSolicitud by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun cargar(p: Int, mostrarCargando: Boolean = true) {
        if (mostrarCargando) estado = EstadoEventos.Cargando
        estado = when (val r = eventosRepo.listar(p)) {
            is ResultadoEvento.Exito -> {
                pagina = r.dato.pagina
                EstadoEventos.Cargada(r.dato)
            }
            is ResultadoEvento.Error -> EstadoEventos.Error(r.mensaje)
        }
    }
    LaunchedEffect(Unit) { cargar(0) }

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
            "Eventos",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )

        when (val e = estado) {
            is EstadoEventos.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
            is EstadoEventos.Error -> {
                Text(e.mensaje, color = BaniterioColors.muted)
                Button(onClick = { scope.launch { cargar(pagina) } }) { Text("Reintentar") }
            }
            is EstadoEventos.Cargada -> {
                val d = e.datos
                if (d.puedeCrear) {
                    Button(
                        onClick = onCrear,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BaniterioColors.brand,
                            contentColor = BaniterioColors.gold,
                        ),
                    ) { Text("Crear evento", fontWeight = FontWeight.Bold) }
                } else if (d.puedeSolicitar) {
                    OutlinedButton(onClick = { mensajeSolicitud = ""; dialogoSolicitud = true }) {
                        Text("Solicitar crear evento")
                    }
                }
                aviso?.let { Text(it, color = BaniterioColors.gold) }

                d.eventos.forEach { ev -> TarjetaEvento(ev) { onAbrirEvento(ev.id) } }
                if (d.eventos.isEmpty()) Text("Todavía no hay eventos.", color = BaniterioColors.muted)

                if (d.totalPaginas > 1) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            enabled = pagina > 0,
                            onClick = { scope.launch { cargar(pagina - 1, false) } },
                        ) { Text("Anterior") }
                        Text("${pagina + 1} / ${d.totalPaginas}", color = BaniterioColors.muted)
                        TextButton(
                            enabled = pagina + 1 < d.totalPaginas,
                            onClick = { scope.launch { cargar(pagina + 1, false) } },
                        ) { Text("Siguiente") }
                    }
                }
            }
        }
    }

    if (dialogoSolicitud) {
        AlertDialog(
            onDismissRequest = { dialogoSolicitud = false },
            title = { Text("Solicitar crear un evento") },
            text = {
                OutlinedTextField(
                    value = mensajeSolicitud,
                    onValueChange = { mensajeSolicitud = it },
                    label = { Text("¿Qué evento quieres organizar? (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val r = eventosRepo.solicitarCrear(mensajeSolicitud.ifBlank { null })
                        dialogoSolicitud = false
                        aviso = when (r) {
                            is ResultadoEvento.Exito ->
                                "Solicitud enviada. Un administrador tiene que autorizarla."
                            is ResultadoEvento.Error -> r.mensaje
                        }
                        cargar(pagina, mostrarCargando = false)
                    }
                }) { Text("Enviar") }
            },
            dismissButton = {
                TextButton(onClick = { dialogoSolicitud = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun TarjetaEvento(e: EventoResumen, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .relieveDeCarta(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
            .alpha(if (e.pasado) 0.6f else 1f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                e.nombre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            e.lugar?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = BaniterioColors.muted)
            }
        }
        Text(formatoFecha(e.fecha), style = MaterialTheme.typography.bodySmall, color = BaniterioColors.muted)
    }
}
