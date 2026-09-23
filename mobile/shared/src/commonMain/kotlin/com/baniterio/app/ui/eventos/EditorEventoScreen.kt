package com.baniterio.app.ui.eventos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.CuentasRepository
import com.baniterio.app.data.EventosRepository
import com.baniterio.app.data.ResultadoCuenta
import com.baniterio.app.data.ResultadoEvento
import com.baniterio.app.data.dto.CuentaResumen
import com.baniterio.app.data.dto.GuardarEventoRequest
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.ui.comun.CabeceraPantalla
import com.baniterio.app.ui.comun.CajaFecha
import com.baniterio.app.ui.comun.relieveDeCarta
import kotlinx.coroutines.launch

private val REGEX_FECHA = Regex("""\d{4}-\d{2}-\d{2}""")

private sealed interface EstadoEditorEvento {
    data object Cargando : EstadoEditorEvento
    data object Listo : EstadoEditorEvento
    data class Error(val mensaje: String) : EstadoEditorEvento
}

@Composable
fun EditorEventoScreen(
    eventosRepo: EventosRepository,
    cuentasRepo: CuentasRepository,
    eventoId: Long?,
    esAdmin: Boolean,
    onGuardado: (Long) -> Unit,
    onVolver: () -> Unit,
) {
    val editando = eventoId != null
    var estado by remember {
        mutableStateOf<EstadoEditorEvento>(
            if (editando) EstadoEditorEvento.Cargando else EstadoEditorEvento.Listo,
        )
    }
    var nombre by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var lugar by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf("") }
    var fechaFin by remember { mutableStateOf("") }
    var cuotaCubatas by remember { mutableStateOf("") }
    var precioCamiseta by remember { mutableStateOf("") }
    var precioSudadera by remember { mutableStateOf("") }
    var cuentas by remember { mutableStateOf<List<CuentaResumen>>(emptyList()) }
    // Cuenta elegida: un id de cuenta existente, o `cuentaNueva` para crear una
    // con el nombre del evento. Nunca las dos a la vez.
    var cuentaId by remember { mutableStateOf<Long?>(null) }
    var cuentaNueva by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        when (val r = cuentasRepo.listar()) {
            is ResultadoCuenta.Exito -> cuentas = r.dato
            is ResultadoCuenta.Error -> Unit
        }
    }

    LaunchedEffect(eventoId) {
        if (eventoId == null) return@LaunchedEffect
        estado = EstadoEditorEvento.Cargando
        when (val r = eventosRepo.detalle(eventoId)) {
            is ResultadoEvento.Exito -> {
                nombre = r.dato.nombre
                descripcion = r.dato.descripcion ?: ""
                lugar = r.dato.lugar ?: ""
                fecha = r.dato.fecha
                fechaFin = r.dato.fechaFin ?: ""
                cuotaCubatas = r.dato.cuotaCubatas?.let { formatoImporte(it) } ?: ""
                precioCamiseta = r.dato.precioCamiseta?.let { formatoImporte(it) } ?: ""
                precioSudadera = r.dato.precioSudadera?.let { formatoImporte(it) } ?: ""
                cuentaId = r.dato.cuenta.id
                cuentaNueva = false
                estado = EstadoEditorEvento.Listo
            }
            is ResultadoEvento.Error -> estado = EstadoEditorEvento.Error(r.mensaje)
        }
    }

    fun guardar() {
        error = null
        if (nombre.isBlank()) {
            error = "El nombre es obligatorio."
            return
        }
        if (!REGEX_FECHA.matches(fecha)) {
            error = "La fecha debe tener el formato aaaa-mm-dd."
            return
        }
        if (fechaFin.isNotBlank()) {
            if (!REGEX_FECHA.matches(fechaFin)) {
                error = "La fecha de fin debe tener el formato aaaa-mm-dd."
                return
            }
            if (fechaFin < fecha) {
                error = "La fecha de fin no puede ser anterior a la de inicio."
                return
            }
        }
        if (!cuentaNueva && cuentaId == null) {
            error = "Elige una cuenta."
            return
        }
        // No se condiciona a `esAdmin` con un `return` temprano: los campos solo se
        // PINTAN para admins, así que un no-admin reenvía "" y aquí sale null (el
        // backend además ignora este campo si quien guarda no es admin).
        fun numeroCuota(raw: String, etiqueta: String): Double? {
            if (!esAdmin || raw.isBlank()) {
                return null
            }
            val n = raw.trim().replace(',', '.').toDoubleOrNull()
            if (n == null || n < 0.0) {
                error = "La cuota de $etiqueta tiene que ser un número mayor o igual que 0."
                return null
            }
            return n
        }
        val cubatas = numeroCuota(cuotaCubatas, "cubatas")
        if (error != null) return

        guardando = true
        scope.launch {
            val req = GuardarEventoRequest(
                nombre = nombre.trim(),
                descripcion = descripcion.trim().ifBlank { null },
                lugar = lugar.trim().ifBlank { null },
                fecha = fecha,
                fechaFin = fechaFin.ifBlank { null },
                cuentaId = if (cuentaNueva) null else cuentaId,
                cuentaNueva = cuentaNueva,
                cuotaCubatas = cubatas,
                precioCamiseta = numeroCuota(precioCamiseta, "camiseta"),
                precioSudadera = numeroCuota(precioSudadera, "sudadera"),
            )
            val r = if (eventoId != null) eventosRepo.editar(eventoId, req) else eventosRepo.crear(req)
            when (r) {
                is ResultadoEvento.Exito -> onGuardado(r.dato.id)
                is ResultadoEvento.Error -> {
                    guardando = false
                    error = r.mensaje
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CabeceraPantalla(onVolver)
        Text(
            if (editando) "Editar evento" else "Nuevo evento",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )

        when (val e = estado) {
            is EstadoEditorEvento.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
            is EstadoEditorEvento.Error -> Text(e.mensaje, color = BaniterioColors.error)
            is EstadoEditorEvento.Listo -> {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .relieveDeCarta(RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
                        value = nombre, onValueChange = { nombre = it },
                        label = { Text("Nombre") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = descripcion, onValueChange = { descripcion = it },
                        label = { Text("Descripción (opcional)") }, minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = lugar, onValueChange = { lugar = it },
                        label = { Text("Lugar (opcional)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CajaFecha("Fecha", fecha, { fecha = it })
                    CajaFecha("Fecha de fin (opcional)", fechaFin, { fechaFin = it }, opcional = true)

                    if (esAdmin) {
                        Text(
                            "Cuota (opcional). Las demás (cervezas, 1 día, embarazada) se " +
                                "calculan solas a partir de esta al guardar. Si se deja vacía, " +
                                "no se muestra ninguna.",
                            style = MaterialTheme.typography.labelLarge,
                            color = BaniterioColors.muted,
                        )
                        OutlinedTextField(
                            value = cuotaCubatas, onValueChange = { cuotaCubatas = it },
                            label = { Text("Cubatas €") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = precioCamiseta, onValueChange = { precioCamiseta = it },
                            label = { Text("Precio camiseta €") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = precioSudadera, onValueChange = { precioSudadera = it },
                            label = { Text("Precio sudadera €") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    SelectorCuenta(
                        cuentas = cuentas,
                        cuentaId = cuentaId,
                        cuentaNueva = cuentaNueva,
                        onExistente = { cuentaId = it; cuentaNueva = false },
                        onNueva = { cuentaId = null; cuentaNueva = true },
                    )

                    error?.let {
                        Text(it, color = BaniterioColors.error, style = MaterialTheme.typography.bodyMedium)
                    }

                    Button(
                        onClick = { guardar() },
                        enabled = !guardando,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BaniterioColors.brand,
                            contentColor = BaniterioColors.gold,
                        ),
                    ) {
                        Text(if (guardando) "Guardando…" else "Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectorCuenta(
    cuentas: List<CuentaResumen>,
    cuentaId: Long?,
    cuentaNueva: Boolean,
    onExistente: (Long) -> Unit,
    onNueva: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Cuenta",
            style = MaterialTheme.typography.labelLarge,
            color = BaniterioColors.muted,
        )
        cuentas.forEach { c ->
            OpcionCuenta(
                texto = c.nombre,
                seleccionada = !cuentaNueva && cuentaId == c.id,
                onClick = { onExistente(c.id) },
            )
        }
        OpcionCuenta(
            texto = "Otro evento (crea una cuenta nueva)",
            seleccionada = cuentaNueva,
            onClick = onNueva,
        )
    }
}

/** "26" si es entero, "26.5" si tiene decimales. Sin `String.format` (no está en common). */
internal fun formatoImporte(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

/** "2026-09-25" (ISO del backend) -> "25-09-2026". Deja igual lo que no encaje. */
internal fun formatoFecha(iso: String): String {
    val p = iso.split("-")
    return if (p.size == 3) "${p[2]}-${p[1]}-${p[0]}" else iso
}

@Composable
private fun OpcionCuenta(texto: String, seleccionada: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = seleccionada, onClick = onClick)
        Text(texto, color = MaterialTheme.colorScheme.onBackground)
    }
}
