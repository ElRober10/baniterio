package com.baniterio.app.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.AdminRepository
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark

@Composable
fun AdminPermisosScreen(adminRepo: AdminRepository, onVolver: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        BaniterioWordmark()
        Text("Permisos", color = MaterialTheme.colorScheme.onBackground)
        Text("Volver", color = BaniterioColors.brandBright, modifier = Modifier.clickable { onVolver() })
    }
}
