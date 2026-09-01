package com.baniterio.app.ui.miembros

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.PerfilRepository
import com.baniterio.app.data.ResultadoPerfil
import com.baniterio.app.data.dto.TarjetaMiembroResponse
import com.baniterio.app.data.dto.VinculoPendiente
import com.baniterio.app.data.urlMedia
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.ui.comun.CaraDeCarta
import kotlinx.coroutines.launch

private sealed interface EstadoMiembros {
    data object Cargando : EstadoMiembros
    data class Cargada(val tarjetas: List<TarjetaMiembroResponse>, val pendiente: VinculoPendiente?) : EstadoMiembros
    data class Error(val mensaje: String) : EstadoMiembros
}

/**
 * Sección Miembros: rejilla de tarjetas con forma de carta (mismas que el editor
 * y que la web), en el orden que da el backend (yo → pareja → hijos → resto). Un
 * banner arriba si otra persona ha declarado que sois pareja y falta mi
 * confirmación.
 */
@Composable
fun MiembrosScreen(
    perfilRepo: PerfilRepository,
    miId: Long?,
    onEditar: () -> Unit,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoMiembros>(EstadoMiembros.Cargando) }
    var procesando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun cargar(mostrarCargando: Boolean = true) {
        if (mostrarCargando) estado = EstadoMiembros.Cargando
        val tarjetas = perfilRepo.miembros()
        if (tarjetas is ResultadoPerfil.Error) {
            estado = EstadoMiembros.Error(tarjetas.mensaje); return
        }
        // El perfil solo se usa para el banner; si falla, se sigue sin él.
        val pendiente = (perfilRepo.miPerfil() as? ResultadoPerfil.Exito)?.dato?.vinculoPendiente
        estado = EstadoMiembros.Cargada((tarjetas as ResultadoPerfil.Exito).dato, pendiente)
    }

    LaunchedEffect(Unit) { cargar() }

    fun responderVinculo(aceptar: Boolean) {
        procesando = true
        scope.launch {
            val r = if (aceptar) perfilRepo.aceptarPareja() else perfilRepo.rechazarPareja()
            if (r is ResultadoPerfil.Error) aviso = r.mensaje
            procesando = false
            cargar(mostrarCargando = false)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BaniterioWordmark()
            Text(
                text = "Volver",
                color = BaniterioColors.brandBright,
                modifier = Modifier.clickable { onVolver() },
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Miembros de la peña",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))

        aviso?.let {
            Text(it, color = BaniterioColors.brandBright, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
        }

        when (val e = estado) {
            is EstadoMiembros.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
            is EstadoMiembros.Error -> Column {
                Text(e.mensaje, color = BaniterioColors.error)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Reintentar",
                    color = BaniterioColors.brandBright,
                    modifier = Modifier.clickable { scope.launch { cargar() } },
                )
            }
            is EstadoMiembros.Cargada -> {
                e.pendiente?.let { v ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BaniterioColors.brandDark)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "${v.solicitanteNombre} dice que sois pareja.",
                            color = BaniterioColors.goldSoft,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { responderVinculo(true) },
                                enabled = !procesando,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BaniterioColors.brand,
                                    contentColor = BaniterioColors.gold,
                                ),
                            ) { Text("Confirmar", fontWeight = FontWeight.Bold) }
                            TextButton(onClick = { responderVinculo(false) }, enabled = !procesando) {
                                Text("Rechazar", color = BaniterioColors.brandBright)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(e.tarjetas, key = { it.id }) { t ->
                        TarjetaMiembro(t = t, esLaMia = t.id == miId, onEditar = onEditar)
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaMiembro(t: TarjetaMiembroResponse, esLaMia: Boolean, onEditar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BaniterioColors.panel),
    ) {
        CaraDeCarta(
            modelo = urlMedia(t.imagenUrl),
            iniciales = ((t.nombre.firstOrNull()?.toString() ?: "") +
                (t.apellidos.firstOrNull()?.toString() ?: "")).uppercase(),
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "${t.nombre} ${t.apellidos}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            t.mote?.takeIf { it.isNotBlank() }?.let {
                Text("«$it»", color = BaniterioColors.muted, style = MaterialTheme.typography.bodySmall)
            }
            t.sobreMi?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = BaniterioColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            t.parejaNombre?.let {
                Text(
                    buildString { append("Pareja: "); append(it) },
                    color = BaniterioColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (t.hijos.isNotEmpty()) {
                Text(
                    "Hijos: ${t.hijos.joinToString(", ")}",
                    color = BaniterioColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (esLaMia) {
                Text(
                    "Editar",
                    color = BaniterioColors.brandBright,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp).clickable { onEditar() },
                )
            }
        }
    }
}
