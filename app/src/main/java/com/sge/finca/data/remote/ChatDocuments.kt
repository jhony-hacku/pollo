package com.sge.finca.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.sge.finca.domain.model.ChatMessage
import com.sge.finca.domain.model.ChatThread

/*
 * Chat propietario ↔ trabajador:
 *
 * farms/{farmId}/chats/{workerUid}
 *   lastMessage, lastMessageAt, lastSenderUid
 *
 * farms/{farmId}/chats/{workerUid}/messages/{id}
 *   senderUid, text, imageUrl, createdAt
 */

fun DocumentSnapshot.toChatMessage(): ChatMessage? {
    val text = getString("text").orEmpty()
    val imageUrl = getString("imageUrl").orEmpty()

    if (text.isBlank() && imageUrl.isBlank()) {
        return null
    }

    return ChatMessage(
        id = id,
        senderUid = getString("senderUid") ?: return null,
        text = text,
        imageUrl = imageUrl,
        createdAt = getLong("createdAt") ?: 0L,
    )
}

fun DocumentSnapshot.toChatThread(): ChatThread? {
    return ChatThread(
        workerUid = id,
        lastMessage = getString("lastMessage") ?: return null,
        lastMessageAt = getLong("lastMessageAt") ?: 0L,
        lastSenderUid = getString("lastSenderUid").orEmpty(),
    )
}