package com.sge.finca.ui.screens.animals

import com.sge.finca.domain.model.RanchExit
import com.sge.finca.domain.model.TrackedAnimal

data class AnimalListUiState(
    val animals: List<TrackedAnimal> = emptyList(),
    val isLoading: Boolean = true,
    /** ¿Hay potreros que vigilar? Sin ellos no se muestra "Dentro / Fuera del rancho". */
    val hasRanch: Boolean = false,
    /** Vacas que están fuera del rancho ahora mismo. */
    val ranchExits: List<RanchExit> = emptyList(),
)
