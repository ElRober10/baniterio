package com.baniterio.app.ui.eventos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.AsistenciaRepository
import com.baniterio.app.data.BebidaRepository
import com.baniterio.app.data.EventosRepository
import com.baniterio.app.data.ResultadoEvento
import com.baniterio.app.data.dto.EventoDetalle
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.ui.comun.CabeceraPantalla
import com.baniterio.app.ui.comun.EstadoCarga
import com.baniterio.app.ui.comun.PantallaConEstado
import com.baniterio.app.ui.comun.relieveDeCarta
import kotlinx.coroutines.launch

/** Icono de papelera dibujado a mano (sin depender de una librería de iconos). */
@Composable
private fun IconoPapelera(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier.width(20.dp).height(20.dp)) {
        val stroke = Stroke(width = size.width / 11f, cap = StrokeCap.Round)
        val left = size.width * 0.22f
        val right = size.width * 0.78f
        val lidY = size.height * 0.28f
        val bottomY = size.height * 0.92f
        drawLine(tint, Offset(size.width * 0.12f, lidY), Offset(size.width * 0.88f, lidY), stroke.width, stroke.cap)
        drawLine(tint, Offset(size.width * 0.38f, lidY), Offset(size.width * 0.38f, size.height * 0.1f), stroke.width, stroke.cap)
        drawLine(tint, Offset(size.width * 0.62f, lidY), Offset(size.width * 0.62f, size.height * 0.1f), stroke.width, stroke.cap)
        drawLine(tint, Offset(size.width * 0.38f, size.height * 0.1f), Offset(size.width * 0.62f, size.height * 0.1f), stroke.width, stroke.cap)
        drawLine(tint, Offset(left, lidY), Offset(left + size.width * 0.04f, bottomY), stroke.width, stroke.cap)
        drawLine(tint, Offset(right, lidY), Offset(right - size.width * 0.04f, bottomY), stroke.width, stroke.cap)
        drawLine(tint, Offset(left + size.width * 0.04f, bottomY), Offset(right - size.width * 0.04f, bottomY), stroke.width, stroke.cap)
        drawLine(tint, Offset(size.width * 0.5f, lidY + size.height * 0.1f), Offset(size.width * 0.5f, bottomY - size.height * 0.1f), stroke.width, stroke.cap)
    }
}

private fun metodoLegible(m: String?) = when (m) {
    "BIZUM" -> "Bizum"
    "TRANSFERENCIA" -> "Transferencia"
    "EFECTIVO" -> "Efectivo"
    else -> m ?: ""
}

/** (valor de backend, etiqueta) de los tres estados de asistencia. */
internal val ESTADOS_ASISTENCIA = listOf(
    "APUNTADO" to "Me apunto",
    "NO_VOY" to "No voy",
    "EN_DUDA" to "En duda",
)

/**
 * Vista de un evento: sus datos, el recuento de asistentes y —según los permisos
 * que devuelve el backend— los botones de gestión y borrado. La parte de
 * asistencia (responder, ficha de bebida, convocatoria, añadir a mano) se movió
 * a [AsistenciaEventoScreen] mientras se rediseña.
 */
