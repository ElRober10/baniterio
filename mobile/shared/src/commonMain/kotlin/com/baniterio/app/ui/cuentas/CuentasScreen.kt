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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.CuentasRepository
import com.baniterio.app.data.ResultadoCuenta
import com.baniterio.app.data.dto.CuentaResumen
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.ui.comun.relieveDeCarta

private sealed interface EstadoCuentas {
    data object Cargando : EstadoCuentas
    data class Cargada(val cuentas: List<CuentaResumen>) : EstadoCuentas
    data class Error(val mensaje: String) : EstadoCuentas
}

@Composable
fun CuentasScreen(
    cuentasRepo: CuentasRepository,
    onAbrirCuenta: (Long) -> Unit,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoCuentas>(EstadoCuentas.Cargando) }
    var intento by remember { mutableStateOf(0) }

    LaunchedEffect(intento) {
        estado = EstadoCuentas.Cargando
        estado = when (val r = cuentasRepo.listar()) {
            is ResultadoCuenta.Exito -> EstadoCuentas.Cargada(r.dato)
            is ResultadoCuenta.Error -> EstadoCuentas.Error(r.mensaje)
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
        Text(
            "Cuentas",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "El dinero de cada evento que se repite: lo que sobra de un año sirve para el siguiente.",
            style = MaterialTheme.typography.bodyMedium,
            color = BaniterioColors.muted,
        )

        when (val e = estado) {
            is EstadoCuentas.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
            is EstadoCuentas.Error -> {
                Text(e.mensaje, color = BaniterioColors.muted)
                Button(onClick = { intento++ }) { Text("Reintentar") }
            }
            is EstadoCuentas.Cargada -> {
                e.cuentas.forEach { c -> TarjetaCuenta(c) { onAbrirCuenta(c.id) } }
                if (e.cuentas.isEmpty()) {
                    Text("Todavía no hay cuentas.", color = BaniterioColors.muted)
                }
            }
        }
    }
}

@Composable
private fun TarjetaCuenta(c: CuentaResumen, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .relieveDeCarta(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                c.nombre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            c.descripcion?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = BaniterioColors.muted)
            }
        }
        Text("→", color = BaniterioColors.muted)
    }
}
