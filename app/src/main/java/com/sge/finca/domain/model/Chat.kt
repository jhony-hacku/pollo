package com.sge.finca.domain.model

/**
 * Chat uno a uno entre el propietario y un trabajador.
 *
 * Cada conversación se identifica con el uid del trabajador:
 * farms/{farmId}/chats/{workerUid}/messages/{id}
 */
data class ChatMessage(
    val id: String,
    val senderUid: String,
    val text: String,
    val imageUrl: String = "",
    val createdAt: Long,
) {
    val hasText: Boolean
        get() = text.isNotBlank()

    val hasImage: Boolean
        get() = imageUrl.isNotBlank()

    companion object {
        const val MAX_LENGTH = 1000
        const val MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024
    }
}

/** Resumen de una conversación para la lista de Mensajes. */
data class ChatThread(
    val workerUid: String,
    val lastMessage: String,
    val lastMessageAt: Long,
    val lastSenderUid: String,
)