package com.baniterio.app.ui.miembros

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.baniterio.app.data.FotoElegida
import com.baniterio.app.data.PerfilRepository
import com.baniterio.app.data.ResultadoPerfil
import com.baniterio.app.data.dto.AvatarResumen
import com.baniterio.app.data.dto.GuardarPerfilRequest
import com.baniterio.app.data.dto.HijoRequest
import com.baniterio.app.data.dto.PerfilResponse
import com.baniterio.app.data.normalizarTelefonoEs
import com.baniterio.app.data.rememberSelectorContacto
import com.baniterio.app.data.rememberSelectorFoto
import com.baniterio.app.data.urlMedia
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.ui.comun.CaraDeCarta
import kotlinx.coroutines.launch

/** Texto fijo junto al teléfono de pareja/hijo. */
private const val AVISO_TELEFONO_FAMILIA =
    "Lo pedimos para que cuando haya un evento, un solo miembro de la familia pueda apuntar a todos."

private class FilaHijo(
    val id: Long?,
    nombre: String,
    mayorDeEdad: Boolean,
    telefono: String,
    visible: Boolean,
) {
    var nombre by mutableStateOf(nombre)
    var mayorDeEdad by mutableStateOf(mayorDeEdad)
    var telefono by mutableStateOf(telefono)
    var visible by mutableStateOf(visible)
}

private sealed interface EstadoEditor {
    data object Cargando : EstadoEditor
    data object Listo : EstadoEditor
    data object Guardando : EstadoEditor
    data class Error(val mensaje: String) : EstadoEditor
}

/**
 * Editor de perfil, con forma de carta (igual que la tarjeta de la sección
 * Miembros y que el editor web): la foto o el avatar a lo grande arriba y el
 * resto del formulario dentro. Un mismo componente para el alta obligatoria del
 * primer login ([obligatorio] = true, sin "Volver") y para "Editar" desde la
 * propia tarjeta.
 *
 * Landmines de backend respetadas: `tienePareja` arranca del valor real y solo
 * cambia si el usuario toca el check; con un vínculo `ACEPTADO` los campos de
 * pareja no se editan (solo "Romper vínculo") y el `PUT` reenvía tal cual los
 * que trajo el `GET`.
 */
