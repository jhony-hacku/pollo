package com.sge.finca.ui.screens.chat

import com.sge.finca.domain.model.ChatMessage

data class ChatUiState(
    val isLoading: Boolean = true,
    /** Nombre de la otra persona dentro de la conversación. */
    val title: String = "",
    /** Correo de la otra persona. */
    val participantEmail: String = "",
    /** Rol de la otra persona dentro de la finca. */
    val participantRole: String = "",
    /** Foto de perfil de la otra persona. */
    val participantPhotoUrl: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val myUid: String? = null,
    /** Indica que una imagen se está subiendo y enviando. */
    val isSendingImage: Boolean = false,
    val errorMessage: String? = null,
)
