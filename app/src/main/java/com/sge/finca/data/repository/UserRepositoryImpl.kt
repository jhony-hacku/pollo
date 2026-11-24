package com.sge.finca.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sge.finca.BuildConfig
import com.sge.finca.data.remote.FirestorePaths
import com.sge.finca.domain.model.FarmRole
import com.sge.finca.domain.model.UserProfile
import com.sge.finca.domain.repository.UserRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * El perfil vive en Firestore (users/{uid}). Las imágenes se suben a Cloudinary y la URL
 * resultante se guarda en el perfil y en la membresía de la finca.
 */
@Singleton
class UserRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val httpClient: OkHttpClient,
) : UserRepository {

    override val currentUserId: String?
        get() = auth.currentUser?.uid

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCurrentUser(): Flow<UserProfile?> =
        observeUid().flatMapLatest { uid ->
            if (uid == null) flowOf(null) else observeProfile(uid)
        }

    override fun observeCurrentFarmId(): Flow<String?> =
        observeCurrentUser().map { it?.farmId }.distinctUntilChanged()

    override suspend fun updateProfile(name: String, selectedPhotoUri: String?) {
        val user = checkNotNull(auth.currentUser) {
            "Inicia sesión para modificar tu perfil"
        }
        val cleanName = name.trim()
        require(cleanName.length in MIN_NAME_LENGTH..MAX_NAME_LENGTH) {
            "El nombre debe tener entre $MIN_NAME_LENGTH y $MAX_NAME_LENGTH caracteres"
        }

        val userReference = firestore.document(FirestorePaths.user(user.uid))
        val currentProfile = userReference.get().await()
        val farmId = currentProfile.getString("farmId")
        val previousPhotoUrl = currentProfile.getString("photoUrl").orEmpty()

        val uploadedPhotoUrl = selectedPhotoUri
            ?.takeIf { it.isNotBlank() }
            ?.let { uploadProfilePhoto(user.uid, Uri.parse(it)) }
            ?: previousPhotoUrl

        val batch = firestore.batch()
            .set(
                userReference,
                mapOf(
                    "name" to cleanName,
                    "email" to user.email.orEmpty(),
                    "photoUrl" to uploadedPhotoUrl,
                ),
                SetOptions.merge(),
            )

        if (!farmId.isNullOrBlank()) {
            batch.set(
                firestore.document(FirestorePaths.member(farmId, user.uid)),
                mapOf(
                    "displayName" to cleanName,
                    "email" to user.email.orEmpty(),
                    "photoUrl" to uploadedPhotoUrl,
                ),
                SetOptions.merge(),
            )
        }

        batch.commit().await()

        user.updateProfile(
            userProfileChangeRequest {
                displayName = cleanName
                if (uploadedPhotoUrl.isNotBlank()) {
                    photoUri = Uri.parse(uploadedPhotoUrl)
                }
            },
        ).await()
    }

    private suspend fun uploadProfilePhoto(uid: String, photoUri: Uri): String {
        val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
        val uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET

        check(cloudName.isNotBlank() && uploadPreset.isNotBlank()) {
            "Falta configurar Cloudinary en local.properties"
        }

        val detectedContentType = context.contentResolver.getType(photoUri).orEmpty()
        require(detectedContentType.startsWith("image/")) {
            "Selecciona un archivo de imagen"
        }

        val bytes = context.contentResolver.openInputStream(photoUri)?.use { stream ->
            stream.readBytes()
        } ?: throw IllegalArgumentException("No se pudo abrir la imagen seleccionada")

        require(bytes.size <= MAX_PHOTO_SIZE_BYTES) {
            "La imagen no puede pesar más de 5 MB"
        }

        val extension = when (detectedContentType.lowercase(Locale.ROOT)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }

        val fileBody = bytes.toRequestBody(detectedContentType.toMediaTypeOrNull())

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("upload_preset", uploadPreset)
            .addFormDataPart("folder", "sge/profilePhotos")
            .addFormDataPart(
                "file",
                "profile_${uid}_${System.currentTimeMillis()}.$extension",
                fileBody,
            )
            .build()

        val request = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
            .post(multipartBody)
            .build()

        return withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val cloudinaryMessage = runCatching {
                        JSONObject(responseText)
                            .optJSONObject("error")
                            ?.optString("message")
                    }.getOrNull()

                    throw IllegalStateException(
                        cloudinaryMessage
                            ?.takeIf { it.isNotBlank() }
                            ?: "Cloudinary rechazó la imagen",
                    )
                }

                val secureUrl = runCatching {
                    JSONObject(responseText).optString("secure_url")
                }.getOrNull().orEmpty()

                check(secureUrl.isNotBlank()) {
                    "Cloudinary no devolvió la URL de la imagen"
                }

                secureUrl
            }
        }
    }

    private fun observeUid(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener {
            trySend(it.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    private fun observeProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = firestore.document(FirestorePaths.user(uid))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("SgeUser", "No se pudo leer el perfil", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.takeIf { it.exists() }?.toUserProfile())
            }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.toUserProfile() = UserProfile(
        uid = id,
        name = getString("name") ?: "",
        email = getString("email") ?: "",
        photoUrl = getString("photoUrl") ?: "",
        farmId = getString("farmId"),
        role = getString("role")?.let { raw ->
            FarmRole.entries.firstOrNull { it.name == raw }
        },
        createdAt = getLong("createdAt") ?: 0L,
    )

    private companion object {
        const val MIN_NAME_LENGTH = 2
        const val MAX_NAME_LENGTH = 60
        const val MAX_PHOTO_SIZE_BYTES = 5 * 1024 * 1024
    }
}