package com.sge.finca.ui.screens.chat

import com.sge.finca.domain.model.ChatMessage

data class ChatUiState(
    val isLoading: Boolean = true,
    /** Nombre de la otra persona dentro de la conversación. */
    val title: String = "",
    /** Foto de perfil de la otra persona. */
    val participantPhotoUrl: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val myUid: String? = null,
    val errorMessage: String? = null,
)