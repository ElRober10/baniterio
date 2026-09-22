package com.baniterio.app.ui.compra

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.ListaCompraRepository
import com.baniterio.app.data.ResultadoListaCompra
import com.baniterio.app.data.dto.CategoriaListaCompraDto
import com.baniterio.app.data.dto.LineaCompraDto
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.ui.comun.CabeceraPantalla
import com.baniterio.app.ui.comun.EstadoCarga
import com.baniterio.app.ui.comun.PantallaConEstado
import com.baniterio.app.ui.comun.relieveDeCarta
import kotlinx.coroutines.launch

private data class DatosLista(
    val puedoEditar: Boolean,
    val bloqueada: Boolean,
    val apuntados: Int,
    val diasFiesta: Int,
    val categorias: List<CategoriaListaCompraDto>,
    /** Saldo de la cuenta del evento en su año en curso. */
    val presupuesto: Double?,
)

/** Qué línea se está ajustando y con qué desplegable (tamaño, tienda o cantidad). */
private sealed class DialogoLinea(val linea: LineaCompraDto) {
    class Tamano(l: LineaCompraDto) : DialogoLinea(l)
    class Tienda(l: LineaCompraDto) : DialogoLinea(l)
    class Cantidad(l: LineaCompraDto) : DialogoLinea(l)
}

/** Tamaños entre los que se puede cambiar una botella cuando la marca aún no tiene precios. */
private val TAMANOS_BASE = listOf("70 cl", "1 L")

/** Quita el ".0" de las cantidades enteras. */
private fun fmt(d: Double): String =
    if (d % 1.0 == 0.0) d.toLong().toString() else fmtDec(d)

/** Hasta dos decimales, sin ceros de sobra (equivalente al pipe `1.0-2`). */
private fun fmtDec(d: Double): String {
    val redondeado = kotlin.math.round(d * 100) / 100.0
    if (redondeado % 1.0 == 0.0) return redondeado.toLong().toString()
    val negativo = redondeado < 0
    val abs = kotlin.math.abs(redondeado)
    val centimos = kotlin.math.round((abs % 1.0) * 100).toLong()
    val texto = if (centimos % 10 == 0L) {
        "${abs.toLong()},${(centimos / 10)}"
    } else {
        "${abs.toLong()},${centimos.toString().padStart(2, '0')}"
    }
    return if (negativo) "-$texto" else texto
}

private fun fmtEuros(d: Double): String {
    val redondeado = kotlin.math.round(d * 100) / 100.0
    val negativo = redondeado < 0
    val abs = kotlin.math.abs(redondeado)
    val texto = if (abs % 1.0 == 0.0) {
        "${abs.toLong()},00"
    } else {
        val centimos = kotlin.math.round((abs % 1.0) * 100).toLong()
        "${abs.toLong()},${centimos.toString().padStart(2, '0')}"
    }
    return "${if (negativo) "-" else ""}$texto €"
}

/** El admin puede ajustar una línea con la lista bloqueada y aún sin comprar. */
private fun puedeAjustar(datos: DatosLista, l: LineaCompraDto): Boolean =
    datos.bloqueada && datos.puedoEditar && !l.comprada

private fun hayModificadas(categorias: List<CategoriaListaCompraDto>): Boolean =
    categorias.any { c -> c.lineas.any { it.modificada } }

/** El menor precio por litro entre los tamaños de una línea de bebida (para marcar el más barato). */
private fun menorPrecioLitro(l: LineaCompraDto): Double? =
    l.info?.tamanos?.mapNotNull { it.precioLitro }?.minOrNull()

/** Si alguna línea de la categoría tiene precio, se pintan las columnas de precio/total. */
private fun tienePrecios(c: CategoriaListaCompraDto): Boolean =
    c.lineas.any { it.precioUnitario != null }

private fun hayPrecios(categorias: List<CategoriaListaCompraDto>): Boolean =
    categorias.any { tienePrecios(it) }

/**
 * Suma cantidad × precio unitario de las líneas de la categoría que tengan precio y aún no
 * estén compradas: lo ya comprado tiene un coste real (el ticket en Cuentas) y ya no es estimado.
 */
private fun totalEstimado(c: CategoriaListaCompraDto): Double =
    c.lineas.filter { !it.comprada && it.precioUnitario != null }.sumOf { it.cantidad * it.precioUnitario!! }

