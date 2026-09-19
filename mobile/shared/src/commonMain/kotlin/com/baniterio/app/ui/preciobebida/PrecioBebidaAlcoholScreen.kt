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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.PrecioBebidaRepository
import com.baniterio.app.data.ResultadoPrecioBebida
import com.baniterio.app.data.dto.BebidaRefDto
import com.baniterio.app.data.dto.GrillaAlcoholDto
import com.baniterio.app.data.dto.TiendaDto
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.ui.comun.CabeceraPantalla
import com.baniterio.app.ui.comun.EstadoCarga
import com.baniterio.app.ui.comun.PantallaConEstado
import com.baniterio.app.ui.comun.relieveDeCarta
import kotlinx.coroutines.launch

/**
 * Rejilla de precios de bebidas alcohólicas de un evento: una pestaña por
 * tamaño, una tarjeta por marca con un campo de precio por tienda. Solo edita
 * quien puede (`puedoEditar`). Equivalente a la tabla de la web, en formato de
 * lista porque una tabla ancha no cabe en una pantalla de móvil.
 */
@Composable
fun PrecioBebidaAlcoholScreen(
    precioBebidaRepo: PrecioBebidaRepository,
    eventoId: Long,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoCarga<GrillaAlcoholDto>>(EstadoCarga.Cargando) }
    var intento by remember { mutableStateOf(0) }
    var tamanoActivo by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var mensajeError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(intento) {
        estado = EstadoCarga.Cargando
        estado = when (val r = precioBebidaRepo.alcohol(eventoId)) {
            is ResultadoPrecioBebida.Exito -> {
                if (tamanoActivo !in r.dato.tamanos) tamanoActivo = r.dato.tamanos.firstOrNull() ?: ""
                EstadoCarga.Cargado(r.dato)
            }
            is ResultadoPrecioBebida.Error -> EstadoCarga.Error(r.mensaje)
        }
    }

    fun guardar(bebidaId: Long, tiendaId: Long, texto: String) {
        val precio = texto.trim().replace(',', '.').let { if (it.isEmpty()) null else it.toDoubleOrNull() }
        if (texto.trim().isNotEmpty() && precio == null) return
        scope.launch {
            when (val r = precioBebidaRepo.guardarPrecio(eventoId, bebidaId, tamanoActivo, tiendaId, precio)) {
                is ResultadoPrecioBebida.Exito -> intento++
                is ResultadoPrecioBebida.Error -> mensajeError = r.mensaje
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CabeceraPantalla(onVolver)
        Text(
            "Bebidas alcohólicas",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        mensajeError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        PantallaConEstado(estado, onReintentar = { intento++ }) { grilla ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                grilla.tamanos.forEach { t ->
                    val activo = t == tamanoActivo
                    OutlinedButton(onClick = { tamanoActivo = t }) {
                        Text(t, fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            if (grilla.puedoEditar) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    OutlinedButton(enabled = !ocupado, onClick = {
                        ocupado = true
                        scope.launch {
                            when (val r = precioBebidaRepo.anadirTamano(eventoId, "Nuevo tamaño")) {
                                is ResultadoPrecioBebida.Exito -> intento++
                                is ResultadoPrecioBebida.Error -> mensajeError = r.mensaje
                            }
                            ocupado = false
                        }
                    }) { Text("+ Tamaño") }
                }
            }

            if (grilla.bebidas.isEmpty()) {
                Text("No hay marcas de alcohol aceptadas en el catálogo.", color = BaniterioColors.muted)
            }
            grilla.bebidas.forEach { b ->
                TarjetaBebida(
                    bebida = b,
                    tiendas = grilla.tiendas,
                    tamanoActivo = tamanoActivo,
                    precioDe = { tiendaId ->
                        grilla.precios.firstOrNull {
                            it.bebidaId == b.id && it.tamano == tamanoActivo && it.tiendaId == tiendaId
                        }?.precio
                    },
                    puedoEditar = grilla.puedoEditar,
                    onGuardar = { tiendaId, texto -> guardar(b.id, tiendaId, texto) },
                )
            }
        }
    }
}

@Composable
private fun TarjetaBebida(
    bebida: BebidaRefDto,
    tiendas: List<TiendaDto>,
    tamanoActivo: String,
    precioDe: (Long) -> Double?,
    puedoEditar: Boolean,
    onGuardar: (Long, String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .relieveDeCarta(RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(bebida.nombre, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        tiendas.forEach { t ->
            val precio = precioDe(t.id)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t.nombre, color = BaniterioColors.muted, style = MaterialTheme.typography.bodyMedium)
                if (puedoEditar) {
                    var texto by remember(bebida.id, t.id, tamanoActivo, precio) {
                        mutableStateOf(precio?.toString() ?: "")
                    }
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it },
                        modifier = Modifier.width(90.dp).onFocusChanged { f ->
                            if (!f.isFocused) onGuardar(t.id, texto)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                } else {
                    Text(
                        precio?.let { "$it €" } ?: "—",
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}
