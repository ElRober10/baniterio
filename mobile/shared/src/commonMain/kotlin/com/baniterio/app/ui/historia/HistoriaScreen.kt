package com.baniterio.app.ui.historia

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark

@Composable
fun HistoriaScreen(onVolver: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        BaniterioWordmark()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "← Volver",
            color = BaniterioColors.muted,
            modifier = Modifier.clickable { onVolver() },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Historia del Bañiterio",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "El Bañiterio nace en Robledo del Mazo, capital del Valle del Gévalo, de la unión de dos peñas con mucha historia propia. Por un lado, El Bañito Bañito, que vistió primero de blanco y después de rojo. Por otro, El Bruterio, fiel al azul desde siempre. De la mezcla de esos dos colores —rojo y azul— nació el morado que hoy nos identifica, y de la unión de su gente, el Bañiterio.",
            style = MaterialTheme.typography.bodyLarge,
            color = BaniterioColors.muted,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Nuestro escudo nace de esa misma unión: el Bruterio llevaba una jarra de cerveza rompiéndose junto al lema «aquí no se friega», y el Bañito Bañito, un chico bañándose de noche. El logo del Bañiterio es la mezcla de los dos: el chico del Bañito tirándose en bomba dentro de la jarra del Bruterio. Y en el hombro izquierdo lleva una rosa, en recuerdo de nuestra amiga Rosi, que ya no está con nosotros pero sigue siendo parte de esta familia.",
            style = MaterialTheme.typography.bodyLarge,
            color = BaniterioColors.muted,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Pero el Bañiterio es mucho más que las fiestas del pueblo: es un grupo de amigos unido durante todo el año. Organizamos eventos propios, como las Migas Santas y las Chuletas Santas, y colaboramos de forma desinteresada en las fiestas de San Miguel, echando una mano con los juegos y con todo lo que haga falta.",
            style = MaterialTheme.typography.bodyLarge,
            color = BaniterioColors.muted,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Amistad, buen rollo y grandes amigos: así es la familia del Bañiterio.",
            style = MaterialTheme.typography.bodyLarge,
            color = BaniterioColors.goldSoft,
            fontWeight = FontWeight.Bold,
        )
    }
}