private fun totalGeneral(categorias: List<CategoriaListaCompraDto>): Double =
    categorias.sumOf { totalEstimado(it) }

/** Líneas que hay que comprar pero aún no tienen precio (no entran en el total). */
private fun lineasSinPrecio(categorias: List<CategoriaListaCompraDto>): Int =
    categorias.sumOf { c ->
        c.lineas.count { !it.comprada && it.cantidad > 0 && it.precioUnitario == null && !it.necesitaFicha }
    }

/**
 * Lista de la compra calculada de un evento. La ve cualquier peñista; el
 * admin (área INVENTARIO) puede además marcar "comprado" y, con la lista
 * bloqueada, ajustar a mano el tamaño, la tienda o la cantidad final de una
 * línea (p.ej. cuando ya sabe que el stock parcial que queda alcanza).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListaCompraScreen(
    listaCompraRepo: ListaCompraRepository,
    eventoId: Long,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoCarga<DatosLista>>(EstadoCarga.Cargando) }
    var intento by remember { mutableStateOf(0) }
    var ocupado by remember { mutableStateOf(false) }
    var dialogo by remember { mutableStateOf<DialogoLinea?>(null) }
    val scope = rememberCoroutineScope()

    fun recargarTras(bloque: suspend () -> Unit) {
        if (ocupado) return
        ocupado = true
        scope.launch {
            bloque()
            ocupado = false
            dialogo = null
            intento++
        }
    }

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
                        r.dato.presupuesto,
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !ocupado,
                        onClick = {
                            recargarTras { listaCompraRepo.cambiarBloqueo(eventoId, !datos.bloqueada) }
                        },
                    ) { Text(if (datos.bloqueada) "Desbloquear lista" else "Bloquear lista") }
                    if (hayModificadas(datos.categorias)) {
                        OutlinedButton(
                            enabled = !ocupado,
                            onClick = {
                                recargarTras { listaCompraRepo.restablecerTodo(eventoId) }
                            },
                        ) { Text("↺ Restablecer todo") }
                    }
                }
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
                        Column(Modifier.fillMaxWidth()) {
                            FilaLinea(
                                datos = datos,
                                l = l,
                                ocupado = ocupado,
                                onAbrirTamano = { dialogo = DialogoLinea.Tamano(l) },
                                onAbrirTienda = { dialogo = DialogoLinea.Tienda(l) },
                                onAbrirCantidad = { dialogo = DialogoLinea.Cantidad(l) },
                                onRestablecer = {
                                    recargarTras { listaCompraRepo.restablecerLinea(eventoId, l.id) }
                                },
                                onComprado = {
                                    recargarTras { listaCompraRepo.marcarComprada(eventoId, l.id) }
                                },
                            )
                        }
                        HorizontalDivider(color = BaniterioColors.outline)
                    }
                    if (tienePrecios(c)) {
                        Text(
                            "Gasto estimado en ${c.etiqueta.lowercase()}: ${fmtEuros(totalEstimado(c))}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = BaniterioColors.gold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
            if (hayPrecios(datos.categorias)) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .relieveDeCarta(RoundedCornerShape(14.dp)).padding(16.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        "Gasto estimado total: ${fmtEuros(totalGeneral(datos.categorias))}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BaniterioColors.gold,
                    )
                    datos.presupuesto?.let { presupuesto ->
                        val restante = presupuesto - totalGeneral(datos.categorias)
                        Text(
                            "Presupuesto (saldo de la cuenta): ${fmtEuros(presupuesto)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = BaniterioColors.muted,
                        )
                        Text(
                            (if (restante >= 0) "Quedarían " else "Nos pasamos en ") +
                                fmtEuros(if (restante >= 0) restante else -restante),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (restante >= 0) BaniterioColors.gold else BaniterioColors.error,
                        )
                    }
                    val sinPrecio = lineasSinPrecio(datos.categorias)
                    if (sinPrecio > 0) {
                        Text(
                            "No incluye $sinPrecio " +
                                if (sinPrecio == 1) "línea sin precio." else "líneas sin precio.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BaniterioColors.muted,
                        )
                    }
                }
            }
        }
    }

    dialogo?.let { d ->
        DialogoAjusteLinea(
            dialogo = d,
            ocupado = ocupado,
            onCerrar = { dialogo = null },
            onGuardarTamano = { tamano ->
                recargarTras { listaCompraRepo.ajustarLinea(eventoId, d.linea.id, d.linea.cantidad, tamano = tamano) }
            },
            onGuardarTienda = { tienda ->
                recargarTras { listaCompraRepo.ajustarLinea(eventoId, d.linea.id, d.linea.cantidad, tienda = tienda) }
            },
            onGuardarCantidad = { cantidad ->
                recargarTras { listaCompraRepo.ajustarLinea(eventoId, d.linea.id, cantidad) }
            },
        )
    }
}

/**
 * Un dato editable de la línea (tamaño, tienda, cantidad): pastilla con borde que se ajusta
 * al texto, en vez de un `TextButton`/`OutlinedButton` — esos fuerzan un ancho mínimo grande
 * (pensado para botones de acción) que descuadraba filas cortas como "70 cl".
 */
