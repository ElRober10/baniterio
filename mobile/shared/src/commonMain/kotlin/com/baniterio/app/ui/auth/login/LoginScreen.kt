package com.baniterio.app.ui.auth.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.AlmacenCredenciales
import com.baniterio.app.data.AuthRepository
import com.baniterio.app.data.ResultadoAuth
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import kotlinx.coroutines.launch

private sealed interface EstadoLogin {
    data object Editando : EstadoLogin
    data object Enviando : EstadoLogin
    data class Error(val mensaje: String) : EstadoLogin
}

@Composable
fun LoginScreen(
    repo: AuthRepository,
    almacen: AlmacenCredenciales,
    onLoginSuccess: () -> Unit,
    onIrARegistro: () -> Unit,
) {
    var telefono by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var estado by remember { mutableStateOf<EstadoLogin>(EstadoLogin.Editando) }
    var ofrecerBiometria by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val enviando = estado is EstadoLogin.Enviando

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BaniterioWordmark()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Accede a la peña",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Introduce tu teléfono y contraseña para entrar.",
            style = MaterialTheme.typography.bodyMedium,
            color = BaniterioColors.muted,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = telefono,
            onValueChange = { telefono = it },
            label = { Text("Teléfono") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            enabled = !enviando,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !enviando,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (telefono.isNotBlank() && password.isNotBlank()) {
                    estado = EstadoLogin.Enviando
                    scope.launch {
                        when (val r = repo.login(telefono, password)) {
                            is ResultadoAuth.Exito ->
                                if (almacen.hayCredenciales) onLoginSuccess()
                                else ofrecerBiometria = true
                            is ResultadoAuth.Error -> estado = EstadoLogin.Error(r.mensaje)
                        }
                    }
                }
            },
            enabled = !enviando,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = BaniterioColors.brand,
                contentColor = BaniterioColors.gold,
            ),
        ) {
            Text(if (enviando) "Entrando…" else "Entrar", fontWeight = FontWeight.Bold)
        }

        val estadoActual = estado
        if (estadoActual is EstadoLogin.Error) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = estadoActual.mensaje,
                color = Color(0xFFFF6B6B),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Text("¿No tienes cuenta todavía? ", color = BaniterioColors.muted)
            Text(
                text = "Regístrate",
                color = BaniterioColors.brandBright,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onIrARegistro() },
            )
        }
    }

    if (ofrecerBiometria) {
        AlertDialog(
            onDismissRequest = {
                ofrecerBiometria = false
                onLoginSuccess()
            },
            title = { Text("Entrar con huella") },
            text = { Text("¿Quieres usar tu huella o Face ID para entrar la próxima vez?") },
            confirmButton = {
                TextButton(onClick = {
                    almacen.guardar(telefono, password)
                    onLoginSuccess()
                }) {
                    Text("Sí")
                }
            },
            dismissButton = {
                TextButton(onClick = { onLoginSuccess() }) {
                    Text("Ahora no")
                }
            },
        )
    }
}
