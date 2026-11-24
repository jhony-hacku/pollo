package com.sge.finca.ui.screens.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sge.finca.domain.model.RanchExit

/** Rojo de las alertas del rancho; también se usa para marcar la vaca en el mapa. */
val RanchAlertRed = Color(0xFFC62828)

/** Texto del aviso según cuántas vacas estén afuera. */
fun ranchExitMessage(exits: List<RanchExit>): String = when (exits.size) {
    0 -> ""
    1 -> "${exits[0].animalName} salió del rancho"
    else -> "${exits.size} vacas fuera del rancho: " + exits.joinToString(", ") { it.animalName }
}

/**
 * Aviso rojo sobre el mapa cuando hay vacas fuera del rancho. Al tocarlo se selecciona la primera
 * vaca que salió, para ver su ficha.
 */
@Composable
fun RanchExitBanner(
    exits: List<RanchExit>,
    onClick: (animalId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (exits.isEmpty()) return
    Surface(
        onClick = { onClick(exits.first().animalId) },
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = RanchAlertRed,
        contentColor = Color.White,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(22.dp))
            Column {
                Text(
                    text = ranchExitMessage(exits),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Toca para ver dónde está",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
