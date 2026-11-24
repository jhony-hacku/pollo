package com.sge.finca.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.sge.finca.BuildConfig
import com.sge.finca.data.remote.FirestorePaths
import com.sge.finca.data.remote.toChatMessage
import com.sge.finca.data.remote.toChatThread
import com.sge.finca.domain.model.ChatMessage
import com.sge.finca.domain.model.ChatThread
import com.sge.finca.domain.model.Permission
import com.sge.finca.domain.repository.ChatRepository
import com.sge.finca.domain.repository.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * El chat utiliza Firestore para los mensajes en tiempo real.
 * Las imágenes se almacenan en Cloudinary y Firestore conserva únicamente su URL.
 */
@Singleton
class FirestoreChatRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val sessionRepository: SessionRepository,
    private val httpClient: OkHttpClient,
) : ChatRepository {

    override fun observeThreads(
        farmId: String,
    ): Flow<List<ChatThread>> = callbackFlow {
        val registration = firestore
            .collection(
                FirestorePaths.farmCollection(
                    farmId,
                    FirestorePaths.CHATS,
                ),
            )
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "No se pudo escuchar las conversaciones de $farmId", error)
                    close(error)
                    return@addSnapshotListener
                }

                val threads = snapshot
                    ?.documents
                    .orEmpty()
                    .mapNotNull { it.toChatThread() }

                trySend(threads)
            }

        awaitClose {
            registration.remove()
        }
    }

    override fun observeMessages(
        farmId: String,
        workerUid: String,
    ): Flow<List<ChatMessage>> = callbackFlow {
        val registration = firestore
            .collection(
                FirestorePaths.chatMessages(
                    farmId,
                    workerUid,
                ),
            )
            .orderBy(
                "createdAt",
                Query.Direction.ASCENDING,
            )
            .limitToLast(MESSAGES_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "No se pudo escuchar el chat $farmId/$workerUid", error)
                    close(error)
                    return@addSnapshotListener
                }

                val messages = snapshot
                    ?.documents
                    .orEmpty()
                    .mapNotNull { it.toChatMessage() }

                trySend(messages)
            }

        awaitClose {
            registration.remove()
        }
    }

    override suspend fun send(
        farmId: String,
        workerUid: String,
        text: String,
    ) {
        val uid = requireChatAccess(
            farmId = farmId,
            workerUid = workerUid,
        )

        val cleanText = text.trim()

        require(cleanText.isNotEmpty()) {
            "Escribe un mensaje"
        }

        require(cleanText.length <= ChatMessage.MAX_LENGTH) {
            "El mensaje es muy largo " +
                "(máximo ${ChatMessage.MAX_LENGTH} caracteres)"
        }

        saveMessage(
            farmId = farmId,
            workerUid = workerUid,
            senderUid = uid,
            text = cleanText,
            imageUrl = "",
            preview = cleanText.take(PREVIEW_LENGTH),
        )
    }

    override suspend fun sendImage(
        farmId: String,
        workerUid: String,
        selectedImageUri: String,
    ) {
        val uid = requireChatAccess(
            farmId = farmId,
            workerUid = workerUid,
        )

        require(selectedImageUri.isNotBlank()) {
            "Selecciona una imagen"
        }

        val imageUrl = uploadImageToCloudinary(
            farmId = farmId,
            selectedImageUri = selectedImageUri,
        )

        saveMessage(
            farmId = farmId,
            workerUid = workerUid,
            senderUid = uid,
            text = "",
            imageUrl = imageUrl,
            preview = IMAGE_PREVIEW,
        )
    }

    private suspend fun requireChatAccess(
        farmId: String,
        workerUid: String,
    ): String {
        sessionRepository.requirePermission(
            farmId,
            Permission.CHAT,
        )

        val session = sessionRepository.session.value
        val uid = checkNotNull(session.uid) {
            "Inicia sesión para enviar mensajes"
        }

        if (!session.isOwner && uid != workerUid) {
            throw SecurityException(
                "Solo puedes escribir en tu conversación con el propietario",
            )
        }

        return uid
    }

    private suspend fun saveMessage(
        farmId: String,
        workerUid: String,
        senderUid: String,
        text: String,
        imageUrl: String,
        preview: String,
    ) {
        val now = System.currentTimeMillis()

        val chat = firestore.document(
            FirestorePaths.chat(
                farmId,
                workerUid,
            ),
        )

        val message = chat
            .collection(FirestorePaths.MESSAGES)
            .document()

        val messageData = mapOf(
            "senderUid" to senderUid,
            "text" to text,
            "imageUrl" to imageUrl,
            "createdAt" to now,
        )

        val threadData = mapOf(
            "lastMessage" to preview,
            "lastMessageAt" to now,
            "lastSenderUid" to senderUid,
        )

        val commit = firestore.batch()
            .set(message, messageData)
            .set(chat, threadData)
            .commit()

        // Con señal, unos segundos bastan para saber si el servidor lo rechazó. Sin señal no se
        // espera: Firestore conserva la operación en su cola local.
        try {
            withTimeoutOrNull(CONFIRM_TIMEOUT_MS) {
                commit.await()
            }
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "Firestore rechazó el mensaje en $farmId/$workerUid", e)
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw SecurityException("No se pudo enviar: no tienes acceso a este chat")
            }
            throw IllegalStateException("No se pudo enviar el mensaje. Intenta de nuevo")
        }
    }

    private suspend fun uploadImageToCloudinary(
        farmId: String,
        selectedImageUri: String,
    ): String = withContext(Dispatchers.IO) {
        val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
        val uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET

        check(cloudName.isNotBlank()) {
            "Falta configurar CLOUDINARY_CLOUD_NAME"
        }

        check(uploadPreset.isNotBlank()) {
            "Falta configurar CLOUDINARY_UPLOAD_PRESET"
        }

        val uri = Uri.parse(selectedImageUri)
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri).orEmpty()

        require(mimeType.startsWith("image/")) {
            "El archivo seleccionado no es una imagen válida"
        }

        val imageBytes = contentResolver
            .openInputStream(uri)
            ?.use { inputStream ->
                inputStream.readBytes()
            }
            ?: throw IllegalArgumentException(
                "No se pudo leer la imagen seleccionada",
            )

        require(imageBytes.size <= ChatMessage.MAX_IMAGE_SIZE_BYTES) {
            "La imagen supera el tamaño máximo de 5 MB"
        }

        val fileBody = imageBytes.toRequestBody(
            mimeType.toMediaTypeOrNull(),
        )

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "upload_preset",
                uploadPreset,
            )
            .addFormDataPart(
                "folder",
                "sge/chatImages/$farmId",
            )
            .addFormDataPart(
                "file",
                "chat_${System.currentTimeMillis()}",
                fileBody,
            )
            .build()

        val request = Request.Builder()
            .url(
                "https://api.cloudinary.com/v1_1/" +
                    "$cloudName/image/upload",
            )
            .post(multipartBody)
            .build()

        httpClient
            .newCall(request)
            .execute()
            .use { response ->
                val responseBody = response.body
                    ?.string()
                    .orEmpty()

                if (!response.isSuccessful) {
                    val cloudinaryError = runCatching {
                        JSONObject(responseBody)
                            .optJSONObject("error")
                            ?.optString("message")
                    }.getOrNull()

                    throw IllegalStateException(
                        cloudinaryError
                            ?.takeIf { it.isNotBlank() }
                            ?: "Cloudinary rechazó la imagen",
                    )
                }

                val secureUrl = JSONObject(responseBody)
                    .optString("secure_url")

                check(secureUrl.isNotBlank()) {
                    "Cloudinary no devolvió la URL de la imagen"
                }

                secureUrl
            }
    }

    private companion object {
        const val MESSAGES_LIMIT = 200L
        const val PREVIEW_LENGTH = 200
        const val CONFIRM_TIMEOUT_MS = 5_000L
        const val IMAGE_PREVIEW = "📷 Imagen"
        const val TAG = "SgeChat"
    }
}
