package com.baniterio.app.ui.auth.solicitaracceso

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.AuthRepository
import com.baniterio.app.data.ResultadoAuth
import com.baniterio.app.data.dto.SolicitudIngresoRequest
import com.baniterio.app.nav.SolicitudPrecarga
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import kotlinx.coroutines.launch

private sealed interface EstadoSolicitud {
    data object Editando : EstadoSolicitud
    data object Enviando : EstadoSolicitud
    data object Ok : EstadoSolicitud
    data class Error(val mensaje: String) : EstadoSolicitud
}

private val telefonoValido = Regex("^[67]\\d{8}$")

@Composable
fun SolicitarAccesoScreen(
    repo: AuthRepository,
    precarga: SolicitudPrecarga?,
    onEnviada: () -> Unit,
    onVolver: () -> Unit,
) {
    var nombre by rememberSaveable { mutableStateOf(precarga?.nombre ?: "") }
    var apellidos by rememberSaveable { mutableStateOf(precarga?.apellidos ?: "") }
    var telefono by rememberSaveable { mutableStateOf(precarga?.telefono ?: "") }
    var email by rememberSaveable { mutableStateOf(precarga?.email ?: "") }
    var motivo by rememberSaveable { mutableStateOf("") }
    var relacion by rememberSaveable { mutableStateOf("") }
    var conocidos by rememberSaveable { mutableStateOf("") }
    var estado by remember { mutableStateOf<EstadoSolicitud>(EstadoSolicitud.Editando) }
    val scope = rememberCoroutineScope()

    val enviando = estado is EstadoSolicitud.Enviando
    val formularioValido = nombre.isNotBlank() && apellidos.isNotBlank() &&
        telefonoValido.matches(telefono) && email.contains("@") &&
        motivo.trim().length >= 10 && relacion.trim().length >= 10 &&
        conocidos.trim().length >= 10

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .imePadding(),
    ) {
        BaniterioWordmark()
        Spacer(Modifier.height(16.dp))

        when (val e = estado) {
            is EstadoSolicitud.Ok -> {
                Text(
                    text = "Solicitud enviada. Un administrador la revisará.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { onEnviada() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BaniterioColors.brand,
                        contentColor = BaniterioColors.gold,
                    ),
                ) {
                    Text("Volver", fontWeight = FontWeight.Bold)
                }
            }

            else -> {
                val mensajeError = (e as? EstadoSolicitud.Error)?.mensaje

                Text(
                    text = "Solicitar acceso",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tu teléfono no está en la lista de la peña. Cuéntanos quién " +
                        "eres y un administrador revisará tu solicitud.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BaniterioColors.muted,
                )
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = nombre,
                    onValueChange = {
                        nombre = it
                        if (estado is EstadoSolicitud.Error) estado = EstadoSolicitud.Editando
                    },
                    label = { Text("Nombre") },
                    enabled = !enviando,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = apellidos,
                    onValueChange = {
                        apellidos = it
                        if (estado is EstadoSolicitud.Error) estado = EstadoSolicitud.Editando
                    },
                    label = { Text("Apellidos") },
                    enabled = !enviando,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = telefono,
                    onValueChange = {
                        telefono = it
                        if (estado is EstadoSolicitud.Error) estado = EstadoSolicitud.Editando
                    },
                    label = { Text("Teléfono") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    enabled = !enviando,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        if (estado is EstadoSolicitud.Error) estado = EstadoSolicitud.Editando
                    },
                    label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !enviando,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = motivo,
                    onValueChange = {
                        motivo = it
                        if (estado is EstadoSolicitud.Error) estado = EstadoSolicitud.Editando
                    },
                    label = { Text("¿Por qué quieres entrar a la peña?") },
                    minLines = 3,
                    enabled = !enviando,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = relacion,
                    onValueChange = {
                        relacion = it
                        if (estado is EstadoSolicitud.Error) estado = EstadoSolicitud.Editando
                    },
                    label = { Text("¿Qué relación tienes con la peña?") },
                    minLines = 3,
                    enabled = !enviando,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = conocidos,
                    onValueChange = {
                        conocidos = it
                        if (estado is EstadoSolicitud.Error) estado = EstadoSolicitud.Editando
                    },
                    label = { Text("¿A quién conoces de la peña?") },
                    minLines = 3,
                    enabled = !enviando,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (formularioValido && !enviando) {
                            estado = EstadoSolicitud.Enviando
                            scope.launch {
                                estado = when (
                                    val r = repo.solicitarAcceso(
                                        SolicitudIngresoRequest(
                                            telefono = telefono,
                                            email = email,
                                            nombre = nombre,
                                            apellidos = apellidos,
                                            motivo = motivo,
                                            relacion = relacion,
                                            conocidos = conocidos,
                                            password = precarga?.password?.takeIf { it.isNotBlank() },
                                        ),
                                    )
                                ) {
                                    is ResultadoAuth.Exito -> EstadoSolicitud.Ok
                                    is ResultadoAuth.Error -> EstadoSolicitud.Error(r.mensaje)
                                }
                            }
                        }
                    },
                    enabled = formularioValido && !enviando,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BaniterioColors.brand,
                        contentColor = BaniterioColors.gold,
                    ),
                ) {
                    Text(
                        if (enviando) "Enviando…" else "Enviar solicitud",
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (mensajeError != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = mensajeError,
                        color = BaniterioColors.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Volver",
                    color = BaniterioColors.brandBright,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onVolver() },
                )
            }
        }
    }
}
