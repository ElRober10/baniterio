package com.baniterio.app.ui.admin

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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.EventosRepository
import com.baniterio.app.data.ResultadoEvento
import com.baniterio.app.data.dto.PagoDeclaradoPendienteDto
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.ui.comun.relieveDeCarta
import com.baniterio.app.ui.eventos.formatoImporte
import kotlinx.coroutines.launch

private sealed interface EstadoPagos {
    data object Cargando : EstadoPagos
    data class Lista(val pendientes: List<PagoDeclaradoPendienteDto>) : EstadoPagos
    data class Error(val mensaje: String) : EstadoPagos
}

private fun metodoLegible(m: String) = when (m) {
    "BIZUM" -> "Bizum"
    "TRANSFERENCIA" -> "Transferencia"
    "EFECTIVO" -> "Efectivo"
    else -> m
}

/**
 * Pantalla de admin "Confirmar pagos": la cola de declaraciones de pago que la
 * gente ha hecho desde el detalle del evento. Confirmar → marca las cuotas
 * cubiertas como pagadas; rechazar → avisa al declarante. Solo se llega desde el
 * índice de administración, que solo la ofrece a admins/superadmins.
 */
@Composable
fun AdminPagosScreen(
    eventosRepo: EventosRepository,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoPagos>(EstadoPagos.Cargando) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var intento by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(intento) {
        estado = EstadoPagos.Cargando
        estado = when (val r = eventosRepo.pagosDeclaradosPendientes()) {
            is ResultadoEvento.Exito -> EstadoPagos.Lista(r.dato)
            is ResultadoEvento.Error -> EstadoPagos.Error(r.mensaje)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BaniterioWordmark()
            Text("Volver", color = BaniterioColors.brandBright,
                modifier = Modifier.clickable { onVolver() })
        }
        Text("Confirmar pagos", style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        aviso?.let { Text(it, color = BaniterioColors.gold) }

        when (val e = estado) {
            is EstadoPagos.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
            is EstadoPagos.Error -> {
                Text(e.mensaje, color = BaniterioColors.muted)
                Button(onClick = { intento++ }) { Text("Reintentar") }
            }
            is EstadoPagos.Lista -> {
                if (e.pendientes.isEmpty()) {
                    Text("No hay pagos pendientes de confirmar.", color = BaniterioColors.muted)
                }
                e.pendientes.forEach { p ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .relieveDeCarta(RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "${p.declaradoPor} · ${formatoImporte(p.importe)} € · ${metodoLegible(p.metodoPago)}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(p.eventoNombre, color = BaniterioColors.muted,
                            style = MaterialTheme.typography.bodySmall)
                        if (p.cubre.size > 1) {
                            Text("Cubre: " + p.cubre.joinToString(", ") { it.nombre },
                                color = BaniterioColors.muted,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        when (eventosRepo.confirmarPagoDeclarado(p.id)) {
                                            is ResultadoEvento.Exito -> {
                                                aviso = "Pago de ${p.declaradoPor} confirmado."; intento++
                                            }
                                            is ResultadoEvento.Error -> aviso = "No se pudo confirmar."
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BaniterioColors.brand,
                                    contentColor = BaniterioColors.gold,
                                ),
                            ) { Text("Confirmar") }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    when (eventosRepo.rechazarPagoDeclarado(p.id)) {
                                        is ResultadoEvento.Exito -> {
                                            aviso = "Pago de ${p.declaradoPor} rechazado."; intento++
                                        }
                                        is ResultadoEvento.Error -> aviso = "No se pudo rechazar."
                                    }
                                }
                            }) { Text("Rechazar") }
                        }
                    }
                }
            }
        }
    }
}
