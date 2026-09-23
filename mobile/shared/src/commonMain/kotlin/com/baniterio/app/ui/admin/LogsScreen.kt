package com.baniterio.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.AdminRepository
import com.baniterio.app.data.ResultadoAdmin
import com.baniterio.app.data.dto.LogEventoDto
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.ui.comun.CabeceraPantalla
import com.baniterio.app.ui.comun.EstadoCarga
import com.baniterio.app.ui.comun.PantallaConEstado
import com.baniterio.app.ui.comun.relieveDeCarta

private const val TAMANO_PAGINA = 50

/**
 * Registro de eventos y errores: solo lectura, paginado con "Cargar más". Toda
 * petición de escritura de la API y los errores de móvil/web que nunca llegaron
 * al backend. Solo se llega desde el índice de administración, que solo la
 * ofrece a admins/superadmins.
 */
@Composable
fun LogsScreen(
    adminRepo: AdminRepository,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoCarga<Unit>>(EstadoCarga.Cargando) }
    var filas by remember { mutableStateOf<List<LogEventoDto>>(emptyList()) }
    var total by remember { mutableStateOf(0L) }
    var pagina by remember { mutableStateOf(0) }
    var intento by remember { mutableStateOf(0) }

    LaunchedEffect(pagina, intento) {
        if (pagina == 0) estado = EstadoCarga.Cargando
        when (val r = adminRepo.logs(pagina = pagina, tamano = TAMANO_PAGINA)) {
            is ResultadoAdmin.Exito -> {
                filas = if (pagina == 0) r.dato.contenido else filas + r.dato.contenido
                total = r.dato.total
                estado = EstadoCarga.Cargado(Unit)
            }
            is ResultadoAdmin.Error -> estado = EstadoCarga.Error(r.mensaje)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CabeceraPantalla(onVolver)
        Text(
            "Registro de eventos y errores",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        PantallaConEstado(estado, onReintentar = { pagina = 0; intento++ }) {
            if (filas.isEmpty()) {
                Text("Todavía no hay nada registrado.", color = BaniterioColors.muted)
            }
            filas.forEach { f ->
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .relieveDeCarta(RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Text("${f.creadoEn}  ·  ${f.origen}", color = BaniterioColors.muted,
                        style = MaterialTheme.typography.bodySmall)
                    Text(f.ruta ?: "—", fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        listOfNotNull(f.usuarioNombre, f.metodo, f.estado?.toString(), f.codigoError ?: f.mensaje)
                            .joinToString(" · "),
                        color = BaniterioColors.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if ((pagina + 1) * TAMANO_PAGINA < total) {
                OutlinedButton(onClick = { pagina++ }) { Text("Cargar más") }
            }
        }
    }
}
