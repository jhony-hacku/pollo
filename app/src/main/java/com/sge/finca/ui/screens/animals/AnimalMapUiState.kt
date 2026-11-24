package com.sge.finca.ui.screens.animals

import com.sge.finca.domain.model.Coordinate
import com.sge.finca.domain.model.RanchExit
import com.sge.finca.domain.model.TrackedAnimal

data class AnimalMapUiState(
    val animals: List<TrackedAnimal> = emptyList(),
    val selectedAnimalId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Límite del rancho (envolvente de los potreros); vacío si aún no hay potreros. */
    val ranchPerimeter: List<Coordinate> = emptyList(),
    /** Vacas que están fuera del rancho ahora mismo. */
    val ranchExits: List<RanchExit> = emptyList(),
) {
    val selectedAnimal: TrackedAnimal?
        get() = animals.firstOrNull { it.animal.id == selectedAnimalId }

    /** ¿La vaca seleccionada está fuera del rancho? */
    val selectedIsOutsideRanch: Boolean
        get() = selectedAnimalId != null && ranchExits.any { it.animalId == selectedAnimalId }
}
