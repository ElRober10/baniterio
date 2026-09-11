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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.PerfilRepository
import com.baniterio.app.data.ResultadoPerfil
import com.baniterio.app.data.dto.TarjetaMiembroResponse
import com.baniterio.app.data.dto.VinculoPendiente
import com.baniterio.app.data.urlMedia
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.ui.comun.CaraDeCarta
import com.baniterio.app.ui.comun.EstadoCarga
import com.baniterio.app.ui.comun.PantallaConEstado
import com.baniterio.app.ui.comun.relieveDeCarta
import kotlinx.coroutines.launch

private data class DatosMiembros(val tarjetas: List<TarjetaMiembroResponse>, val pendiente: VinculoPendiente?)

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
    var estado by remember { mutableStateOf<EstadoCarga<DatosMiembros>>(EstadoCarga.Cargando) }
    var procesando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun cargar(mostrarCargando: Boolean = true) {
        if (mostrarCargando) estado = EstadoCarga.Cargando
        val tarjetas = perfilRepo.miembros()
        if (tarjetas is ResultadoPerfil.Error) {
            estado = EstadoCarga.Error(tarjetas.mensaje); return
        }
        // El perfil solo se usa para el banner; si falla, se sigue sin él.
        val pendiente = (perfilRepo.miPerfil() as? ResultadoPerfil.Exito)?.dato?.vinculoPendiente
        estado = EstadoCarga.Cargado(DatosMiembros((tarjetas as ResultadoPerfil.Exito).dato, pendiente))
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
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

        PantallaConEstado(estado, onReintentar = { scope.launch { cargar() } }) { datos ->
            run {
                datos.pendiente?.let { v ->
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

                RejillaAlturaIgual(
                    items = datos.tarjetas,
                    columnas = 2,
                    espacio = 16.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) { t ->
                    TarjetaMiembro(t = t, esLaMia = t.id == miId, onEditar = onEditar)
                }
            }
        }
    }
}

/**
 * Rejilla de [columnas] columnas donde TODAS las celdas miden lo mismo de alto:
 * el de la celda más alta con su contenido natural (no se recorta nada). No es
 * lazy —la lista de miembros de una peña es corta— y se apoya en un
 * [SubcomposeLayout] para medir dos veces: primero cada celda a su alto natural
 * y luego todas fijadas al máximo, así el fondo de cada tarjeta llena su hueco.
 */
@Composable
private fun <T> RejillaAlturaIgual(
    items: List<T>,
    columnas: Int,
    espacio: Dp,
    modifier: Modifier = Modifier,
    celda: @Composable (T) -> Unit,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val gap = espacio.roundToPx()
        val anchoCol = ((constraints.maxWidth - gap * (columnas - 1)) / columnas).coerceAtLeast(0)
        val medida = Constraints(maxWidth = anchoCol)

        val alturaMax = subcompose("medir") { items.forEach { celda(it) } }
            .maxOfOrNull { it.measure(medida).height } ?: 0

        val fijo = Constraints.fixed(anchoCol, alturaMax)
        val placeables = subcompose("pintar") { items.forEach { celda(it) } }
            .map { it.measure(fijo) }

        val filas = if (items.isEmpty()) 0 else (items.size + columnas - 1) / columnas
        val alto = filas * alturaMax + (filas - 1).coerceAtLeast(0) * gap

        layout(constraints.maxWidth, alto) {
            placeables.forEachIndexed { i, p ->
                val fila = i / columnas
                val col = i % columnas
                p.place(x = col * (anchoCol + gap), y = fila * (alturaMax + gap))
            }
        }
    }
}

@Composable
private fun TarjetaMiembro(t: TarjetaMiembroResponse, esLaMia: Boolean, onEditar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .relieveDeCarta(RoundedCornerShape(18.dp)),
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
