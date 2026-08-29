package com.baniterio.app.ui.auth.registro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.AuthRepository
import com.baniterio.app.data.CodigoErrorAuth
import com.baniterio.app.data.ResultadoAuth
import com.baniterio.app.data.dto.RegistroRequest
import com.baniterio.app.nav.SolicitudPrecarga
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface EstadoRegistro {
    data object Editando : EstadoRegistro
    data object Enviando : EstadoRegistro
    data object Ok : EstadoRegistro
    data object NoAutorizado : EstadoRegistro
    data class Error(val mensaje: String) : EstadoRegistro
}

private val telefonoValido = Regex("^[67]\\d{8}$")

@Composable
fun RegistroScreen(
    repo: AuthRepository,
    onRegistroCompletado: () -> Unit,
    onVolverALogin: () -> Unit,
    onSolicitarAcceso: (SolicitudPrecarga) -> Unit,
) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var apellidos by rememberSaveable { mutableStateOf("") }
    var mote by rememberSaveable { mutableStateOf("") }
    var telefono by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var estado by remember { mutableStateOf<EstadoRegistro>(EstadoRegistro.Editando) }
    val scope = rememberCoroutineScope()

    val enviando = estado is EstadoRegistro.Enviando
    val formularioValido = nombre.isNotBlank() && apellidos.isNotBlank() &&
        telefonoValido.matches(telefono) && email.contains("@") && password.length >= 6

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
            is EstadoRegistro.Ok -> {
                Text(
                    text = "¡Cuenta creada!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                LaunchedEffect(Unit) {
                    delay(1200)
                    onRegistroCompletado()
                }
            }

            is EstadoRegistro.NoAutorizado -> {
                Text(
                    text = "Únete a la peña",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Teléfono no autorizado: tu número no está en la lista de la peña.",
                    color = BaniterioColors.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        onSolicitarAcceso(SolicitudPrecarga(nombre, apellidos, telefono, email, password))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BaniterioColors.brand,
                        contentColor = BaniterioColors.gold,
                    ),
                ) {
                    Text("Solicitar acceso a la peña", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Volver al inicio de sesión",
                    color = BaniterioColors.brandBright,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onVolverALogin() },
                )
            }

            else -> {
                val mensajeError = (e as? EstadoRegistro.Error)?.mensaje

                Text(
                    text = "Únete a la peña",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Rellena tus datos para darte de alta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BaniterioColors.muted,
                )
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = nombre,
                    onValueChange = {
                        nombre = it
                        if (estado is EstadoRegistro.Error) estado = EstadoRegistro.Editando
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
                        if (estado is EstadoRegistro.Error) estado = EstadoRegistro.Editando
                    },
                    label = { Text("Apellidos") },
                    enabled = !enviando,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = mote,
                    onValueChange = {
                        mote = it
                        if (estado is EstadoRegistro.Error) estado = EstadoRegistro.Editando
                    },
                    label = { Text("Mote (opcional)") },
                    enabled = !enviando,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = telefono,
                    onValueChange = {
                        telefono = it
                        if (estado is EstadoRegistro.Error) estado = EstadoRegistro.Editando
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
                        if (estado is EstadoRegistro.Error) estado = EstadoRegistro.Editando
                    },
                    label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !enviando,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (estado is EstadoRegistro.Error) estado = EstadoRegistro.Editando
                    },
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !enviando,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (formularioValido && !enviando) {
                            estado = EstadoRegistro.Enviando
                            scope.launch {
                                val r = repo.registro(
                                    RegistroRequest(
                                        telefono = telefono,
                                        email = email,
                                        password = password,
                                        nombre = nombre,
                                        apellidos = apellidos,
                                        mote = mote,
                                    ),
                                )
                                estado = when (r) {
                                    is ResultadoAuth.Exito -> EstadoRegistro.Ok
                                    is ResultadoAuth.Error ->
                                        if (r.codigo == CodigoErrorAuth.TELEFONO_NO_AUTORIZADO) {
                                            EstadoRegistro.NoAutorizado
                                        } else {
                                            EstadoRegistro.Error(r.mensaje)
                                        }
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
                        if (enviando) "Creando…" else "Crear cuenta",
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
                Row {
                    Text("¿Ya tienes cuenta? ", color = BaniterioColors.muted)
                    Text(
                        text = "Entra aquí",
                        color = BaniterioColors.brandBright,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onVolverALogin() },
                    )
                }
            }
        }
    }
}
