package com.baniterio.app.ui.inventario

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
import androidx.compose.material3.OutlinedButton
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
import com.baniterio.app.data.InventarioRepository
import com.baniterio.app.data.ResultadoInventario
import com.baniterio.app.data.dto.ArticuloFiestaDto
import com.baniterio.app.data.dto.CategoriaFiestaDto
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.ui.comun.relieveDeCarta
import kotlinx.coroutines.launch

private sealed interface EstadoFiesta {
    data object Cargando : EstadoFiesta
    data class Cargada(val puedoEditar: Boolean, val categorias: List<CategoriaFiestaDto>) : EstadoFiesta
    data class Error(val mensaje: String) : EstadoFiesta
}

/** Quita el ".0" de las cantidades enteras. */
private fun fmtFiesta(d: Double): String =
    if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

/**
 * Inventario que se ha enviado a un evento ("inventario de la fiesta"). Lo ve
 * cualquier apuntado; el botón "Devolver" de cada línea sólo sale con permiso
 * (`puedoEditar`) y pide confirmación.
 */
@Composable
fun InventarioFiestaScreen(
    inventarioRepo: InventarioRepository,
    eventoId: Long,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoFiesta>(EstadoFiesta.Cargando) }
    var intento by remember { mutableStateOf(0) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var confirmar by remember { mutableStateOf<ArticuloFiestaDto?>(null) }
    var confirmarLista by remember { mutableStateOf<ArticuloFiestaDto?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(intento) {
        estado = EstadoFiesta.Cargando
        estado = when (val r = inventarioRepo.inventarioFiesta(eventoId)) {
            is ResultadoInventario.Exito -> EstadoFiesta.Cargada(r.dato.puedoEditar, r.dato.categorias)
            is ResultadoInventario.Error -> EstadoFiesta.Error(r.mensaje)
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
            "Inventario de la fiesta",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Lo que se ha llevado del inventario de la peña a este evento.",
            style = MaterialTheme.typography.bodyMedium,
            color = BaniterioColors.muted,
        )

        aviso?.let { Text(it, color = BaniterioColors.gold) }

        when (val e = estado) {
            is EstadoFiesta.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
            is EstadoFiesta.Error -> {
                Text(e.mensaje, color = BaniterioColors.muted)
                Button(onClick = { intento++ }) { Text("Reintentar") }
            }
            is EstadoFiesta.Cargada -> {
                if (e.categorias.isEmpty()) {
                    Text("Todavía no se ha enviado nada a este evento.", color = BaniterioColors.muted)
                }
                e.categorias.forEach { c ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .relieveDeCarta(RoundedCornerShape(14.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            c.etiqueta,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        c.articulos.forEach { a ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(a.nombre, color = MaterialTheme.colorScheme.onBackground)
                                    Text(
                                        a.tamano,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BaniterioColors.muted,
                                    )
                                }
                                Text(
                                    fmtFiesta(a.cantidad),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                            if (e.puedoEditar) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (a.cantidad - a.cantidadComprada > 0.0) {
                                        OutlinedButton(onClick = { confirmar = a }) {
                                            Text("Devolver a inventario")
                                        }
                                    }
                                    if (a.cantidadComprada > 0.0) {
                                        OutlinedButton(onClick = { confirmarLista = a }) {
                                            Text("Devolver a la lista")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmar?.let { art ->
        AlertDialog(
            onDismissRequest = { confirmar = null },
            confirmButton = {
                TextButton(onClick = {
                    confirmar = null
                    scope.launch {
                        aviso = null
                        when (val r = inventarioRepo.devolver(eventoId, art.id)) {
                            is ResultadoInventario.Exito -> Unit
                            is ResultadoInventario.Error -> aviso = r.mensaje
                        }
                        intento++
                    }
                }) { Text("Devolver") }
            },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar") } },
            title = { Text("Devolver a inventario") },
            text = { Text("¿Devolver «${art.nombre}» al inventario general?") },
        )
    }

    confirmarLista?.let { art ->
        AlertDialog(
            onDismissRequest = { confirmarLista = null },
            confirmButton = {
                TextButton(onClick = {
                    confirmarLista = null
                    scope.launch {
                        aviso = null
                        when (val r = inventarioRepo.devolverALista(eventoId, art.id)) {
                            is ResultadoInventario.Exito -> Unit
                            is ResultadoInventario.Error -> aviso = r.mensaje
                        }
                        intento++
                    }
                }) { Text("Devolver") }
            },
            dismissButton = { TextButton(onClick = { confirmarLista = null }) { Text("Cancelar") } },
            title = { Text("Devolver a la lista de la compra") },
            text = { Text("¿Devolver «${art.nombre}» a la lista de la compra?") },
        )
    }
}
