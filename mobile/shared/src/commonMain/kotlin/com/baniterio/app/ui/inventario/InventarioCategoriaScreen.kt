package com.baniterio.app.ui.inventario

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.baniterio.app.data.CodigoErrorInventario
import com.baniterio.app.data.InventarioRepository
import com.baniterio.app.data.ResultadoInventario
import com.baniterio.app.data.dto.ArticuloInventarioDto
import com.baniterio.app.data.dto.CategoriaInventarioDto
import com.baniterio.app.data.dto.EventoAbiertoDto
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.ui.comun.CabeceraPantalla
import com.baniterio.app.ui.comun.CajaSelect
import com.baniterio.app.ui.comun.relieveDeCarta
import kotlinx.coroutines.launch

/** Valor del desplegable de nombres que activa el input de "nombre nuevo". */
private const val NOMBRE_NUEVO = "__nuevo__"

/** Qué se envía a un evento: un artículo concreto o toda la categoría. */
private sealed interface EnvioObjetivo {
    data class Uno(val articulo: ArticuloInventarioDto) : EnvioObjetivo
    data object Todo : EnvioObjetivo
}

private sealed interface EstadoCategoria {
    data object Cargando : EstadoCategoria
    data class Cargada(val cat: CategoriaInventarioDto, val puedoEditar: Boolean) : EstadoCategoria
    data class Error(val mensaje: String) : EstadoCategoria
}

private data class Fila(val nombre: String, val tamano: String, val cantidad: String)

/** Quita el ".0" de las cantidades enteras (192.0 -> "192", 1.5 -> "1.5"). */
private fun fmt(d: Double): String =
    if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

/**
 * Listado de UNA categoría del inventario. Pide el inventario entero
 * (`GET /api/v1/inventario`) y se queda con su categoría. Si el backend dice
 * `puedoEditar`, aparecen "Editar cantidades" y "Añadir artículo"; "Guardar"
 * manda un PUT por cada fila cambiada. Los botones "Enviar a evento" son
 * placeholders sin función todavía.
 */
