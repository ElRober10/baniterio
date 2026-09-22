package com.baniterio.app.ui.preciobebida

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.PrecioBebidaRepository
import com.baniterio.app.data.ResultadoPrecioBebida
import com.baniterio.app.data.dto.GrillaArticuloDto
import com.baniterio.app.data.dto.TiendaDto
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.ui.comun.CabeceraPantalla
import com.baniterio.app.ui.comun.EstadoCarga
import com.baniterio.app.ui.comun.PantallaConEstado
import com.baniterio.app.ui.comun.relieveDeCarta
import kotlinx.coroutines.launch

/** Tamaños de botella de refresco; el primero es el de por defecto. */
private val TAMANOS_LITROS = listOf(2.0, 1.5, 1.0)

private val ETIQUETAS = mapOf(
    "REFRESCOS" to "Refrescos",
    "CERVEZA" to "Para alternar",
    "LIMPIEZA" to "Limpieza y utensilios",
    "COMIDA" to "Comida",
)

/** Los platos y los vasos se venden en packs: se apunta cuántas unidades trae. */
private val ARTICULOS_EN_PACK = setOf(
    "Platos",
    "Vasos de chupito",
    "Vasos de invitar",
    "Vasos de mini",
    "Vasos de sidra",
)

/**
 * Rejilla de precios de una sección sin tamaños (refrescos, cerveza, limpieza
 * y utensilios, comida). Filas = artículos, cada uno con un campo de precio
 * por tienda. Misma idea que la rejilla de alcohol pero sin pestañas de
 * tamaño: aquí cada artículo se compra en un único formato. Equivalente a
 * `front/.../precio-bebidas/articulo/`.
 */