@Composable
fun EditorPerfilScreen(
    perfilRepo: PerfilRepository,
    obligatorio: Boolean,
    onGuardado: () -> Unit,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoEditor>(EstadoEditor.Cargando) }
    var errorCarga by remember { mutableStateOf<String?>(null) }
    var intento by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val selectorFoto = rememberSelectorFoto()
    val selectorContacto = rememberSelectorContacto()

    var avatares by remember { mutableStateOf<List<AvatarResumen>>(emptyList()) }
    var dialogoAvatar by remember { mutableStateOf(false) }

    var nombre by remember { mutableStateOf("") }
    var apellidos by remember { mutableStateOf("") }
    var mote by remember { mutableStateOf("") }
    var sobreMi by remember { mutableStateOf("") }
    var imagenRefAvatar by remember { mutableStateOf<String?>(null) }
    var fotoPendiente by remember { mutableStateOf<FotoElegida?>(null) }
    var imagenRefFoto by remember { mutableStateOf<String?>(null) }
    var imagenUrlGuardada by remember { mutableStateOf<String?>(null) }

    var tienePareja by remember { mutableStateOf(false) }
    var parejaEstado by remember { mutableStateOf<String?>(null) }
    var parejaNombre by remember { mutableStateOf("") }
    var parejaTelefono by remember { mutableStateOf("") }
    val hijos = remember { mutableStateListOf<FilaHijo>() }

    var mensajeError by remember { mutableStateOf<String?>(null) }

    fun precargar(p: PerfilResponse) {
        nombre = p.nombre
        apellidos = p.apellidos
        mote = p.mote ?: ""
        sobreMi = p.sobreMi ?: ""
        imagenRefAvatar = if (p.imagenTipo == "AVATAR") p.imagenRef else null
        imagenRefFoto = if (p.imagenTipo == "FOTO") p.imagenRef else null
        fotoPendiente = null
        imagenUrlGuardada = urlMedia(p.imagenUrl)
        tienePareja = p.pareja != null
        parejaEstado = p.pareja?.estado
        parejaNombre = p.pareja?.nombre ?: ""
        parejaTelefono = p.pareja?.telefono ?: ""
        hijos.clear()
        p.hijos.forEach {
            hijos.add(FilaHijo(it.id, it.nombre, it.mayorDeEdad, it.telefono ?: "", it.visible))
        }
    }

    LaunchedEffect(intento) {
        estado = EstadoEditor.Cargando
        errorCarga = null
        val perfil = perfilRepo.miPerfil()
        if (perfil is ResultadoPerfil.Error) {
            errorCarga = perfil.mensaje; estado = EstadoEditor.Error(perfil.mensaje); return@LaunchedEffect
        }
        val cat = perfilRepo.avatares()
        if (cat is ResultadoPerfil.Error) {
            errorCarga = cat.mensaje; estado = EstadoEditor.Error(cat.mensaje); return@LaunchedEffect
        }
        avatares = (cat as ResultadoPerfil.Exito).dato
        precargar((perfil as ResultadoPerfil.Exito).dato)
        estado = EstadoEditor.Listo
    }

    // Lo que se pinta en la cara de la carta: la foto elegida ahora (bytes), la
    // foto/avatar ya guardado, o el avatar recién elegido.
    val modeloImagen: Any? = when {
        fotoPendiente != null -> fotoPendiente!!.bytes
        imagenRefFoto != null -> imagenUrlGuardada
        imagenRefAvatar != null -> urlMedia("/api/v1/media/avatares/$imagenRefAvatar.png")
        else -> null
    }
    val hayImagen = fotoPendiente != null || imagenRefFoto != null || imagenRefAvatar != null

    fun guardar() {
        mensajeError = null
        if (nombre.isBlank() || apellidos.isBlank()) {
            mensajeError = "El nombre y los apellidos son obligatorios."
            return
        }
        if (!hayImagen) {
            mensajeError = "Elige una foto o un avatar."
            return
        }
        val aceptado = parejaEstado == "ACEPTADO"
        if (tienePareja && !aceptado) {
            if (parejaNombre.isBlank()) { mensajeError = "Escribe el nombre de tu pareja."; return }
            if (normalizarTelefonoEs(parejaTelefono) == null) {
                mensajeError = "Revisa el teléfono de tu pareja: no parece un móvil español."
                return
            }
        }
        for (h in hijos) {
            if (h.nombre.isBlank()) { mensajeError = "Cada hijo necesita un nombre."; return }
            if (h.telefono.isNotBlank() && normalizarTelefonoEs(h.telefono) == null) {
                mensajeError = "Revisa el teléfono de ${h.nombre}: no parece un móvil español."
                return
            }
        }

        estado = EstadoEditor.Guardando
        scope.launch {
            val imagenTipo: String
            val imagenRef: String
            val pend = fotoPendiente
            if (pend != null) {
                when (val r = perfilRepo.subirFoto(pend)) {
                    is ResultadoPerfil.Exito -> { imagenTipo = "FOTO"; imagenRef = r.dato }
                    is ResultadoPerfil.Error -> { mensajeError = r.mensaje; estado = EstadoEditor.Listo; return@launch }
                }
            } else if (imagenRefFoto != null) {
                imagenTipo = "FOTO"; imagenRef = imagenRefFoto!!
            } else {
                imagenTipo = "AVATAR"; imagenRef = imagenRefAvatar!!
            }

            val req = GuardarPerfilRequest(
                nombre = nombre.trim(),
                apellidos = apellidos.trim(),
                mote = mote.trim().ifBlank { null },
                sobreMi = sobreMi.trim().ifBlank { null },
                imagenTipo = imagenTipo,
                imagenRef = imagenRef,
                tienePareja = tienePareja,
                parejaNombre = when {
                    !tienePareja -> null
                    aceptado -> parejaNombre
                    else -> parejaNombre.trim().ifBlank { null }
                },
                parejaTelefono = when {
                    !tienePareja -> null
                    aceptado -> parejaTelefono
                    else -> normalizarTelefonoEs(parejaTelefono)
                },
                hijos = hijos.map {
                    HijoRequest(
                        id = it.id,
                        nombre = it.nombre.trim(),
                        mayorDeEdad = it.mayorDeEdad,
                        telefono = it.telefono.trim().ifBlank { null }?.let(::normalizarTelefonoEs),
                        visible = it.visible,
                    )
                },
            )
            when (val r = perfilRepo.guardar(req)) {
                is ResultadoPerfil.Exito -> onGuardado()
                is ResultadoPerfil.Error -> { mensajeError = r.mensaje; estado = EstadoEditor.Listo }
            }
        }
    }

    fun romperVinculo() {
        scope.launch {
            when (val r = perfilRepo.romperPareja()) {
                is ResultadoPerfil.Exito -> intento++ // recarga
                is ResultadoPerfil.Error -> mensajeError = r.mensaje
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BaniterioWordmark()
            if (!obligatorio) {
                Text(
                    text = "Volver",
                    color = BaniterioColors.brandBright,
                    modifier = Modifier.clickable { onVolver() },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (obligatorio) "Completa tu perfil" else "Editar perfil",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (obligatorio) {
            Spacer(Modifier.height(4.dp))
            Text("Rellena tu perfil para entrar en la peña.", color = BaniterioColors.muted)
        }
        Spacer(Modifier.height(20.dp))

        when (val e = estado) {
            is EstadoEditor.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
            is EstadoEditor.Error -> Column {
                Text(e.mensaje, color = BaniterioColors.error)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Reintentar",
                    color = BaniterioColors.brandBright,
                    modifier = Modifier.clickable { intento++ },
                )
            }
            else -> {
                val guardando = estado is EstadoEditor.Guardando

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 440.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, BaniterioColors.outline, RoundedCornerShape(16.dp))
                        .background(BaniterioColors.panel),
                ) {
                    CaraDeCarta(
                        modelo = modeloImagen,
                        iniciales = ((nombre.firstOrNull()?.toString() ?: "") +
                            (apellidos.firstOrNull()?.toString() ?: "")).uppercase(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { dialogoAvatar = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BaniterioColors.brand,
                                    contentColor = BaniterioColors.gold,
                                ),
                            ) { Text("Elegir avatar", fontWeight = FontWeight.Bold) }
                            if (selectorFoto.disponible) {
                                Button(
                                    onClick = {
                                        selectorFoto.elegir {
                                            if (it != null) {
                                                fotoPendiente = it
                                                imagenRefAvatar = null
                                                imagenRefFoto = null
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BaniterioColors.brand,
                                        contentColor = BaniterioColors.gold,
                                    ),
                                ) { Text("Subir una foto", fontWeight = FontWeight.Bold) }
                            }
                        }

                        OutlinedTextField(
                            value = nombre, onValueChange = { nombre = it },
                            label = { Text("Nombre") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = apellidos, onValueChange = { apellidos = it },
                            label = { Text("Apellidos") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = mote, onValueChange = { mote = it },
                            label = { Text("Mote (opcional)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = sobreMi, onValueChange = { if (it.length <= 500) sobreMi = it },
                            label = { Text("Sobre mí (opcional)") }, minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Pareja
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BaniterioColors.surface)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = tienePareja, onCheckedChange = { tienePareja = it })
                                Text("Tengo pareja en la peña", color = MaterialTheme.colorScheme.onBackground)
                            }
                            if (tienePareja) {
                                if (parejaEstado == "ACEPTADO") {
                                    Text("Tu pareja: $parejaNombre", color = BaniterioColors.muted)
                                    TextButton(onClick = { romperVinculo() }) {
                                        Text("Romper vínculo", color = BaniterioColors.error)
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = parejaNombre, onValueChange = { parejaNombre = it },
                                        label = { Text("Nombre de tu pareja") }, singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    OutlinedTextField(
                                        value = parejaTelefono, onValueChange = { parejaTelefono = it },
                                        label = { Text("Su teléfono") }, singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (selectorContacto.disponible) {
                                        TextButton(onClick = {
                                            selectorContacto.elegir { n ->
                                                if (n != null) parejaTelefono = normalizarTelefonoEs(n) ?: n
                                            }
                                        }) { Text("Elegir de contactos") }
                                    }
                                    if (parejaEstado == "PENDIENTE") {
                                        Text("Esperando a que confirme el vínculo.", color = BaniterioColors.muted)
                                    }
                                    Text(
                                        AVISO_TELEFONO_FAMILIA,
                                        color = BaniterioColors.muted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }

                        // Hijos
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BaniterioColors.surface)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Hijos", color = BaniterioColors.brandBright, fontWeight = FontWeight.Bold)
                                TextButton(onClick = {
                                    hijos.add(FilaHijo(null, "", false, "", false))
                                }) { Text("+ Añadir hijo") }
                            }
                            if (hijos.isEmpty()) {
                                Text("No has añadido ningún hijo.", color = BaniterioColors.muted)
                            }
                            hijos.forEachIndexed { i, h ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(BaniterioColors.panel)
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedTextField(
                                        value = h.nombre, onValueChange = { h.nombre = it },
                                        label = { Text("Nombre") }, singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    OutlinedTextField(
                                        value = h.telefono, onValueChange = { h.telefono = it },
                                        label = { Text("Teléfono (opcional)") }, singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (selectorContacto.disponible) {
                                        TextButton(onClick = {
                                            selectorContacto.elegir { n ->
                                                if (n != null) h.telefono = normalizarTelefonoEs(n) ?: n
                                            }
                                        }) { Text("Elegir de contactos") }
                                    }
                                    Text(
                                        AVISO_TELEFONO_FAMILIA,
                                        color = BaniterioColors.muted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = h.mayorDeEdad, onCheckedChange = { h.mayorDeEdad = it })
                                        Text("Mayor de 18 años", color = MaterialTheme.colorScheme.onBackground)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = h.visible, onCheckedChange = { h.visible = it })
                                        Text("Mostrar en la peña", color = MaterialTheme.colorScheme.onBackground)
                                    }
                                    TextButton(onClick = { hijos.removeAt(i) }) {
                                        Text("Quitar", color = BaniterioColors.muted)
                                    }
                                }
                            }
                        }

                        mensajeError?.let {
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

    if (dialogoAvatar) {
        Dialog(onDismissRequest = { dialogoAvatar = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BaniterioColors.panel)
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Elige tu avatar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    TextButton(onClick = { dialogoAvatar = false }) { Text("Cerrar") }
                }
                Spacer(Modifier.height(12.dp))
                SelectorAvatar(
                    avatares = avatares,
                    seleccionado = imagenRefAvatar,
                    onElegir = {
                        imagenRefAvatar = it
                        fotoPendiente = null
                        imagenRefFoto = null
                        dialogoAvatar = false
                    },
                )
            }
        }
    }
}
