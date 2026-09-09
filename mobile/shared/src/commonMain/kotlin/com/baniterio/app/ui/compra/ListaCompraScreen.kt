package com.baniterio.app.ui.compra

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
import com.baniterio.app.data.ListaCompraRepository
import com.baniterio.app.data.ResultadoListaCompra
import com.baniterio.app.data.dto.CategoriaListaCompraDto
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.ui.comun.relieveDeCarta

private sealed interface EstadoLista {
    data object Cargando : EstadoLista
    data class Cargada(val apuntados: Int, val diasFiesta: Int, val categorias: List<CategoriaListaCompraDto>) : EstadoLista
    data class Error(val mensaje: String) : EstadoLista
}

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
    var estado by remember { mutableStateOf<EstadoLista>(EstadoLista.Cargando) }
    var intento by remember { mutableStateOf(0) }

    LaunchedEffect(intento) {
        estado = EstadoLista.Cargando
        estado = when (val r = listaCompraRepo.lista(eventoId)) {
            is ResultadoListaCompra.Exito ->
                EstadoLista.Cargada(r.dato.apuntados, r.dato.diasFiesta, r.dato.categorias)
            is ResultadoListaCompra.Error -> EstadoLista.Error(r.mensaje)
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
            "Lista de la compra",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )

        when (val e = estado) {
            is EstadoLista.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
            is EstadoLista.Error -> {
                Text(e.mensaje, color = BaniterioColors.muted)
                Button(onClick = { intento++ }) { Text("Reintentar") }
            }
            is EstadoLista.Cargada -> {
                Text(
                    "${e.apuntados} apuntados · ${e.diasFiesta} día(s) de fiesta",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BaniterioColors.muted,
                )
                if (e.categorias.isEmpty()) {
                    Text("Todavía no hay nada que comprar para este evento.", color = BaniterioColors.muted)
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
                            }
                        }
                    }
                }
            }
        }
    }
}