@Composable
fun EventoDetalleScreen(
    eventosRepo: EventosRepository,
    asistenciaRepo: AsistenciaRepository,
    bebidaRepo: BebidaRepository,
    eventoId: Long,
    onEditar: () -> Unit,
    onBorrado: () -> Unit,
    onInventarioFiesta: () -> Unit,
    onListaCompra: () -> Unit,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoCarga<EventoDetalle>>(EstadoCarga.Cargando) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var intento by remember { mutableStateOf(0) }
    var confirmarBorrado by remember { mutableStateOf(false) }
    var mostrarAnadirAsistente by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(eventoId, intento) {
        estado = EstadoCarga.Cargando
        estado = when (val r = eventosRepo.detalle(eventoId)) {
            is ResultadoEvento.Exito -> EstadoCarga.Cargado(r.dato)
            is ResultadoEvento.Error -> EstadoCarga.Error(r.mensaje)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CabeceraPantalla(onVolver)

        PantallaConEstado(estado, onReintentar = { intento++ }) { ev ->
            run {
                var verAsistentes by remember { mutableStateOf(false) }
                var verPago by remember { mutableStateOf(false) }
                var verPagoInfo by remember { mutableStateOf(false) }
                var verPagoDeclarado by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .relieveDeCarta(RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (ev.oculto) {
                        Text(
                            "OCULTO (BORRADO)",
                            style = MaterialTheme.typography.labelSmall,
                            color = BaniterioColors.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            ev.nombre,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).alpha(if (ev.pasado) 0.7f else 1f),
                        )
                        if (ev.puedoBorrar && !ev.oculto) {
                            IconButton(
                                onClick = { confirmarBorrado = true },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFDC2626)),
                            ) {
                                IconoPapelera()
                            }
                        }
                    }
                    Text(
                        buildString {
                            append(formatoFecha(ev.fecha))
                            ev.fechaFin?.let { append(" – ").append(formatoFecha(it)) }
                            ev.lugar?.let { append("  ·  ").append(it) }
                            if (ev.pasado) append("  ·  PASADO")
                        },
                        color = BaniterioColors.muted,
                    )
                    Text("Cuenta: ${ev.cuenta.nombre}", color = BaniterioColors.muted)
                    val cuotas = remember(ev) {
                        listOfNotNull(
                            ev.cuotaCubatas?.let { "Cubatas" to it },
                            ev.cuotaCervezas?.let { "Cervezas" to it },
                            ev.cuotaCubatas1Dia?.let { "Cubatas 1 día" to it },
                            ev.cuotaCervezas1Dia?.let { "Cervezas 1 día" to it },
                            ev.cuotaEmbarazada?.let { "Embarazada" to it },
                        )
                    }
                    if (cuotas.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(BaniterioColors.brand.copy(alpha = 0.10f))
                                .border(1.dp, BaniterioColors.outline.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "CUOTAS A PAGAR",
                                style = MaterialTheme.typography.labelSmall,
                                color = BaniterioColors.goldSoft,
                                fontWeight = FontWeight.Bold,
                            )
                            // 3 cajas por fila fijas; la fila incompleta (si sobran 1 o 2) se
                            // centra, igual que en la web (evento-detalle.html).
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val espaciado = 8.dp
                                val anchoCaja = (maxWidth - espaciado * 2) / 3
                                Column(verticalArrangement = Arrangement.spacedBy(espaciado)) {
                                    cuotas.chunked(3).forEach { fila ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = if (fila.size == 3) {
                                                Arrangement.spacedBy(espaciado)
                                            } else {
                                                Arrangement.spacedBy(espaciado, Alignment.CenterHorizontally)
                                            },
                                        ) {
                                            fila.forEach { (etiqueta, importe) ->
                                                CajaCuota(etiqueta, importe, Modifier.width(anchoCaja))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ev.asistencia.ficha.miFicha?.let { f ->
                        if (f.cuota != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Tu cuota: ${formatoImporte(f.cuota)} €",
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                val confirmado = f.estadoPago == "CONFIRMADO_EN_CUENTA" ||
                                    f.estadoPago == "CONFIRMADO_PENDIENTE_ENVIO"
                                val declarada = f.estadoPago == "DECLARADO"
                                OutlinedButton(
                                    onClick = {
                                        when {
                                            confirmado -> verPagoInfo = true
                                            declarada -> verPagoDeclarado = true
                                            else -> verPago = true
                                        }
                                    },
                                ) {
                                    Text(
                                        when {
                                            confirmado -> "Ya he pagado"
                                            declarada -> "Ver mi pago declarado"
                                            else -> "Confirmar el pago"
                                        },
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        } else if (f.cuotaPendiente) {
                            Text(
                                "Tu cuota está pendiente de que se fije la cuota máxima del evento.",
                                color = BaniterioColors.muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    ev.descripcion?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    ev.creadoPor?.let {
                        Text(
                            "Creado por ${it.nombre}",
                            color = BaniterioColors.muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (ev.asistencia.notificacionMandada) {
                        Text(
                            "${ev.asistencia.apuntados} apuntados · ${ev.asistencia.enDuda} en duda · " +
                                "${ev.asistencia.sinContestar} sin contestar",
                            color = BaniterioColors.muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (ev.asistencia.ficha.llevaFicha) {
                        val botonRelleno = ButtonDefaults.buttonColors(
                            containerColor = BaniterioColors.brand,
                            contentColor = BaniterioColors.gold,
                        )
                        Button(
                            onClick = { verAsistentes = true },
                            colors = botonRelleno,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Listado de asistentes", fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = onInventarioFiesta,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Inventario de la fiesta", fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = onListaCompra,
                            colors = botonRelleno,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Lista de la compra", fontWeight = FontWeight.Bold) }
                    }

                    aviso?.let { Text(it, color = BaniterioColors.gold) }

                    if (verAsistentes) {
                        ListadoAsistentesDialog(
                            eventoId,
                            eventosRepo,
                            onCerrar = { verAsistentes = false },
                            puedoEditar = ev.puedoEditar,
                            llevaFicha = ev.asistencia.ficha.llevaFicha,
                            diasEvento = ev.asistencia.ficha.diasEvento,
                            asistenciaRepo = asistenciaRepo,
                            bebidaRepo = bebidaRepo,
                        )
                    }
                    if (verPago) {
                        HePagadoDialog(
                            eventoId = eventoId,
                            repo = eventosRepo,
                            onConfirmar = { pago ->
                                verPago = false
                                scope.launch {
                                    val r = eventosRepo.declararPago(
                                        eventoId,
                                        com.baniterio.app.data.dto.DeclararPagoBody(
                                            importe = pago.importe,
                                            metodo = pago.metodo,
                                            cubreUsuarioIds = pago.cubreUsuarioIds,
                                            cubreAsistenciaIds = pago.cubreAsistenciaIds,
                                        ),
                                    )
                                    aviso = when (r) {
                                        is ResultadoEvento.Exito -> "Pago enviado. Un administrador lo confirmará."
                                        is ResultadoEvento.Error -> r.mensaje
                                    }
                                    intento++
                                }
                            },
                            onCerrar = { verPago = false },
                        )
                    }
                    if (verPagoDeclarado) {
                        ev.asistencia.ficha.miFicha?.miPagoDeclarado?.let { d ->
                            AlertDialog(
                                onDismissRequest = { verPagoDeclarado = false },
                                confirmButton = {
                                    TextButton(onClick = { verPagoDeclarado = false }) { Text("Cerrar") }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        verPagoDeclarado = false
                                        scope.launch {
                                            when (val r = eventosRepo.anularPagoDeclarado(eventoId)) {
                                                is ResultadoEvento.Exito -> aviso = "Declaración anulada."
                                                is ResultadoEvento.Error -> aviso = r.mensaje
                                            }
                                            intento++
                                        }
                                    }) { Text("Anular declaración") }
                                },
                                title = { Text("Tu pago declarado") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("${formatoImporte(d.importe)} € por ${metodoLegible(d.metodoPago)}.")
                                        if (d.cubre.size > 1) {
                                            Text(
                                                "Cubre: " + d.cubre.joinToString(", ") { it.nombre },
                                                color = BaniterioColors.muted,
                                            )
                                        }
                                        Text(
                                            "Pendiente de que un administrador lo confirme.",
                                            color = BaniterioColors.muted,
                                        )
                                    }
                                },
                            )
                        }
                    }
                    if (verPagoInfo) {
                        ev.asistencia.ficha.miFicha?.let { f ->
                            AlertDialog(
                                onDismissRequest = { verPagoInfo = false },
                                confirmButton = {
                                    TextButton(onClick = { verPagoInfo = false }) { Text("Cerrar") }
                                },
                                title = { Text("Tu pago está confirmado") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            "Lo confirmó ${f.pagadoPor ?: "un administrador"}" +
                                                (f.pagadoAt?.let { " el $it" } ?: "") + ".",
                                        )
                                        f.metodoPago?.let {
                                            Text("Has pagado por ${metodoLegible(it)}.")
                                        }
                                        if (f.estadoPago == "CONFIRMADO_PENDIENTE_ENVIO") {
                                            Text(
                                                "Pendiente de ingresar en la cuenta de la peña.",
                                                color = BaniterioColors.muted,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }

                    if (ev.puedoEditar) {
                        OutlinedButton(
                            onClick = onEditar,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Editar", fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = { mostrarAnadirAsistente = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BaniterioColors.brand,
                                contentColor = BaniterioColors.gold,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Añadir asistente", fontWeight = FontWeight.Bold) }
                    }
                    if (ev.puedoBorrar && ev.oculto) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        when (val r = eventosRepo.recuperar(eventoId)) {
                                            is ResultadoEvento.Exito -> {
                                                aviso = "Evento recuperado."
                                                intento++
                                            }
                                            is ResultadoEvento.Error -> aviso = r.mensaje
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Recuperar", fontWeight = FontWeight.Bold) }
                        }
                    }

                    if (mostrarAnadirAsistente) {
                        AnadirAsistenteDialog(
                            eventoId = eventoId,
                            llevaFicha = ev.asistencia.ficha.llevaFicha,
                            diasEvento = ev.asistencia.ficha.diasEvento,
                            asistenciaRepo = asistenciaRepo,
                            eventosRepo = eventosRepo,
                            bebidaRepo = bebidaRepo,
                            onAnadido = {
                                mostrarAnadirAsistente = false
                                aviso = "«${it.nombre}» añadido."
                                intento++
                            },
                            onCerrar = { mostrarAnadirAsistente = false },
                        )
                    }

                    if (confirmarBorrado) {
                        AlertDialog(
                            onDismissRequest = { confirmarBorrado = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmarBorrado = false
                                    scope.launch {
                                        when (val r = eventosRepo.ocultar(eventoId)) {
                                            is ResultadoEvento.Exito -> onBorrado()
                                            is ResultadoEvento.Error -> aviso = r.mensaje
                                        }
                                    }
                                }) { Text("Sí, borrar") }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") }
                            },
                            title = { Text("¿Borrar «${ev.nombre}»?") },
                        )
                    }
                }
            }
        }
    }
}

/** Una caja de la rejilla de "Cuotas a pagar" del detalle de evento. */
@Composable
private fun CajaCuota(etiqueta: String, importe: Double, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BaniterioColors.brand.copy(alpha = 0.15f))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = BaniterioColors.muted)
        Text(
            "${formatoImporte(importe)} €",
            style = MaterialTheme.typography.titleMedium,
            color = BaniterioColors.goldSoft,
            fontWeight = FontWeight.Bold,
        )
    }
}
