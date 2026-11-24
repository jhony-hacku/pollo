package com.sge.finca.data.ranch

import android.util.Log
import com.sge.finca.data.injection.ApplicationScope
import com.sge.finca.domain.model.Coordinate
import com.sge.finca.domain.model.RanchEvent
import com.sge.finca.domain.model.RanchExit
import com.sge.finca.domain.model.RanchExitTracker
import com.sge.finca.domain.model.RanchGeometry
import com.sge.finca.domain.repository.PaddockRepository
import com.sge.finca.domain.repository.SessionRepository
import com.sge.finca.domain.usecase.ObserveTrackedAnimalsUseCase
import com.sge.finca.platform.notifications.RanchExitNotifier
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Lo que el mapa necesita saber del rancho. */
data class RanchState(
    /** Límite del rancho (envolvente de los potreros). Vacío si aún no hay potreros. */
    val perimeter: List<Coordinate> = emptyList(),
    /** Vacas fuera del rancho ahora mismo. */
    val exits: List<RanchExit> = emptyList(),
)

/**
 * Vigila que ninguna vaca salga del rancho, dentro de la app y sin servidor.
 *
 * Mientras hay una sesión con finca: arma el límite del rancho con los potreros (Room), escucha
 * las posiciones de los collares (las mismas que ve el mapa) y, con [RanchExitTracker], avisa con
 * una notificación cuando una vaca sale y cuando vuelve. El mapa lee [state] para dibujar el
 * límite y el aviso rojo.
 *
 * Se inicia una vez desde FincaApplication. Funciona mientras la app esté abierta (también en
 * segundo plano mientras Android no cierre el proceso).
 */
@Singleton
class RanchExitMonitor @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val paddockRepository: PaddockRepository,
    private val observeTrackedAnimals: ObserveTrackedAnimalsUseCase,
    private val notifier: RanchExitNotifier,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val tracker = RanchExitTracker()
    private val _state = MutableStateFlow(RanchState())
    val state: StateFlow<RanchState> = _state.asStateFlow()

    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job != null) return
        job = scope.launch {
            sessionRepository.session
                // Solo con membresía activa, igual que la sincronización.
                .map { session -> session.farmId.takeIf { session.role != null } }
                .distinctUntilChanged()
                .collectLatest { farmId ->
                    tracker.reset()
                    _state.value = RanchState()
                    if (farmId != null) watchWithRetry(farmId)
                }
        }
    }

    /** Si algo falla (base de datos, red), se reintenta: la vigilancia no debe morir en silencio. */
    private suspend fun watchWithRetry(farmId: String) {
        var attempt = 0
        while (true) {
            try {
                watch(farmId)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                Log.w(TAG, "Falló la vigilancia del rancho (intento $attempt); se reintenta", e)
                delay((RETRY_BASE_MS * attempt).coerceAtMost(RETRY_MAX_MS))
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun watch(farmId: String) {
        paddockRepository.observePaddocks(farmId)
            .map { paddocks -> RanchGeometry.perimeter(paddocks) }
            .distinctUntilChanged()
            .flatMapLatest { perimeter ->
                // Sin potreros no hay rancho que vigilar: no se consultan los collares.
                if (perimeter.size < 3) {
                    flowOf(perimeter to emptyList())
                } else {
                    observeTrackedAnimals().map { animals -> perimeter to animals }
                }
            }
            .collect { (perimeter, animals) ->
                val events = tracker.update(perimeter, animals, System.currentTimeMillis())
                _state.value = RanchState(perimeter, tracker.currentExits)
                events.forEach { event ->
                    when (event) {
                        is RanchEvent.Exited -> {
                            Log.i(TAG, "${event.exit.animalName} salió del rancho")
                            notifier.showExit(event.exit.animalId, event.exit.animalName)
                        }
                        is RanchEvent.Returned -> {
                            Log.i(TAG, "${event.animalName} volvió al rancho")
                            notifier.showReturn(event.animalId, event.animalName)
                        }
                    }
                }
            }
    }

    private companion object {
        const val TAG = "RanchMonitor"
        const val RETRY_BASE_MS = 5_000L
        const val RETRY_MAX_MS = 60_000L
    }
}
