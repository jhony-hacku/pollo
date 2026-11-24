package com.sge.finca.ui.screens.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sge.finca.data.repository.WorkersRepository
import com.sge.finca.domain.repository.ChatRepository
import com.sge.finca.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Conversación entre el propietario y el trabajador indicado en la ruta. */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val chatRepository: ChatRepository,
    workersRepository: WorkersRepository,
) : ViewModel() {

    private val workerUid: String = checkNotNull(savedStateHandle["workerUid"])
    private val error = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChatUiState> = sessionRepository.session
        .filter { !it.loading }
        .flatMapLatest { session ->
            val farmId = session.farmId
                ?: return@flatMapLatest flowOf(
                    ChatUiState(isLoading = false),
                )

            val participant: Flow<ChatParticipant> = if (session.isOwner) {
                workersRepository.observeWorkers(farmId).map { workers ->
                    val worker = workers.firstOrNull { it.uid == workerUid }

                    ChatParticipant(
                        name = worker?.name
                            ?.takeIf { it.isNotBlank() }
                            ?: worker?.email
                                ?.takeIf { it.isNotBlank() }
                            ?: "Trabajador",
                        photoUrl = worker?.photoUrl.orEmpty(),
                    )
                }
            } else {
                workersRepository.observeOwner(farmId).map { owner ->
                    ChatParticipant(
                        name = owner?.name
                            ?.takeIf { it.isNotBlank() }
                            ?: owner?.email
                                ?.takeIf { it.isNotBlank() }
                            ?: "Propietario",
                        photoUrl = owner?.photoUrl.orEmpty(),
                    )
                }
            }

            combine(
                chatRepository.observeMessages(farmId, workerUid),
                participant,
                error,
            ) { messages, otherPerson, pendingError ->
                ChatUiState(
                    isLoading = false,
                    title = otherPerson.name,
                    participantPhotoUrl = otherPerson.photoUrl,
                    messages = messages,
                    myUid = session.uid,
                    errorMessage = pendingError,
                )
            }
        }
        .catch { error ->
            Log.e("SgeChat", "No se pudo cargar la conversación", error)
            emit(
                ChatUiState(
                    isLoading = false,
                    errorMessage = "No se pudo cargar la conversación. Revisa tu conexión e intenta de nuevo",
                ),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChatUiState(),
        )

    fun onSend(text: String) {
        val farmId = sessionRepository.session.value.farmId ?: return

        viewModelScope.launch {
            try {
                chatRepository.send(farmId, workerUid, text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: "No se pudo enviar el mensaje"
            }
        }
    }

    fun onErrorShown() {
        error.value = null
    }

    private data class ChatParticipant(
        val name: String,
        val photoUrl: String,
    )
}