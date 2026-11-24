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

    private val workerUid: String =
        checkNotNull(savedStateHandle["workerUid"])

    private val error = MutableStateFlow<String?>(null)
    private val sendingImage = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChatUiState> = sessionRepository.session
        .filter { !it.loading }
        .flatMapLatest { session ->
            val farmId = session.farmId
                ?: return@flatMapLatest flowOf(
                    ChatUiState(isLoading = false),
                )

            val participant: Flow<ChatParticipant> =
                if (session.isOwner) {
                    workersRepository
                        .observeWorkers(farmId)
                        .map { workers ->
                            val worker = workers.firstOrNull {
                                it.uid == workerUid
                            }

                            ChatParticipant(
                                name = worker?.name
                                    ?.takeIf { it.isNotBlank() }
                                    ?: worker?.email
                                        ?.takeIf { it.isNotBlank() }
                                    ?: "Trabajador",
                                email = worker?.email.orEmpty(),
                                role = "Trabajador de la finca",
                                photoUrl = worker?.photoUrl.orEmpty(),
                            )
                        }
                } else {
                    workersRepository
                        .observeOwner(farmId)
                        .map { owner ->
                            ChatParticipant(
                                name = owner?.name
                                    ?.takeIf { it.isNotBlank() }
                                    ?: owner?.email
                                        ?.takeIf { it.isNotBlank() }
                                    ?: "Propietario",
                                email = owner?.email.orEmpty(),
                                role = "Propietario de la finca",
                                photoUrl = owner?.photoUrl.orEmpty(),
                            )
                        }
                }

            combine(
                chatRepository.observeMessages(
                    farmId,
                    workerUid,
                ),
                participant,
                error,
                sendingImage,
            ) { messages, otherPerson, pendingError, isSendingImage ->
                ChatUiState(
                    isLoading = false,
                    title = otherPerson.name,
                    participantEmail = otherPerson.email,
                    participantRole = otherPerson.role,
                    participantPhotoUrl = otherPerson.photoUrl,
                    messages = messages,
                    myUid = session.uid,
                    isSendingImage = isSendingImage,
                    errorMessage = pendingError,
                )
            }
        }
        .catch { caughtError ->
            Log.e(
                "SgeChat",
                "No se pudo cargar la conversación",
                caughtError,
            )
            emit(
                ChatUiState(
                    isLoading = false,
                    errorMessage =
                        "No se pudo cargar la conversación. " +
                                "Revisa tu conexión e intenta de nuevo",
                ),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChatUiState(),
        )

    fun onSend(text: String) {
        val farmId = sessionRepository.session.value.farmId
            ?: return

        viewModelScope.launch {
            try {
                chatRepository.send(
                    farmId = farmId,
                    workerUid = workerUid,
                    text = text,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value =
                    e.message ?: "No se pudo enviar el mensaje"
            }
        }
    }

    fun onSendImage(selectedImageUri: String) {
        val farmId = sessionRepository.session.value.farmId
            ?: return

        if (sendingImage.value) {
            return
        }

        viewModelScope.launch {
            sendingImage.value = true
            error.value = null

            try {
                chatRepository.sendImage(
                    farmId = farmId,
                    workerUid = workerUid,
                    selectedImageUri = selectedImageUri,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value =
                    e.message ?: "No se pudo enviar la imagen"
            } finally {
                sendingImage.value = false
            }
        }
    }

    fun onErrorShown() {
        error.value = null
    }

    private data class ChatParticipant(
        val name: String,
        val email: String,
        val role: String,
        val photoUrl: String,
    )
}