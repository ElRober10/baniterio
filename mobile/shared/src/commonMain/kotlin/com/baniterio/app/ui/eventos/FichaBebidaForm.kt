package com.baniterio.app.ui.eventos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.dto.BebidaRefDto
import com.baniterio.app.data.dto.CatalogoBebidasDto
import com.baniterio.app.data.dto.FichaBebidaBody
import com.baniterio.app.data.dto.FichaBebidaMiaDto
import com.baniterio.app.theme.BaniterioColors

private const val OTRA = -1L

private val ALTERNATIVAS = listOf(
    "NADA" to "Nada",
    "CERVEZA" to "Cerveza",
    "TINTO_VERANO" to "Tinto de verano",
    "CERVEZA_ESPECIAL" to "Cerveza especial",
)

/**
 * Formulario de la ficha de bebida de San Miguel. Reutilizable: el detalle del
 * evento, la pantalla de convocatoria y el alta manual lo montan igual. No hace
 * ninguna llamada: al pulsar el botón emite [onGuardar] con el cuerpo listo.
 */
@Composable
fun FichaBebidaForm(
    catalogo: CatalogoBebidasDto,
    dias: List<String>,
    fichaActual: FichaBebidaMiaDto?,
    enDuda: Boolean,
    onGuardar: (FichaBebidaBody) -> Unit,
    textoBoton: String = "Guardar ficha",
) {
    val dosDias = dias.size == 2
    var embarazada by remember { mutableStateOf(fichaActual?.embarazada ?: false) }
    var alcoholId by remember { mutableStateOf(fichaActual?.alcoholBebidaId) }
    var alcoholOtra by remember { mutableStateOf("") }
    var refrescoId by remember { mutableStateOf(fichaActual?.refrescoBebidaId) }
    var refrescoOtra by remember { mutableStateOf("") }
    var alternativa by remember { mutableStateOf(fichaActual?.alternativa ?: "NADA") }
    var cervezaEspecial by remember { mutableStateOf(fichaActual?.cervezaEspecial ?: "") }
    var dia1 by remember { mutableStateOf(fichaActual?.asisteDia1 ?: true) }
    var dia2 by remember { mutableStateOf(fichaActual?.asisteDia2 ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (enDuda) "¿Qué beberías si al final vas?" else "¿Qué vas a beber?",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = embarazada, onCheckedChange = { embarazada = it })
            Text("Estoy embarazada (solo refresco)", color = MaterialTheme.colorScheme.onBackground)
        }

        if (!embarazada) {
            SelectorBebida("Bebida alcohólica", catalogo.alcohol, alcoholId,
                incluyeNinguno = true, ningunoTexto = "No bebo alcohol",
                onElegir = { alcoholId = it; if (it != OTRA) alcoholOtra = "" })
            if (alcoholId == OTRA) {
                OutlinedTextField(alcoholOtra, { alcoholOtra = it },
                    label = { Text("¿Cuál? (la revisa un admin)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            SelectorAlternativa(alternativa) { alternativa = it }
            if (alternativa == "CERVEZA_ESPECIAL") {
                OutlinedTextField(cervezaEspecial, { cervezaEspecial = it },
                    label = { Text("¿Cuál?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }

        SelectorBebida("Refresco", catalogo.refresco, refrescoId,
            incluyeNinguno = false, ningunoTexto = "",
            onElegir = { refrescoId = it; if (it != OTRA) refrescoOtra = "" })
        if (refrescoId == OTRA) {
            OutlinedTextField(refrescoOtra, { refrescoOtra = it },
                label = { Text("¿Cuál? (la revisa un admin)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }

        if (dosDias) {
            Text("¿Qué días vas?", color = BaniterioColors.muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = dia1, onCheckedChange = { dia1 = it })
                Text(dias[0], color = MaterialTheme.colorScheme.onBackground)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = dia2, onCheckedChange = { dia2 = it })
                Text(dias[1], color = MaterialTheme.colorScheme.onBackground)
            }
        }

        error?.let { Text(it, color = BaniterioColors.gold) }

        Button(
            onClick = {
                val refOtra = if (refrescoId == OTRA) refrescoOtra.trim() else ""
                val refId = if (refrescoId != null && refrescoId != OTRA) refrescoId else null
                val va1 = if (dosDias) dia1 else true
                val va2 = if (dosDias) dia2 else true
                error = when {
                    refId == null && refOtra.isEmpty() -> "Elige un refresco."
                    !embarazada && alternativa == "CERVEZA_ESPECIAL" && cervezaEspecial.isBlank() ->
                        "Escribe cuál es tu cerveza especial."
                    !va1 && !va2 -> "Marca al menos un día."
                    else -> null
                }
                if (error != null) return@Button
                val alcOtra = if (!embarazada && alcoholId == OTRA) alcoholOtra.trim() else ""
                val alcId = if (!embarazada && alcoholId != null && alcoholId != OTRA) alcoholId else null
                onGuardar(
                    FichaBebidaBody(
                        estado = if (enDuda) "EN_DUDA" else "APUNTADO",
                        alcoholBebidaId = alcId,
                        alcoholOtra = alcOtra.ifEmpty { null },
                        refrescoBebidaId = refId,
                        refrescoOtra = refOtra.ifEmpty { null },
                        alternativa = if (embarazada) "NADA" else alternativa,
                        cervezaEspecial = if (!embarazada && alternativa == "CERVEZA_ESPECIAL")
                            cervezaEspecial.trim() else null,
                        embarazada = embarazada,
                        asisteDia1 = va1,
                        asisteDia2 = va2,
                    ),
                )
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = BaniterioColors.brand,
                contentColor = BaniterioColors.gold,
            ),
        ) { Text(textoBoton, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun SelectorBebida(
    etiqueta: String,
    opciones: List<BebidaRefDto>,
    elegidoId: Long?,
    incluyeNinguno: Boolean,
    ningunoTexto: String,
    onElegir: (Long?) -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }
    val texto = when {
        elegidoId == OTRA -> "Otra…"
        elegidoId == null -> if (incluyeNinguno) ningunoTexto else "Elige…"
        else -> opciones.firstOrNull { it.id == elegidoId }?.nombre ?: "Elige…"
    }
    Column {
        Text(etiqueta, color = BaniterioColors.muted, style = MaterialTheme.typography.bodySmall)
        Box {
            OutlinedButton(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth()) {
                Text(texto)
            }
            DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                if (incluyeNinguno) {
                    DropdownMenuItem(text = { Text(ningunoTexto) },
                        onClick = { onElegir(null); abierto = false })
                }
                opciones.forEach { b ->
                    DropdownMenuItem(text = { Text(b.nombre) },
                        onClick = { onElegir(b.id); abierto = false })
                }
                DropdownMenuItem(text = { Text("Otra…") },
                    onClick = { onElegir(OTRA); abierto = false })
            }
        }
    }
}

@Composable
private fun SelectorAlternativa(elegida: String, onElegir: (String) -> Unit) {
    Column {
        Text("Para alternar", color = BaniterioColors.muted, style = MaterialTheme.typography.bodySmall)
        ALTERNATIVAS.forEach { (valor, texto) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onElegir(valor) },
            ) {
                RadioButton(selected = elegida == valor, onClick = { onElegir(valor) })
                Text(texto, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}