@Composable
fun PrecioArticuloScreen(
    precioBebidaRepo: PrecioBebidaRepository,
    eventoId: Long,
    categoria: String,
    onVolver: () -> Unit,
) {
    var estado by remember(categoria) { mutableStateOf<EstadoCarga<GrillaArticuloDto>>(EstadoCarga.Cargando) }
    var intento by remember(categoria) { mutableStateOf(0) }
    var ocupado by remember { mutableStateOf(false) }
    var mensajeError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(categoria, intento) {
        estado = EstadoCarga.Cargando
        estado = when (val r = precioBebidaRepo.articulos(eventoId, categoria)) {
            is ResultadoPrecioBebida.Exito -> EstadoCarga.Cargado(r.dato)
            is ResultadoPrecioBebida.Error -> EstadoCarga.Error(r.mensaje)
        }
    }

    fun llevaTamano(articulo: String): Boolean =
        categoria == "REFRESCOS" || (categoria == "CERVEZA" && articulo == "Tinto de verano")

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CabeceraPantalla(onVolver)
        Text(
            ETIQUETAS[categoria] ?: categoria,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        mensajeError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        PantallaConEstado(estado, onReintentar = { intento++ }) { grilla ->
            if (grilla.puedoEditar) {
                OutlinedButton(enabled = !ocupado, onClick = {
                    ocupado = true
                    scope.launch {
                        when (val r = precioBebidaRepo.crearTienda("Nueva tienda")) {
                            is ResultadoPrecioBebida.Exito -> intento++
                            is ResultadoPrecioBebida.Error -> mensajeError = r.mensaje
                        }
                        ocupado = false
                    }
                }) { Text("+ Tienda") }
            }

            val porKiloNombres = grilla.porKilo.map { it.nombreArticulo }.toSet()
            val articulosTienda = grilla.articulos.filter { it !in porKiloNombres }

            if (articulosTienda.isEmpty() && grilla.porKilo.isEmpty()) {
                Text("No hay artículos para esta sección todavía.", color = BaniterioColors.muted)
            }

            articulosTienda.forEach { a ->
                TarjetaArticulo(
                    articulo = a,
                    tiendas = grilla.tiendas,
                    llevaTamano = llevaTamano(a),
                    tamanoActual = grilla.tamanos.firstOrNull { it.nombreArticulo == a }?.litros ?: TAMANOS_LITROS[0],
                    conPack = categoria == "LIMPIEZA" && a in ARTICULOS_EN_PACK,
                    precioDe = { tiendaId -> grilla.precios.firstOrNull { it.nombreArticulo == a && it.tiendaId == tiendaId }?.precio },
                    cantidadDe = { tiendaId -> grilla.precios.firstOrNull { it.nombreArticulo == a && it.tiendaId == tiendaId }?.cantidad ?: 1 },
                    puedoEditar = grilla.puedoEditar,
                    onGuardarPrecio = { tiendaId, texto ->
                        val precio = texto.trim().replace(',', '.').let { if (it.isEmpty()) null else it.toDoubleOrNull() }
                        if (texto.trim().isNotEmpty() && precio == null) return@TarjetaArticulo
                        val cantidad = grilla.precios.firstOrNull { it.nombreArticulo == a && it.tiendaId == tiendaId }?.cantidad ?: 1
                        scope.launch {
                            when (val r = precioBebidaRepo.guardarPrecioArticulo(eventoId, categoria, a, tiendaId, precio, cantidad)) {
                                is ResultadoPrecioBebida.Exito -> intento++
                                is ResultadoPrecioBebida.Error -> mensajeError = r.mensaje
                            }
                        }
                    },
                    onGuardarCantidad = { tiendaId, texto ->
                        val precio = grilla.precios.firstOrNull { it.nombreArticulo == a && it.tiendaId == tiendaId }?.precio
                        val cantidad = texto.trim().let { if (it.isEmpty()) 1 else it.toIntOrNull() }
                        if (precio == null || cantidad == null || cantidad < 1) return@TarjetaArticulo
                        scope.launch {
                            when (val r = precioBebidaRepo.guardarPrecioArticulo(eventoId, categoria, a, tiendaId, precio, cantidad)) {
                                is ResultadoPrecioBebida.Exito -> intento++
                                is ResultadoPrecioBebida.Error -> mensajeError = r.mensaje
                            }
                        }
                    },
                    onGuardarTamano = { litros ->
                        scope.launch {
                            when (val r = precioBebidaRepo.guardarTamanoArticulo(eventoId, categoria, a, litros)) {
                                is ResultadoPrecioBebida.Exito -> intento++
                                is ResultadoPrecioBebida.Error -> mensajeError = r.mensaje
                            }
                        }
                    },
                )
            }

            if (grilla.porKilo.isNotEmpty()) {
                Text(
                    "Jamones Duriber",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BaniterioColors.gold,
                )
                Text(
                    "Se compra allí, al peso: precio por kilo × peso estimado de la pieza.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BaniterioColors.muted,
                )
                grilla.porKilo.forEach { k ->
                    TarjetaKilo(
                        nombreArticulo = k.nombreArticulo,
                        precioKilo = k.precioKilo,
                        pesoKg = k.pesoKg,
                        puedoEditar = grilla.puedoEditar,
                        onGuardar = { precioKilo, pesoKg ->
                            scope.launch {
                                when (val r = precioBebidaRepo.guardarProductoKilo(eventoId, k.nombreArticulo, precioKilo, pesoKg)) {
                                    is ResultadoPrecioBebida.Exito -> intento++
                                    is ResultadoPrecioBebida.Error -> mensajeError = r.mensaje
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaArticulo(
    articulo: String,
    tiendas: List<TiendaDto>,
    llevaTamano: Boolean,
    tamanoActual: Double,
    conPack: Boolean,
    precioDe: (Long) -> Double?,
    cantidadDe: (Long) -> Int,
    puedoEditar: Boolean,
    onGuardarPrecio: (Long, String) -> Unit,
    onGuardarCantidad: (Long, String) -> Unit,
    onGuardarTamano: (Double) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .relieveDeCarta(RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(articulo, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            if (llevaTamano) {
                if (puedoEditar) {
                    var abierto by remember { mutableStateOf(false) }
                    Column {
                        OutlinedButton(onClick = { abierto = true }) { Text("$tamanoActual L") }
                        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                            TAMANOS_LITROS.forEach { l ->
                                DropdownMenuItem(text = { Text("$l L") }, onClick = { abierto = false; onGuardarTamano(l) })
                            }
                        }
                    }
                } else {
                    Text("$tamanoActual L", color = BaniterioColors.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        tiendas.forEach { t ->
            val precio = precioDe(t.id)
            val cantidad = cantidadDe(t.id)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t.nombre, color = BaniterioColors.muted, style = MaterialTheme.typography.bodyMedium)
                if (puedoEditar) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        var texto by remember(articulo, t.id, precio) { mutableStateOf(precio?.toString() ?: "") }
                        var tuvoFoco by remember(articulo, t.id) { mutableStateOf(false) }
                        OutlinedTextField(
                            value = texto,
                            onValueChange = { texto = it },
                            modifier = Modifier.width(90.dp).onFocusChanged { f ->
                                if (f.isFocused) {
                                    tuvoFoco = true
                                } else if (tuvoFoco) {
                                    tuvoFoco = false
                                    onGuardarPrecio(t.id, texto)
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        if (conPack) {
                            var textoCant by remember(articulo, t.id, cantidad) {
                                mutableStateOf(if (cantidad == 1) "" else cantidad.toString())
                            }
                            var tuvoFocoCant by remember(articulo, t.id) { mutableStateOf(false) }
                            OutlinedTextField(
                                value = textoCant,
                                onValueChange = { textoCant = it },
                                enabled = precio != null,
                                placeholder = { Text("pack") },
                                modifier = Modifier.width(60.dp).onFocusChanged { f ->
                                    if (f.isFocused) {
                                        tuvoFocoCant = true
                                    } else if (tuvoFocoCant) {
                                        tuvoFocoCant = false
                                        onGuardarCantidad(t.id, textoCant)
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                    }
                } else {
                    Text(
                        buildString {
                            append(precio?.let { "$it €" } ?: "—")
                            if (conPack && cantidad > 1) append(" / pack de $cantidad")
                        },
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaKilo(
    nombreArticulo: String,
    precioKilo: Double?,
    pesoKg: Double?,
    puedoEditar: Boolean,
    onGuardar: (Double, Double) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .relieveDeCarta(RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(nombreArticulo, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        var textoKilo by remember(nombreArticulo, precioKilo) { mutableStateOf(precioKilo?.toString() ?: "") }
        var textoPeso by remember(nombreArticulo, pesoKg) { mutableStateOf(pesoKg?.toString() ?: "") }

        fun intentarGuardar() {
            val kilo = textoKilo.trim().replace(',', '.').toDoubleOrNull()
            val peso = textoPeso.trim().replace(',', '.').toDoubleOrNull()
            if (kilo != null && peso != null) onGuardar(kilo, peso)
        }

        if (puedoEditar) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                var tuvoFocoKilo by remember(nombreArticulo) { mutableStateOf(false) }
                OutlinedTextField(
                    value = textoKilo,
                    onValueChange = { textoKilo = it },
                    label = { Text("€/kg") },
                    modifier = Modifier.width(100.dp).onFocusChanged { f ->
                        if (f.isFocused) tuvoFocoKilo = true
                        else if (tuvoFocoKilo) { tuvoFocoKilo = false; intentarGuardar() }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                var tuvoFocoPeso by remember(nombreArticulo) { mutableStateOf(false) }
                OutlinedTextField(
                    value = textoPeso,
                    onValueChange = { textoPeso = it },
                    label = { Text("kg estimado") },
                    modifier = Modifier.width(110.dp).onFocusChanged { f ->
                        if (f.isFocused) tuvoFocoPeso = true
                        else if (tuvoFocoPeso) { tuvoFocoPeso = false; intentarGuardar() }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        } else {
            Text("${precioKilo ?: "—"} €/kg · ${pesoKg ?: "—"} kg", color = BaniterioColors.muted)
        }

        val pieza = if (precioKilo != null && pesoKg != null) {
            val redondeado = kotlin.math.round(precioKilo * pesoKg * 100) / 100
            "$redondeado €"
        } else "—"
        Text("Precio pieza: $pieza", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    }
}
