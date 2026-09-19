package com.baniterio.app.ui.compra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.ListaCompraRepository
import com.baniterio.app.data.ResultadoListaCompra
import com.baniterio.app.data.dto.CategoriaListaCompraDto
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.ui.comun.CabeceraPantalla
import com.baniterio.app.ui.comun.EstadoCarga
import com.baniterio.app.ui.comun.PantallaConEstado
import com.baniterio.app.ui.comun.relieveDeCarta

private data class DatosLista(
    val puedoEditar: Boolean,
    val bloqueada: Boolean,
    val apuntados: Int,
    val diasFiesta: Int,
    val categorias: List<CategoriaListaCompraDto>,
)

/** Quita el ".0" de las cantidades enteras. */
private fun fmt(d: Double): String =
    if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

private fun fmtEuros(d: Double): String {
    val redondeado = kotlin.math.round(d * 100) / 100.0
    val texto = if (redondeado % 1.0 == 0.0) {
        "${redondeado.toLong()},00"
    } else {
        val centimos = kotlin.math.round((redondeado % 1.0) * 100).toLong().let { if (it < 0) -it else it }
        "${redondeado.toLong()},${centimos.toString().padStart(2, '0')}"
    }
    return "$texto €"
}

/** Suma cantidad × precio unitario de las líneas de la categoría que tengan precio. */
private fun totalEstimado(c: CategoriaListaCompraDto): Double =
    c.lineas.filter { it.precioUnitario != null }.sumOf { it.cantidad * it.precioUnitario!! }

/**
 * Lista de la compra calculada de un evento. La ve cualquier peñista; el
 * admin puede además marcar "comprado" y, con la lista bloqueada, ajustar a
 * mano la cantidad final de una línea (p.ej. cuando el stock parcial alcanza).
 */
@Composable
fun ListaCompraScreen(
    listaCompraRepo: ListaCompraRepository,
    eventoId: Long,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoCarga<DatosLista>>(EstadoCarga.Cargando) }
    var intento by remember { mutableStateOf(0) }
    var ocupado by remember { mutableStateOf(false) }
    var editandoId by remember { mutableStateOf<Long?>(null) }
    var cantidadTexto by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(intento) {
        estado = EstadoCarga.Cargando
        estado = when (val r = listaCompraRepo.lista(eventoId)) {
            is ResultadoListaCompra.Exito ->
                EstadoCarga.Cargado(
                    DatosLista(
                        r.dato.puedoEditar,
                        r.dato.bloqueada,
                        r.dato.apuntados,
                        r.dato.diasFiesta,
                        r.dato.categorias,
                    ),
                )
            is ResultadoListaCompra.Error -> EstadoCarga.Error(r.mensaje)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CabeceraPantalla(onVolver)
        Text(
            "Lista de la compra",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )

        PantallaConEstado(estado, onReintentar = { intento++ }) { datos ->
            Text(
                "${datos.apuntados} apuntados · ${datos.diasFiesta} día(s) de fiesta" +
                    if (datos.bloqueada) " · lista bloqueada" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = BaniterioColors.muted,
            )
            if (datos.puedoEditar) {
                OutlinedButton(
                    enabled = !ocupado,
                    onClick = {
                        ocupado = true
                        scope.launch {
                            listaCompraRepo.cambiarBloqueo(eventoId, !datos.bloqueada)
                            ocupado = false
                            intento++
                        }
                    },
                ) { Text(if (datos.bloqueada) "Desbloquear lista" else "Bloquear lista") }
            }
            if (datos.categorias.isEmpty()) {
                Text("Todavía no hay nada que comprar para este evento.", color = BaniterioColors.muted)
            }
            datos.categorias.forEach { c ->
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
                    c.lineas.forEach { l ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(l.nombre, color = MaterialTheme.colorScheme.onBackground)
                                val sub = buildString {
                                    append(l.tamano)
                                    if (l.tienda != null) append(" · ${l.tienda}")
                                    if (l.necesitaFicha) append(" · necesita ficha de bebida")
                                    if (l.ajustada) append(" · ajustado")
                                }
                                Text(
                                    sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BaniterioColors.muted,
                                )
                                if (l.precioUnitario != null) {
                                    Text(
                                        "${fmtEuros(l.precioUnitario)}/ud · total ${fmtEuros(l.cantidad * l.precioUnitario)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BaniterioColors.muted,
                                    )
                                }
                            }
                            if (editandoId == l.id) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = cantidadTexto,
                                        onValueChange = { cantidadTexto = it },
                                        modifier = Modifier.width(80.dp),
                                        singleLine = true,
                                    )
                                    OutlinedButton(
                                        enabled = !ocupado,
                                        modifier = Modifier.padding(start = 8.dp),
                                        onClick = {
                                            val cantidad = cantidadTexto.replace(',', '.').toDoubleOrNull()
                                            if (cantidad != null && cantidad >= 0.0) {
                                                ocupado = true
                                                scope.launch {
                                                    listaCompraRepo.ajustarLinea(eventoId, l.id, cantidad)
                                                    ocupado = false
                                                    editandoId = null
                                                    intento++
                                                }
                                            }
                                        },
                                    ) { Text("Guardar", maxLines = 1, softWrap = false) }
                                    OutlinedButton(
                                        enabled = !ocupado,
                                        modifier = Modifier.padding(start = 4.dp),
                                        onClick = { editandoId = null },
                                    ) { Text("Cancelar", maxLines = 1, softWrap = false) }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        fmt(l.cantidad),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                        modifier = Modifier.width(24.dp),
                                    )
                                    if (datos.puedoEditar) {
                                        Box(
                                            modifier = Modifier.width(132.dp).padding(start = 12.dp),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            if (l.comprada) {
                                                Text(
                                                    "✓ Comprada",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = BaniterioColors.gold,
                                                )
                                            } else {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (datos.bloqueada) {
                                                        OutlinedButton(
                                                            enabled = !ocupado,
                                                            onClick = {
                                                                editandoId = l.id
                                                                cantidadTexto = fmt(l.cantidad)
                                                            },
                                                        ) { Text("Ajustar", maxLines = 1, softWrap = false) }
                                                    }
                                                    if (l.cantidad > 0.0) {
                                                        OutlinedButton(
                                                            enabled = !ocupado,
                                                            modifier = Modifier.padding(start = 4.dp),
                                                            onClick = {
                                                                ocupado = true
                                                                scope.launch {
                                                                    listaCompraRepo.marcarComprada(eventoId, l.id)
                                                                    ocupado = false
                                                                    intento++
                                                                }
                                                            },
                                                        ) { Text("Comprado", maxLines = 1, softWrap = false) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (c.categoria == "ALCOHOL") {
                        Text(
                            "Gasto estimado en alcohol: ${fmtEuros(totalEstimado(c))}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = BaniterioColors.gold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                }
            }
        }
    }
}
