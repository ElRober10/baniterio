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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.CuentasRepository
import com.baniterio.app.data.ResultadoCuenta
import com.baniterio.app.data.dto.CuentaDetalleDto
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

@Composable
fun CuentaDetalleScreen(
    cuentasRepo: CuentasRepository,
    cuentaId: Long,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoCuentaDetalle>(EstadoCuentaDetalle.Cargando) }
    var intento by remember { mutableStateOf(0) }
    var confirmando by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .relieveDeCarta(RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        c.nombre,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    c.descripcion?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        "${formatoImporte(c.saldo)} €",
                        style = MaterialTheme.typography.headlineMedium,
                        color = BaniterioColors.gold,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Estimado cuando todos paguen: ${formatoImporte(c.estimacion)} €",
                        style = MaterialTheme.typography.bodySmall,
                        color = BaniterioColors.muted,
                    )
                }

                val porIngresar = c.porIngresar ?: 0.0
                if (c.puedoGestionar && porIngresar > 0.0) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .relieveDeCarta(RoundedCornerShape(18.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Tienes ${formatoImporte(porIngresar)} € cobrados por bizum o efectivo " +
                                "sin ingresar en la cuenta de la peña.",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { confirmando = true }) {
                            Text("He transferido el dinero a la peña")
                        }
                    }
                }

                Text(
                    "Movimientos",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                if (c.movimientos.isEmpty()) {
                    Text("Todavía no hay movimientos.", color = BaniterioColors.muted)
                } else {
                    c.movimientos.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.concepto, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    m.fecha,
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
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmando) {
        AlertDialog(
            onDismissRequest = { confirmando = false },
            confirmButton = {
                TextButton(
                    enabled = !guardando,
                    onClick = {
                        guardando = true
                        scope.launch {
                            when (val r = cuentasRepo.marcarTransferido(cuentaId)) {
                                is ResultadoCuenta.Exito -> {
                                    estado = EstadoCuentaDetalle.Cargada(r.dato)
                                    confirmando = false
                                }
                                is ResultadoCuenta.Error -> Unit
                            }
                            guardando = false
                        }
                    },
                ) { Text("Sí") }
            },
            dismissButton = { TextButton(onClick = { confirmando = false }) { Text("No") } },
            title = { Text("¿Seguro que has hecho la transferencia?") },
            text = {
                Text(
                    "Todo lo cobrado por bizum o efectivo pasará a constar como ingresado " +
                        "en la cuenta de la peña.",
                )
            },
        )
    }
}
