package com.baniterio.app.ui.compra

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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

/**
 * Lista de la compra calculada de un evento. Solo lectura; la ve cualquier
 * peñista. El ajuste de cantidades está en "Cantidades para eventos".
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
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(l.nombre, color = MaterialTheme.colorScheme.onBackground)
                                val sub = buildString {
                                    append(l.tamano)
                                    if (l.necesitaFicha) append(" · necesita ficha de bebida")
                                    if (l.ajustada) append(" · ajustado")
                                }
                                Text(
                                    sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BaniterioColors.muted,
                                )
                            }
                            Text(
                                fmt(l.cantidad),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            if (datos.puedoEditar) {
                                if (l.comprada) {
                                    Text(
                                        "✓ Comprada",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BaniterioColors.gold,
                                    )
                                } else if (l.cantidad > 0.0) {
                                    OutlinedButton(
                                        enabled = !ocupado,
                                        onClick = {
                                            ocupado = true
                                            scope.launch {
                                                listaCompraRepo.marcarComprada(eventoId, l.id)
                                                ocupado = false
                                                intento++
                                            }
                                        },
                                    ) { Text("Comprado") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
