package com.baniterio.app.ui.cuentas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.API_BASE_URL
import com.baniterio.app.data.CuentasRepository
import com.baniterio.app.data.ResultadoCuenta
import com.baniterio.app.data.dto.CuentaDetalleDto
import com.baniterio.app.data.dto.MovimientoFilaDto
import com.baniterio.app.data.dto.PenistaCuotaDto
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.ui.comun.relieveDeCarta
import com.baniterio.app.ui.eventos.formatoFecha
import com.baniterio.app.ui.eventos.formatoImporte
import kotlinx.coroutines.launch

private sealed interface EstadoCuentaDetalle {
    data object Cargando : EstadoCuentaDetalle
    data class Cargada(val cuenta: CuentaDetalleDto) : EstadoCuentaDetalle
    data class Error(val mensaje: String) : EstadoCuentaDetalle
}

private fun estadoTexto(e: String?) = when (e) {
    "DECLARADO" -> "Pagado, pendiente de confirmar"
    "CONFIRMADO_PENDIENTE_ENVIO" -> "Confirmado, pendiente de ingresar en la cuenta"
    "CONFIRMADO_EN_CUENTA" -> "Confirmado y en la cuenta"
    else -> "Pendiente de pago"
}

private fun confirmado(p: PenistaCuotaDto) =
    p.estadoPago == "CONFIRMADO_EN_CUENTA" || p.estadoPago == "CONFIRMADO_PENDIENTE_ENVIO"

/** "2" · "2 · M" · "2 · M ✓" · "·" según cantidad / talla / confirmada. */
private fun ropaTexto(cantidad: Int, talla: String?, confirmada: Boolean): String {
    if (cantidad <= 0) return "·"
    val base = if (talla.isNullOrBlank()) "$cantidad" else "$cantidad · $talla"
    return if (confirmada) "$base ✓" else base
}

// Anchos de las 8 columnas de la hoja (réplica de la tabla de la web).
private val ANCHO_CONCEPTO = 200.dp
private val ANCHO_ESTADO = 150.dp
private val ANCHO_ROPA = 92.dp
private val ANCHO_DINERO = 84.dp
private val ANCHO_RECIBO = 68.dp
private val ANCHO_SALDO = 96.dp

