package com.sge.finca.ui.screens.animals

import com.sge.finca.domain.model.RanchExit
import com.sge.finca.domain.model.TrackedAnimal

data class AnimalMapUiState(
    val animals: List<TrackedAnimal> = emptyList(),
    val selectedAnimalId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** ¿Hay potreros que vigilar? Sin ellos no se sabe si una vaca está dentro o fuera. */
    val hasRanch: Boolean = false,
    /** Vacas que están fuera del rancho ahora mismo. */
    val ranchExits: List<RanchExit> = emptyList(),
) {
    val selectedAnimal: TrackedAnimal?
        get() = animals.firstOrNull { it.animal.id == selectedAnimalId }

    /** Estado respecto al rancho de la vaca seleccionada. */
    val selectedRanchPresence: RanchPresence
        get() = ranchPresenceOf(selectedAnimalId, hasRanch, ranchExits)
}
