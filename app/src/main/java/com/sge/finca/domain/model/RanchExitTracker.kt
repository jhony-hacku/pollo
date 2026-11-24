package com.sge.finca.domain.model

/** Vaca que está fuera del rancho ahora mismo. */
data class RanchExit(
    val animalId: String,
    val animalName: String,
    /** Última posición conocida fuera del rancho. */
    val position: Coordinate,
    /** Cuándo se confirmó la salida (epoch ms). */
    val since: Long,
)

/** Cambio de estado de una vaca respecto al rancho. */
sealed interface RanchEvent {
    data class Exited(val exit: RanchExit) : RanchEvent
    data class Returned(val animalId: String, val animalName: String) : RanchEvent
}

/**
 * Decide cuándo una vaca salió del rancho y cuándo volvió, a partir de las lecturas de los collares.
 *
 * Una sola lectura no basta (el GPS salta): la salida se confirma con [confirmReadings] lecturas
 * seguidas fuera, y el regreso con otras tantas dentro. Así no llega una alerta por cada brinco del
 * GPS ni se repite mientras la vaca sigue afuera: un aviso al salir y otro al volver.
 *
 * Es lógica pura (sin Android) para poder probarla; no es segura entre hilos, se usa desde una sola
 * corrutina.
 */
class RanchExitTracker(
    private val confirmReadings: Int = DEFAULT_CONFIRM_READINGS,
    private val marginM: Double = RanchGeometry.DEFAULT_MARGIN_M,
) {
    init {
        require(confirmReadings >= 1) { "confirmReadings debe ser al menos 1" }
    }

    private val outsideStreak = mutableMapOf<String, Int>()
    private val insideStreak = mutableMapOf<String, Int>()
    private val exits = linkedMapOf<String, RanchExit>()

    /** Vacas que están fuera del rancho, en el orden en que salieron. */
    val currentExits: List<RanchExit> get() = exits.values.toList()

    /**
     * Procesa una lectura de todas las vacas y devuelve lo que cambió.
     * [areas] son los potreros (ver [RanchGeometry.areas]). Sin áreas no se evalúa nada y se
     * olvida el estado anterior.
     */
    fun update(areas: List<List<Coordinate>>, animals: List<TrackedAnimal>, nowMs: Long): List<RanchEvent> {
        if (areas.none { it.size >= 3 }) {
            reset()
            return emptyList()
        }
        val events = mutableListOf<RanchEvent>()
        val present = animals.mapTo(mutableSetOf()) { it.animal.id }

        // Vacas eliminadas: dejar de seguirlas sin avisar.
        (exits.keys + outsideStreak.keys + insideStreak.keys)
            .filter { it !in present }
            .forEach { forget(it) }

        for (tracked in animals) {
            val id = tracked.animal.id
            val position = Coordinate(tracked.latitude, tracked.longitude)
            val outside = RanchGeometry.isOutside(areas, position, marginM)
            val current = exits[id]

            if (outside) {
                insideStreak.remove(id)
                if (current != null) {
                    // Sigue afuera: solo actualizar dónde está.
                    exits[id] = current.copy(position = position, animalName = tracked.animal.name)
                    continue
                }
                val streak = (outsideStreak[id] ?: 0) + 1
                outsideStreak[id] = streak
                if (streak >= confirmReadings) {
                    outsideStreak.remove(id)
                    val exit = RanchExit(id, tracked.animal.name, position, nowMs)
                    exits[id] = exit
                    events += RanchEvent.Exited(exit)
                }
            } else {
                outsideStreak.remove(id)
                if (current == null) continue
                val streak = (insideStreak[id] ?: 0) + 1
                insideStreak[id] = streak
                if (streak >= confirmReadings) {
                    insideStreak.remove(id)
                    exits.remove(id)
                    events += RanchEvent.Returned(id, tracked.animal.name)
                }
            }
        }
        return events
    }

    /** Olvida todo (por ejemplo, al cambiar de finca o cerrar sesión). */
    fun reset() {
        outsideStreak.clear()
        insideStreak.clear()
        exits.clear()
    }

    private fun forget(id: String) {
        outsideStreak.remove(id)
        insideStreak.remove(id)
        exits.remove(id)
    }

    companion object {
        /** Con lecturas cada 3 s, la salida se confirma en ~6 s. */
        const val DEFAULT_CONFIRM_READINGS = 2
    }
}