@Composable
private fun Celda(
    texto: String,
    ancho: androidx.compose.ui.unit.Dp,
    align: TextAlign = TextAlign.Start,
    color: androidx.compose.ui.graphics.Color = BaniterioColors.ink,
    negrita: Boolean = false,
) {
    Text(
        texto,
        modifier = Modifier.width(ancho).padding(horizontal = 8.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = color,
        textAlign = align,
        fontWeight = if (negrita) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
private fun FilaSeccion(texto: String) {
    Text(
        texto.uppercase(),
        modifier = Modifier.fillMaxWidth().background(BaniterioColors.panel).padding(horizontal = 8.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelSmall,
        color = BaniterioColors.muted,
    )
}

@Composable
fun CuentaDetalleScreen(
    cuentasRepo: CuentasRepository,
    cuentaId: Long,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoCuentaDetalle>(EstadoCuentaDetalle.Cargando) }
    var intento by remember { mutableStateOf(0) }
    var anioSel by remember { mutableStateOf<Int?>(null) }
    var mostrarAniosViejos by remember { mutableStateOf(false) }
    var confirmandoTransfer by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(cuentaId, intento, anioSel) {
        estado = EstadoCuentaDetalle.Cargando
        estado = when (val r = cuentasRepo.detalle(cuentaId, anioSel)) {
            is ResultadoCuenta.Exito -> EstadoCuentaDetalle.Cargada(r.dato)
            is ResultadoCuenta.Error -> EstadoCuentaDetalle.Error(r.mensaje)
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

        when (val e = estado) {
            is EstadoCuentaDetalle.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
            is EstadoCuentaDetalle.Error -> {
                Text(e.mensaje, color = BaniterioColors.muted)
                Button(onClick = { intento++ }) { Text("Reintentar") }
            }
            is EstadoCuentaDetalle.Cargada -> {
                val c = e.cuenta

                // Cabecera
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .relieveDeCarta(RoundedCornerShape(18.dp)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        c.nombre,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )

                    // Botones de año: el que se ve, encendido.
                    if (c.anios.isNotEmpty()) {
                        val visibles = if (mostrarAniosViejos) c.anios else c.anios.take(5)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            visibles.forEach { a ->
                                ChipAnio(a, seleccionado = a == c.anio) {
                                    if (a != c.anio) anioSel = a
                                }
                            }
                            if (c.anios.size > 5 && !mostrarAniosViejos) {
                                ChipAnio("Años anteriores", seleccionado = false) {
                                    mostrarAniosViejos = true
                                }
                            }
                        }
                    }

                    Text(
                        "${formatoImporte(c.saldo)} €",
                        style = MaterialTheme.typography.headlineMedium,
                        color = BaniterioColors.gold,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Saldo del año ${c.anio}" +
                            (if (c.esAnioActual) " · si pagan todos: ${formatoImporte(c.estimacion)} €" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = BaniterioColors.muted,
                    )
                    val cobrado = c.cobradoSinIngresar ?: 0.0
                    if (c.puedoGestionar && cobrado > 0.0) {
                        Text(
                            "Tienes ${formatoImporte(cobrado)} € cobrados por bizum o efectivo sin llevar al banco.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Button(onClick = { confirmandoTransfer = true }) {
                            Text("He transferido al banco")
                        }
                    }
                }

                // La hoja: una sola tabla con scroll horizontal.
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .relieveDeCarta(RoundedCornerShape(14.dp))
                        .horizontalScroll(rememberScrollState()),
                ) {
                    // Cabecera de columnas
                    Row(Modifier.background(BaniterioColors.panel)) {
                        Celda("Peñista / concepto", ANCHO_CONCEPTO, color = BaniterioColors.muted, negrita = true)
                        Celda("Estado", ANCHO_ESTADO, color = BaniterioColors.muted, negrita = true)
                        Celda("Camiseta", ANCHO_ROPA, TextAlign.Center, BaniterioColors.muted, true)
                        Celda("Sudadera", ANCHO_ROPA, TextAlign.Center, BaniterioColors.muted, true)
                        Celda("Gasto", ANCHO_DINERO, TextAlign.End, BaniterioColors.muted, true)
                        Celda("Ingreso", ANCHO_DINERO, TextAlign.End, BaniterioColors.muted, true)
                        Celda("Recibo", ANCHO_RECIBO, TextAlign.Center, BaniterioColors.muted, true)
                        Celda("Saldo", ANCHO_SALDO, TextAlign.End, BaniterioColors.muted, true)
                    }

                    // Bloque 1: saldo de partida
                    Row {
                        Celda("Saldo del año anterior", ANCHO_CONCEPTO, color = BaniterioColors.muted)
                        Celda("", ANCHO_ESTADO)
                        Celda("", ANCHO_ROPA)
                        Celda("", ANCHO_ROPA)
                        Celda("", ANCHO_DINERO)
                        Celda("", ANCHO_DINERO)
                        Celda("", ANCHO_RECIBO)
                        Celda("${formatoImporte(c.saldoInicial)} €", ANCHO_SALDO, TextAlign.End, BaniterioColors.gold, true)
                    }

                    // Bloque 2: peñistas
                    FilaSeccion("Peñistas · ${c.penistas.count(::confirmado)} de ${c.penistas.size} han pagado")
                    if (c.penistas.isEmpty()) {
                        Celda("Nadie apuntado con cuota todavía.", ANCHO_CONCEPTO, color = BaniterioColors.muted)
                    }
                    c.penistas.forEach { p ->
                        Row {
                            Celda("${p.nombre} · cuota ${formatoImporte(p.cuota)} € · ${p.anio}", ANCHO_CONCEPTO)
                            Celda(
                                estadoTexto(p.estadoPago) +
                                    (if (confirmado(p) && p.metodoPago != null) " · ${p.metodoPago}" else ""),
                                ANCHO_ESTADO,
                                color = BaniterioColors.muted,
                            )
                            Celda(
                                ropaTexto(p.camisetaCantidad, p.camisetaTalla, p.camisetaConfirmada),
                                ANCHO_ROPA, TextAlign.Center,
                            )
                            Celda(
                                ropaTexto(p.sudaderaCantidad, p.sudaderaTalla, p.sudaderaConfirmada),
                                ANCHO_ROPA, TextAlign.Center,
                            )
                            Celda("", ANCHO_DINERO)
                            Celda(
                                p.ingreso?.let { "${formatoImporte(it)} €" } ?: "",
                                ANCHO_DINERO, TextAlign.End, BaniterioColors.goldSoft,
                            )
                            Celda("", ANCHO_RECIBO)
                            Celda(
                                p.saldoTras?.let { "${formatoImporte(it)} €" } ?: "",
                                ANCHO_SALDO, TextAlign.End, negrita = true,
                            )
                        }
                    }
                    Row {
                        Celda(
                            "Total en cuotas: ${formatoImporte(c.totalCuotas)} € · cobrado ${formatoImporte(c.totalCobrado)} €",
                            ANCHO_CONCEPTO, color = BaniterioColors.muted, negrita = true,
                        )
                    }

                    // Bloque 3: ropa confirmada, gastos e ingresos
                    FilaSeccion("Ingresos y gastos")
                    val gastos = c.movimientos.filter {
                        it.manual || it.origen == "CAMISETA" || it.origen == "SUDADERA"
                    }
                    if (gastos.isEmpty()) {
                        Celda("Ningún gasto ni ingreso todavía.", ANCHO_CONCEPTO, color = BaniterioColors.muted)
                    }
                    gastos.forEach { m ->
                        Row {
                            Celda(conceptoMovimiento(m), ANCHO_CONCEPTO)
                            Celda("", ANCHO_ESTADO)
                            Celda("", ANCHO_ROPA)
                            Celda("", ANCHO_ROPA)
                            Celda(
                                if (m.importe < 0) "${formatoImporte(-m.importe)} €" else "",
                                ANCHO_DINERO, TextAlign.End, BaniterioColors.error,
                            )
                            Celda(
                                if (m.importe > 0) "${formatoImporte(m.importe)} €" else "",
                                ANCHO_DINERO, TextAlign.End, BaniterioColors.goldSoft,
                            )
                            Box(Modifier.width(ANCHO_RECIBO).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                if (m.reciboArchivo != null) {
                                    Text(
                                        "📄",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BaniterioColors.gold,
                                        modifier = Modifier.clickable {
                                            uriHandler.openUri("$API_BASE_URL/media/recibos/${m.reciboArchivo}")
                                        },
                                    )
                                }
                            }
                            Celda("${formatoImporte(m.saldoTras)} €", ANCHO_SALDO, TextAlign.End, negrita = true)
                        }
                    }

                    // Pie
                    Row(Modifier.background(BaniterioColors.panel)) {
                        Celda("Saldo actual", ANCHO_CONCEPTO, negrita = true)
                        Celda("", ANCHO_ESTADO)
                        Celda("", ANCHO_ROPA)
                        Celda("", ANCHO_ROPA)
                        Celda("", ANCHO_DINERO)
                        Celda("", ANCHO_DINERO)
                        Celda("", ANCHO_RECIBO)
                        Celda("${formatoImporte(c.saldo)} €", ANCHO_SALDO, TextAlign.End, BaniterioColors.gold, true)
                    }
                }

                if (c.resumenGastos.isNotEmpty()) {
                    Text(
                        "Resumen de gastos",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    c.resumenGastos.forEach { r ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(r.categoria, style = MaterialTheme.typography.bodySmall, color = BaniterioColors.ink)
                            Text(
                                "${formatoImporte(r.total)} €",
                                style = MaterialTheme.typography.bodySmall,
                                color = BaniterioColors.error,
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmandoTransfer) {
        AlertDialog(
            onDismissRequest = { confirmandoTransfer = false },
            confirmButton = {
                TextButton(enabled = !guardando, onClick = {
                    guardando = true
                    scope.launch {
                        when (val r = cuentasRepo.marcarTransferido(cuentaId)) {
                            is ResultadoCuenta.Exito -> {
                                estado = EstadoCuentaDetalle.Cargada(r.dato)
                                confirmandoTransfer = false
                            }
                            is ResultadoCuenta.Error -> Unit
                        }
                        guardando = false
                    }
                }) { Text("Sí") }
            },
            dismissButton = { TextButton(onClick = { confirmandoTransfer = false }) { Text("No") } },
            title = { Text("¿Seguro que has hecho la transferencia?") },
            text = { Text("Solo apaga el aviso; el saldo no cambia.") },
        )
    }
}

/** Fecha + concepto + categoría + quién adelantó, en una línea. */
private fun conceptoMovimiento(m: MovimientoFilaDto): String = buildString {
    append(formatoFecha(m.fecha)).append(" · ").append(m.concepto)
    m.categoria?.let { append(" · ").append(it) }
    m.adelantadoPor?.let { append(" · adelantó ").append(it) }
}

@Composable
private fun ChipAnio(texto: Any, seleccionado: Boolean, onClick: () -> Unit) {
    Text(
        texto.toString(),
        modifier = Modifier
            .background(
                if (seleccionado) BaniterioColors.brand else BaniterioColors.surface,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = if (seleccionado) BaniterioColors.ink else BaniterioColors.muted,
        fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal,
    )
}
