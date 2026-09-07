package com.baniterio.app.ui.cuentas

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.API_BASE_URL
import com.baniterio.app.data.CuentasRepository
import com.baniterio.app.data.ResultadoCuenta
import com.baniterio.app.data.dto.CuentaDetalleDto
import com.baniterio.app.data.dto.PenistaCuotaDto
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.ui.comun.relieveDeCarta
import com.baniterio.app.ui.eventos.formatoImporte
import kotlinx.coroutines.launch

private sealed interface EstadoCuentaDetalle {
    data object Cargando : EstadoCuentaDetalle
    data class Cargada(val cuenta: CuentaDetalleDto) : EstadoCuentaDetalle
    data class Error(val mensaje: String) : EstadoCuentaDetalle
}

private fun estadoTexto(e: String?) = when (e) {
    "DECLARADO" -> "Pagado, pendiente de confirmar"
    "CONFIRMADO_PENDIENTE_ENVIO" -> "Confirmado, pendiente de ingresar en la cuenta"
    "CONFIRMADO_EN_CUENTA" -> "Confirmado y en la cuenta"
    else -> "Pendiente de pago"
}

private fun confirmado(p: PenistaCuotaDto) =
    p.estadoPago == "CONFIRMADO_EN_CUENTA" || p.estadoPago == "CONFIRMADO_PENDIENTE_ENVIO"

@Composable
fun CuentaDetalleScreen(
    cuentasRepo: CuentasRepository,
    cuentaId: Long,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoCuentaDetalle>(EstadoCuentaDetalle.Cargando) }
    var intento by remember { mutableStateOf(0) }
    var confirmandoTransfer by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(cuentaId, intento) {
        estado = EstadoCuentaDetalle.Cargando
        estado = when (val r = cuentasRepo.detalle(cuentaId)) {
            is ResultadoCuenta.Exito -> EstadoCuentaDetalle.Cargada(r.dato)
            is ResultadoCuenta.Error -> EstadoCuentaDetalle.Error(r.mensaje)
        }
    }

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

        when (val e = estado) {
            is EstadoCuentaDetalle.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
            is EstadoCuentaDetalle.Error -> {
                Text(e.mensaje, color = BaniterioColors.muted)
                Button(onClick = { intento++ }) { Text("Reintentar") }
            }
            is EstadoCuentaDetalle.Cargada -> {
                val c = e.cuenta

                // Cabecera
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .relieveDeCarta(RoundedCornerShape(18.dp)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        c.nombre,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${formatoImporte(c.saldo)} €",
                        style = MaterialTheme.typography.headlineMedium,
                        color = BaniterioColors.gold,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Si pagan todos: ${formatoImporte(c.estimacion)} €",
                        style = MaterialTheme.typography.bodySmall,
                        color = BaniterioColors.muted,
                    )
                    val cobrado = c.cobradoSinIngresar ?: 0.0
                    if (c.puedoGestionar && cobrado > 0.0) {
                        Text(
                            "Cobrado sin llevar al banco: ${formatoImporte(cobrado)} €",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Button(onClick = { confirmandoTransfer = true }) {
                            Text("He transferido al banco")
                        }
                    }
                }

                // Peñistas
                Text(
                    "Peñistas (${c.penistas.count(::confirmado)} de ${c.penistas.size} han pagado)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                c.penistas.forEach { p ->
                    Column {
                        Text("${p.nombre} · ${formatoImporte(p.cuota)} €", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            estadoTexto(p.estadoPago) +
                                (if (confirmado(p) && p.metodoPago != null) " · ${p.metodoPago}" else "") +
                                (if (p.camisetaPagada) " · camiseta ✓" else "") +
                                (if (p.sudaderaPagada) " · sudadera ✓" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = BaniterioColors.muted,
                        )
                    }
                }
                Text(
                    "Total en cuotas: ${formatoImporte(c.totalCuotas)} € · cobrado: ${formatoImporte(c.totalCobrado)} €",
                    style = MaterialTheme.typography.bodySmall,
                    color = BaniterioColors.muted,
                )

                // Movimientos
                Text(
                    "Movimientos",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                c.movimientos.forEach { m ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(m.concepto, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                m.fecha + (m.categoria?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = BaniterioColors.muted,
                            )
                        }
                        Column {
                            Text("${formatoImporte(m.importe)} €", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${formatoImporte(m.saldoTras)} €",
                                style = MaterialTheme.typography.bodySmall,
                                color = BaniterioColors.muted,
                            )
                            if (m.reciboArchivo != null) {
                                Text(
                                    "Ver recibo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BaniterioColors.brandBright,
                                    modifier = Modifier.clickable {
                                        uriHandler.openUri("$API_BASE_URL/media/recibos/${m.reciboArchivo}")
                                    },
                                )
                            }
                        }
                    }
                }

                if (c.resumenGastos.isNotEmpty()) {
                    Text(
                        "Resumen de gastos",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    c.resumenGastos.forEach { r ->
                        Text(
                            "${r.categoria}: ${formatoImporte(r.total)} €",
                            style = MaterialTheme.typography.bodySmall,
                            color = BaniterioColors.muted,
                        )
                    }
                }
            }
        }
    }

    if (confirmandoTransfer) {
        AlertDialog(
            onDismissRequest = { confirmandoTransfer = false },
            confirmButton = {
                TextButton(enabled = !guardando, onClick = {
                    guardando = true
                    scope.launch {
                        when (val r = cuentasRepo.marcarTransferido(cuentaId)) {
                            is ResultadoCuenta.Exito -> {
                                estado = EstadoCuentaDetalle.Cargada(r.dato)
                                confirmandoTransfer = false
                            }
                            is ResultadoCuenta.Error -> Unit
                        }
                        guardando = false
                    }
                }) { Text("Sí") }
            },
            dismissButton = { TextButton(onClick = { confirmandoTransfer = false }) { Text("No") } },
            title = { Text("¿Seguro que has hecho la transferencia?") },
            text = { Text("Solo apaga el aviso; el saldo no cambia.") },
        )
    }
}
