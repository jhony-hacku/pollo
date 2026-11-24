package com.sge.finca.ui.screens.animals

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sge.finca.domain.model.RanchExit

/** Dónde está una vaca respecto al rancho. */
enum class RanchPresence {
    INSIDE,
    OUTSIDE,

    /** La finca aún no tiene potreros: no hay límite contra qué comparar. */
    NO_RANCH,
}

/** Estado de [animalId] según las vacas que están fuera ([exits]). */
fun ranchPresenceOf(animalId: String?, hasRanch: Boolean, exits: List<RanchExit>): RanchPresence = when {
    !hasRanch || animalId == null -> RanchPresence.NO_RANCH
    exits.any { it.animalId == animalId } -> RanchPresence.OUTSIDE
    else -> RanchPresence.INSIDE
}

/** Etiqueta "Dentro del rancho" (verde) o "Fuera del rancho" (rojo). Sin potreros no se muestra. */
@Composable
fun RanchStatusLabel(presence: RanchPresence, modifier: Modifier = Modifier) {
    val (text, background) = when (presence) {
        RanchPresence.INSIDE -> "Dentro del rancho" to Color(0xFF2E7D32)
        RanchPresence.OUTSIDE -> "Fuera del rancho" to Color(0xFFC62828)
        RanchPresence.NO_RANCH -> return
    }
    Surface(
        modifier = modifier.padding(vertical = 2.dp),
        shape = RoundedCornerShape(50),
        color = background,
        contentColor = Color.White,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}