@Composable
fun InventarioCategoriaScreen(
    inventarioRepo: InventarioRepository,
    categoria: CategoriaInventario,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoCategoria>(EstadoCategoria.Cargando) }
    var intento by remember { mutableStateOf(0) }
    var editando by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var borrador by remember { mutableStateOf<Map<Long, Fila>>(emptyMap()) }

    var modalAbierto by remember { mutableStateOf(false) }
    var confirmarBorrado by remember { mutableStateOf<ArticuloInventarioDto?>(null) }
    var enviarObjetivo by remember { mutableStateOf<EnvioObjetivo?>(null) }
    var eventosAbiertos by remember { mutableStateOf<List<EventoAbiertoDto>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(enviarObjetivo) {
        if (enviarObjetivo != null && eventosAbiertos.isEmpty()) {
            when (val r = inventarioRepo.eventosAbiertos()) {
                is ResultadoInventario.Exito -> eventosAbiertos = r.dato
                is ResultadoInventario.Error -> aviso = r.mensaje
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(intento) {
        estado = EstadoCategoria.Cargando
        editando = false
        estado = when (val r = inventarioRepo.ver()) {
            is ResultadoInventario.Exito -> {
                val cat = r.dato.categorias.firstOrNull { it.categoria == categoria.clave }
                if (cat == null) EstadoCategoria.Error("No se pudo cargar el listado.")
                else EstadoCategoria.Cargada(cat, r.dato.puedoEditar)
            }
            is ResultadoInventario.Error -> EstadoCategoria.Error(r.mensaje)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CabeceraPantalla(onVolver)
        Text(
            categoria.etiqueta,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )

        val e = estado
        if (e is EstadoCategoria.Cargada && e.puedoEditar) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!editando) {
                    OutlinedButton(onClick = {
                        borrador = e.cat.articulos.associate {
                            it.id to Fila(it.nombre, it.tamano, fmt(it.cantidad))
                        }
                        aviso = null
                        editando = true
                    }) { Text("Editar cantidades") }
                    OutlinedButton(onClick = { aviso = null; modalAbierto = true }) {
                        Text("Añadir artículo")
                    }
                } else {
                    Button(
                        enabled = !guardando,
                        onClick = {
                            scope.launch {
                                guardando = true
                                aviso = null
                                val cambiadas = e.cat.articulos.filter { a ->
                                    val f = borrador[a.id] ?: return@filter false
                                    f.nombre != a.nombre || f.tamano != a.tamano ||
                                        f.cantidad != fmt(a.cantidad)
                                }
                                var huboError = false
                                for (a in cambiadas) {
                                    val f = borrador.getValue(a.id)
                                    val cant = f.cantidad.replace(',', '.').toDoubleOrNull()
                                    if (cant == null || cant < 0) { huboError = true; continue }
                                    when (inventarioRepo.actualizar(a.id, f.nombre.trim(), f.tamano, cant)) {
                                        is ResultadoInventario.Exito -> Unit
                                        is ResultadoInventario.Error -> huboError = true
                                    }
                                }
                                guardando = false
                                if (huboError) aviso = "Algún cambio no se pudo guardar."
                                intento++
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BaniterioColors.gold,
                            contentColor = BaniterioColors.brandDark,
                        ),
                    ) { Text("Guardar") }
                    OutlinedButton(onClick = { editando = false; borrador = emptyMap() }) {
                        Text("Cancelar")
                    }
                }
            }
        }

        aviso?.let { Text(it, color = BaniterioColors.gold) }

        when (e) {
            is EstadoCategoria.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
            is EstadoCategoria.Error -> {
                Text(e.mensaje, color = BaniterioColors.muted)
                Button(onClick = { intento++ }) { Text("Reintentar") }
            }
            is EstadoCategoria.Cargada -> {
                if (e.cat.articulos.isEmpty()) {
                    Text("Nada apuntado todavía.", color = BaniterioColors.muted)
                }
                e.cat.articulos.forEach { a ->
                    FilaArticulo(
                        articulo = a,
                        tamanos = e.cat.tamanos,
                        editando = editando,
                        puedoEditar = e.puedoEditar,
                        fila = borrador[a.id],
                        guardando = guardando,
                        onCambio = { nueva -> borrador = borrador + (a.id to nueva) },
                        onBorrar = { confirmarBorrado = a },
                        onEnviar = { enviarObjetivo = EnvioObjetivo.Uno(a) },
                    )
                }
                if (e.puedoEditar && !editando && e.cat.articulos.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { enviarObjetivo = EnvioObjetivo.Todo }) {
                            Text("Enviar todo a evento")
                        }
                    }
                }
            }
        }
    }

    confirmarBorrado?.let { art ->
        AlertDialog(
            onDismissRequest = { confirmarBorrado = null },
            confirmButton = {
                TextButton(onClick = {
                    confirmarBorrado = null
                    scope.launch {
                        guardando = true
                        aviso = null
                        when (val r = inventarioRepo.borrar(art.id)) {
                            is ResultadoInventario.Exito -> Unit
                            is ResultadoInventario.Error -> aviso = r.mensaje
                        }
                        guardando = false
                        intento++
                    }
                }) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { confirmarBorrado = null }) { Text("Cancelar") } },
            title = { Text("Quitar del inventario") },
            text = { Text("¿Quitar «${art.nombre}» del inventario?") },
        )
    }

    enviarObjetivo?.let { objetivo ->
        var eventoSel by remember(objetivo) { mutableStateOf<Long?>(null) }
        AlertDialog(
            onDismissRequest = { enviarObjetivo = null },
            confirmButton = {
                TextButton(
                    enabled = eventoSel != null,
                    onClick = {
                        val ev = eventoSel ?: return@TextButton
                        enviarObjetivo = null
                        scope.launch {
                            val r = when (objetivo) {
                                is EnvioObjetivo.Uno ->
                                    inventarioRepo.enviarAEvento(objetivo.articulo.id, ev)
                                EnvioObjetivo.Todo ->
                                    inventarioRepo.enviarCategoria(categoria.clave, ev)
                            }
                            when (r) {
                                is ResultadoInventario.Exito -> {
                                    val n = eventosAbiertos.firstOrNull { it.id == ev }?.nombre ?: "el evento"
                                    aviso = "Enviado a «$n»."
                                }
                                is ResultadoInventario.Error -> aviso = r.mensaje
                            }
                            intento++
                        }
                    },
                ) { Text("Enviar") }
            },
            dismissButton = { TextButton(onClick = { enviarObjetivo = null }) { Text("Cancelar") } },
            title = { Text("Enviar a evento") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (objetivo) {
                            is EnvioObjetivo.Uno -> "${objetivo.articulo.nombre} · ${objetivo.articulo.tamano}"
                            EnvioObjetivo.Todo -> "Se enviará todo lo de «${categoria.etiqueta}»."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = BaniterioColors.muted,
                    )
                    if (eventosAbiertos.isEmpty()) {
                        Text("No hay eventos abiertos.", color = BaniterioColors.muted)
                    }
                    eventosAbiertos.forEach { ev ->
                        Row(
                            Modifier.fillMaxWidth().clickable { eventoSel = ev.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = eventoSel == ev.id, onClick = { eventoSel = ev.id })
                            Text(
                                "${ev.nombre} · ${ev.fecha}",
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
            },
        )
    }

    val cargada = estado as? EstadoCategoria.Cargada
    if (modalAbierto && cargada != null) {
        ModalAnadir(
            etiqueta = categoria.etiqueta,
            tamanos = cargada.cat.tamanos,
            nombresExistentes = cargada.cat.articulos.map { it.nombre }.distinct().sorted(),
            onCerrar = { modalAbierto = false },
            onCrear = { nombre, tamano, cantidad ->
                scope.launch {
                    when (val r = inventarioRepo.crear(categoria.clave, nombre, tamano, cantidad)) {
                        is ResultadoInventario.Exito -> {
                            modalAbierto = false
                            intento++
                        }
                        is ResultadoInventario.Error -> aviso = r.mensaje
                    }
                }
            },
        )
    }
}

@Composable
private fun FilaArticulo(
    articulo: ArticuloInventarioDto,
    tamanos: List<String>,
    editando: Boolean,
    puedoEditar: Boolean,
    fila: Fila?,
    guardando: Boolean,
    onCambio: (Fila) -> Unit,
    onBorrar: () -> Unit,
    onEnviar: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .relieveDeCarta(RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (editando && fila != null) {
            OutlinedTextField(
                value = fila.nombre,
                onValueChange = { onCambio(fila.copy(nombre = it)) },
                label = { Text("Artículo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            CajaSelect("Tamaño", fila.tamano, esPlaceholder = false) { cerrar ->
                tamanos.forEach { t ->
                    DropdownMenuItem(text = { Text(t) }, onClick = { onCambio(fila.copy(tamano = t)); cerrar() })
                }
            }
            OutlinedTextField(
                value = fila.cantidad,
                onValueChange = { onCambio(fila.copy(cantidad = it)) },
                label = { Text("Cantidad") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    enabled = !guardando,
                    onClick = onBorrar,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BaniterioColors.error),
                ) { Text("🗑  Quitar") }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(
                        articulo.nombre,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(articulo.tamano, style = MaterialTheme.typography.bodySmall, color = BaniterioColors.muted)
                }
                Text(
                    fmt(articulo.cantidad),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            if (puedoEditar) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onEnviar) { Text("Enviar a evento") }
                }
            }
        }
    }
}

@Composable
private fun ModalAnadir(
    etiqueta: String,
    tamanos: List<String>,
    nombresExistentes: List<String>,
    onCerrar: () -> Unit,
    onCrear: (nombre: String, tamano: String, cantidad: Double) -> Unit,
) {
    var nombreSel by remember { mutableStateOf("") }
    var nombreTexto by remember { mutableStateOf("") }
    var tamano by remember { mutableStateOf(tamanos.firstOrNull() ?: "") }
    var cantidad by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            TextButton(onClick = {
                val nombre = (if (nombreSel == NOMBRE_NUEVO) nombreTexto else nombreSel).trim()
                val cant = cantidad.replace(',', '.').toDoubleOrNull()
                error = when {
                    nombre.isEmpty() -> "Elige o escribe un nombre."
                    tamano.isEmpty() -> "Elige un tamaño."
                    cant == null || cant < 0 -> "Escribe una cantidad."
                    else -> null
                }
                if (error == null) onCrear(nombre, tamano, cant!!)
            }) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
        title = { Text("Añadir artículo · $etiqueta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val textoNombre = when (nombreSel) {
                    "" -> "— Elige uno —"
                    NOMBRE_NUEVO -> "Otro (nuevo)…"
                    else -> nombreSel
                }
                CajaSelect("Artículo", textoNombre, esPlaceholder = nombreSel == "") { cerrar ->
                    nombresExistentes.forEach { n ->
                        DropdownMenuItem(text = { Text(n) }, onClick = { nombreSel = n; cerrar() })
                    }
                    DropdownMenuItem(
                        text = { Text("Otro (nuevo)…") },
                        onClick = { nombreSel = NOMBRE_NUEVO; cerrar() },
                    )
                }
                if (nombreSel == NOMBRE_NUEVO) {
                    OutlinedTextField(
                        value = nombreTexto,
                        onValueChange = { nombreTexto = it },
                        label = { Text("Nombre nuevo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                CajaSelect("Tamaño", tamano.ifEmpty { "— Elige —" }, esPlaceholder = tamano.isEmpty()) { cerrar ->
                    tamanos.forEach { t ->
                        DropdownMenuItem(text = { Text(t) }, onClick = { tamano = t; cerrar() })
                    }
                }
                OutlinedTextField(
                    value = cantidad,
                    onValueChange = { cantidad = it },
                    label = { Text("Cantidad") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = BaniterioColors.gold) }
            }
        },
    )
}
