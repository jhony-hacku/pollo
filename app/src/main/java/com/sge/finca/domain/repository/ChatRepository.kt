package com.sge.finca.domain.repository

import com.sge.finca.domain.model.ChatMessage
import com.sge.finca.domain.model.ChatThread
import kotlinx.coroutines.flow.Flow

/** Chat entre el propietario y un trabajador. */
interface ChatRepository {

    /** Conversaciones de la finca; solo el propietario puede listarlas todas. */
    fun observeThreads(farmId: String): Flow<List<ChatThread>>

    /** Mensajes de la conversación, ordenados del más antiguo al más reciente. */
    fun observeMessages(
        farmId: String,
        workerUid: String,
    ): Flow<List<ChatMessage>>

    /**
     * Envía un mensaje de texto.
     *
     * @throws IllegalArgumentException si el texto está vacío o es demasiado largo.
     * @throws SecurityException si el usuario no participa en la conversación.
     */
    suspend fun send(
        farmId: String,
        workerUid: String,
        text: String,
    )

    /**
     * Sube una imagen a Cloudinary y envía su URL en la conversación.
     *
     * @param selectedImageUri URI de la imagen seleccionada desde la galería.
     * @throws IllegalArgumentException si el archivo no es una imagen válida o supera 5 MB.
     * @throws SecurityException si el usuario no participa en la conversación.
     */
    suspend fun sendImage(
        farmId: String,
        workerUid: String,
        selectedImageUri: String,
    )
}