@Composable
private fun ChipEditable(
    texto: String,
    ocupado: Boolean,
    destacado: Boolean = false,
    onClick: () -> Unit,
) {
    val estilo = if (destacado) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, BaniterioColors.outline, RoundedCornerShape(8.dp))
            .clickable(enabled = !ocupado, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texto, style = estilo, fontWeight = if (destacado) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        Text(" ▾", style = estilo, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaLinea(
    datos: DatosLista,
    l: LineaCompraDto,
    ocupado: Boolean,
    onAbrirTamano: () -> Unit,
    onAbrirTienda: () -> Unit,
    onAbrirCantidad: () -> Unit,
    onRestablecer: () -> Unit,
    onComprado: () -> Unit,
) {
    val ajustable = puedeAjustar(datos, l)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(
                l.nombre,
                color = if (l.comprada) BaniterioColors.muted else MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            if (ajustable) {
                ChipEditable(fmt(l.cantidad), ocupado, destacado = true, onClick = onAbrirCantidad)
            } else {
                Text(fmt(l.cantidad), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        // Tamaño, tienda, detalle del pack, aviso de ficha y "ajustado": todo en una fila que
        // envuelve si hace falta, con el mismo estilo compacto para que no queden desalineados.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (ajustable && l.info?.tamanos != null) {
                ChipEditable(l.tamano, ocupado, onClick = onAbrirTamano)
            } else {
                Text(l.tamano, style = MaterialTheme.typography.bodySmall, color = BaniterioColors.muted)
            }
            l.tienda?.let { tienda ->
                if (ajustable && !l.tiendas.isNullOrEmpty()) {
                    ChipEditable(tienda, ocupado, onClick = onAbrirTienda)
                } else {
                    Text(tienda, style = MaterialTheme.typography.bodySmall, color = BaniterioColors.muted)
                }
            }
            l.detalle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = BaniterioColors.muted)
            }
            if (l.necesitaFicha) {
                Text("necesita ficha de bebida", style = MaterialTheme.typography.bodySmall, color = BaniterioColors.muted)
            }
            if (l.ajustada) {
                Text(
                    "ajustado",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = BaniterioColors.goldSoft,
                )
            }
        }

        if (l.precioUnitario != null) {
            Text(
                "${fmtEuros(l.precioUnitario)}/ud · total ${fmtEuros(l.cantidad * l.precioUnitario)}",
                style = MaterialTheme.typography.bodySmall,
                color = BaniterioColors.muted,
            )
        }

        if (datos.puedoEditar) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (l.comprada) {
                    Text("✓ Comprada", style = MaterialTheme.typography.bodySmall, color = BaniterioColors.gold)
                } else {
                    if (l.modificada) {
                        TextButton(
                            enabled = !ocupado,
                            onClick = onRestablecer,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("↺ Restablecer", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
                        }
                    }
                    if (l.cantidad > 0.0) {
                        OutlinedButton(
                            enabled = !ocupado,
                            onClick = onComprado,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("Comprado", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoAjusteLinea(
    dialogo: DialogoLinea,
    ocupado: Boolean,
    onCerrar: () -> Unit,
    onGuardarTamano: (String) -> Unit,
    onGuardarTienda: (String) -> Unit,
    onGuardarCantidad: (Double) -> Unit,
) {
    val l = dialogo.linea
    when (dialogo) {
        is DialogoLinea.Cantidad -> {
            var cantidad by remember(l.id) { mutableStateOf(l.cantidad) }
            AlertDialog(
                onDismissRequest = onCerrar,
                title = { Text("Ajustar cantidad") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (l.info != null) {
                            Text(
                                "Personas que beben ${l.nombre}: ${fmtDec(l.info.personas)} " +
                                    "(dos días = 1, un día = 0,5)",
                                style = MaterialTheme.typography.bodySmall,
                                color = BaniterioColors.muted,
                            )
                            Text(
                                "En stock: ${fmtDec(l.info.stock)} botellas",
                                style = MaterialTheme.typography.bodySmall,
                                color = BaniterioColors.muted,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { cantidad = (cantidad - 1.0).coerceAtLeast(0.0) }) { Text("−") }
                            OutlinedTextField(
                                value = fmt(cantidad),
                                onValueChange = { cantidad = it.replace(',', '.').toDoubleOrNull() ?: cantidad },
                                modifier = Modifier.width(90.dp),
                                singleLine = true,
                            )
                            OutlinedButton(onClick = { cantidad += 1.0 }) { Text("+") }
                        }
                    }
                },
                confirmButton = {
                    TextButton(enabled = !ocupado && cantidad >= 0.0, onClick = { onGuardarCantidad(cantidad) }) {
                        Text("Guardar")
                    }
                },
                dismissButton = { TextButton(enabled = !ocupado, onClick = onCerrar) { Text("Cancelar") } },
            )
        }

        is DialogoLinea.Tienda -> {
            var elegida by remember(l.id) { mutableStateOf(l.tienda ?: "") }
            AlertDialog(
                onDismissRequest = onCerrar,
                title = { Text("Dónde comprar") },
                text = {
                    Column(
                        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        l.tiendas?.forEach { o ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                RadioButton(selected = elegida == o.tienda, onClick = { elegida = o.tienda })
                                Column(Modifier.weight(1f)) {
                                    Text(o.tienda, fontWeight = FontWeight.SemiBold)
                                    val porUnidad = o.precioLitro ?: (o.precio / o.unidades)
                                    val etiquetaUnidad = if (o.precioLitro != null) "€/litro" else "por unidad"
                                    Text(
                                        "${fmtEuros(o.precio)}" +
                                            (if (o.unidades > 1) " / pack de ${o.unidades}" else "") +
                                            " · ${fmtDec(porUnidad)} $etiquetaUnidad",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BaniterioColors.muted,
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(enabled = !ocupado && elegida.isNotBlank(), onClick = { onGuardarTienda(elegida) }) {
                        Text("Guardar")
                    }
                },
                dismissButton = { TextButton(enabled = !ocupado, onClick = onCerrar) { Text("Cancelar") } },
            )
        }

        is DialogoLinea.Tamano -> {
            var elegido by remember(l.id) { mutableStateOf(l.tamano) }
            val tamanos = l.info?.tamanos.orEmpty()
            AlertDialog(
                onDismissRequest = onCerrar,
                title = { Text("Tamaño de la botella") },
                text = {
                    if (tamanos.isEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Sin precios apuntados para esta marca.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BaniterioColors.muted,
                            )
                            TAMANOS_BASE.forEach { t ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = elegido == t, onClick = { elegido = t })
                                    Text(t)
                                }
                            }
                        }
                    } else {
                        val masBarato = menorPrecioLitro(l)
                        Column(
                            modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            tamanos.forEach { o ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    RadioButton(selected = elegido == o.tamano, onClick = { elegido = o.tamano })
                                    Column(Modifier.weight(1f)) {
                                        Text(o.tamano, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${o.tienda} · ${fmtEuros(o.precio)} · ${fmtDec(o.precioLitro)} €/litro" +
                                                if (o.precioLitro == masBarato) " ★" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (o.precioLitro == masBarato) BaniterioColors.goldSoft else BaniterioColors.muted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(enabled = !ocupado, onClick = { onGuardarTamano(elegido) }) { Text("Guardar") }
                },
                dismissButton = { TextButton(enabled = !ocupado, onClick = onCerrar) { Text("Cancelar") } },
            )
        }
    }
}
