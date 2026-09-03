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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.CuentasRepository
import com.baniterio.app.data.ResultadoCuenta
import com.baniterio.app.data.dto.CuentaResumen
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.ui.comun.relieveDeCarta

private sealed interface EstadoCuentaDetalle {
    data object Cargando : EstadoCuentaDetalle
    data class Cargada(val cuenta: CuentaResumen) : EstadoCuentaDetalle
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
                }
                Text(
                    "Los movimientos de la cuenta (ingresos, gastos y balance) todavía están en construcción.",
                    color = BaniterioColors.gold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
