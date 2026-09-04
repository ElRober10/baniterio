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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.BebidaRepository
import com.baniterio.app.data.ResultadoBebida
import com.baniterio.app.data.dto.BebidaPendienteDto
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.ui.comun.relieveDeCarta
import kotlinx.coroutines.launch

private sealed interface EstadoBebidas {
    data object Cargando : EstadoBebidas
    data class Lista(val pendientes: List<BebidaPendienteDto>) : EstadoBebidas
    data class Error(val mensaje: String) : EstadoBebidas
}

/**
 * Pantalla de admin: acepta o rechaza las bebidas que la gente propone con
 * "Otra…" en la ficha de San Miguel. Solo se llega desde el índice de
 * administración, que solo la ofrece a admins/superadmins.
 */
@Composable
fun AdminBebidasScreen(
    bebidaRepo: BebidaRepository,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoBebidas>(EstadoBebidas.Cargando) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var intento by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(intento) {
        estado = EstadoBebidas.Cargando
        estado = when (val r = bebidaRepo.pendientes()) {
            is ResultadoBebida.Exito -> EstadoBebidas.Lista(r.dato)
            is ResultadoBebida.Error -> EstadoBebidas.Error(r.mensaje)
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
        Text("Bebidas propuestas", style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        aviso?.let { Text(it, color = BaniterioColors.gold) }

        when (val e = estado) {
            is EstadoBebidas.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
            is EstadoBebidas.Error -> {
                Text(e.mensaje, color = BaniterioColors.muted)
                Button(onClick = { intento++ }) { Text("Reintentar") }
            }
            is EstadoBebidas.Lista -> {
                if (e.pendientes.isEmpty()) {
                    Text("No hay bebidas pendientes de revisar.", color = BaniterioColors.muted)
                }
                e.pendientes.forEach { b ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .relieveDeCarta(RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(b.nombre, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            (if (b.tipo == "ALCOHOL") "alcohol" else "refresco") +
                                (b.propuestaPor?.let { " · lo propuso ${it.nombre}" } ?: ""),
                            color = BaniterioColors.muted, style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        when (bebidaRepo.aceptar(b.id)) {
                                            is ResultadoBebida.Exito -> { aviso = "«${b.nombre}» aceptada."; intento++ }
                                            is ResultadoBebida.Error -> aviso = "No se pudo aceptar."
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BaniterioColors.brand,
                                    contentColor = BaniterioColors.gold,
                                ),
                            ) { Text("Aceptar") }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    when (bebidaRepo.rechazar(b.id)) {
                                        is ResultadoBebida.Exito -> { aviso = "«${b.nombre}» rechazada."; intento++ }
                                        is ResultadoBebida.Error -> aviso = "No se pudo rechazar."
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
