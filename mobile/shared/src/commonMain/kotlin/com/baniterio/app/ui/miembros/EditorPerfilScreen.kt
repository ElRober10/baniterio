package com.baniterio.app.ui.miembros

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.PerfilRepository

// Stub — lo sustituye Task 5.
@Composable
fun EditorPerfilScreen(
    perfilRepo: PerfilRepository,
    obligatorio: Boolean,
    onGuardado: () -> Unit,
    onVolver: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Editor de perfil (pendiente)")
    }
}
