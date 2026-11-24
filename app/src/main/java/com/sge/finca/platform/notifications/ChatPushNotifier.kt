package com.sge.finca.platform.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.sge.finca.MainActivity
import com.sge.finca.R
import com.sge.finca.data.injection.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/** Muestra los mensajes del chat como conversaciones nativas de Android. */
@Singleton
class ChatPushNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val manager: NotificationManagerCompat
        get() = NotificationManagerCompat.from(context)

    private val messageHistory =
        ConcurrentHashMap<String, ArrayDeque<ChatNotificationMessage>>()

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mensajes del chat",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Mensajes entre el propietario y los trabajadores"
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }

        context
            .getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun show(
        workerUid: String,
        title: String,
        body: String,
        senderName: String = "",
        photoUrl: String = "",
    ) {
        if (!canShowNotifications()) {
            return
        }

        val displayName = senderName.ifBlank {
            title
                .removePrefix("Mensaje de ")
                .ifBlank { "Nuevo mensaje" }
        }

        val displayBody = body.ifBlank {
            "Nuevo mensaje"
        }

        val timestamp = System.currentTimeMillis()

        addToHistory(
            workerUid = workerUid,
            message = ChatNotificationMessage(
                text = displayBody,
                timestamp = timestamp,
            ),
        )

        val fallbackAvatar = createAvatar(
            source = null,
            displayName = displayName,
        )

        showNotification(
            workerUid = workerUid,
            senderName = displayName,
            senderAvatar = fallbackAvatar,
            isAvatarRefresh = false,
        )

        if (photoUrl.isNotBlank()) {
            applicationScope.launch(Dispatchers.IO) {
                val downloadedPhoto =
                    downloadPhoto(photoUrl) ?: return@launch

                val avatar = createAvatar(
                    source = downloadedPhoto,
                    displayName = displayName,
                )

                if (canShowNotifications()) {
                    showNotification(
                        workerUid = workerUid,
                        senderName = displayName,
                        senderAvatar = avatar,
                        isAvatarRefresh = true,
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(
        workerUid: String,
        senderName: String,
        senderAvatar: Bitmap,
        isAvatarRefresh: Boolean,
    ) {
        if (!canShowNotifications()) {
            return
        }

        val messages = historyFor(workerUid)

        if (messages.isEmpty()) {
            return
        }

        val intent = Intent(
            context,
            MainActivity::class.java,
        ).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

            putExtra(
                NotificationDestinations.EXTRA_DESTINATION,
                NotificationDestinations.CHAT,
            )

            putExtra(
                NotificationDestinations.EXTRA_CHAT_WORKER_UID,
                workerUid,
            )
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId(workerUid),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
        )

        val sender = Person.Builder()
            .setName(senderName)
            .setKey("chat_sender:$workerUid")
            .setIcon(
                IconCompat.createWithBitmap(senderAvatar),
            )
            .build()

        val currentUser = Person.Builder()
            .setName("Tú")
            .setKey("sge_current_user")
            .build()

        val messagingStyle =
            NotificationCompat.MessagingStyle(currentUser)
                .setGroupConversation(false)

        messages.forEach { message ->
            messagingStyle.addMessage(
                NotificationCompat.MessagingStyle.Message(
                    message.text,
                    message.timestamp,
                    sender,
                ),
            )
        }

        val latestMessage = messages.last()

        val contentText = if (messages.size == 1) {
            latestMessage.text
        } else {
            "${messages.size} mensajes nuevos · ${latestMessage.text}"
        }

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID,
            )
                .setSmallIcon(R.drawable.ic_stat_chat)
                .setLargeIcon(senderAvatar)
                .setContentTitle(senderName)
                .setContentText(contentText)
                .setSubText("Chat de la finca")
                .setStyle(messagingStyle)
                .setColor(CHAT_COLOR)
                .setColorized(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setOnlyAlertOnce(isAvatarRefresh)
                .setWhen(latestMessage.timestamp)
                .setShowWhen(true)
                .setNumber(messages.size)
                .setContentIntent(pendingIntent)
                .addPerson(sender)
                .build()

        manager.notify(
            notificationId(workerUid),
            notification,
        )
    }

    private fun addToHistory(
        workerUid: String,
        message: ChatNotificationMessage,
    ) {
        if (!isNotificationActive(workerUid)) {
            messageHistory.remove(workerUid)
        }

        val history = messageHistory.getOrPut(workerUid) {
            ArrayDeque()
        }

        synchronized(history) {
            history.addLast(message)

            while (history.size > MAX_VISIBLE_MESSAGES) {
                history.removeFirst()
            }
        }
    }

    private fun historyFor(
        workerUid: String,
    ): List<ChatNotificationMessage> {
        val history =
            messageHistory[workerUid] ?: return emptyList()

        return synchronized(history) {
            history.toList()
        }
    }

    private fun downloadPhoto(
        photoUrl: String,
    ): Bitmap? {
        return try {
            val request = Request.Builder()
                .url(photoUrl)
                .get()
                .build()

            httpClient
                .newCall(request)
                .execute()
                .use { response ->
                    if (!response.isSuccessful) {
                        return null
                    }

                    response.body
                        ?.byteStream()
                        ?.use(BitmapFactory::decodeStream)
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun createAvatar(
        source: Bitmap?,
        displayName: String,
    ): Bitmap {
        val output = Bitmap.createBitmap(
            AVATAR_SIZE_PX,
            AVATAR_SIZE_PX,
            Bitmap.Config.ARGB_8888,
        )

        val canvas = Canvas(output)
        val center = AVATAR_SIZE_PX / 2f
        val radius = center

        if (
            source != null &&
            source.width > 0 &&
            source.height > 0
        ) {
            val shader = BitmapShader(
                source,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP,
            )

            val scale = maxOf(
                AVATAR_SIZE_PX.toFloat() / source.width,
                AVATAR_SIZE_PX.toFloat() / source.height,
            )

            val matrix = Matrix().apply {
                setScale(
                    scale,
                    scale,
                )

                postTranslate(
                    (AVATAR_SIZE_PX - source.width * scale) / 2f,
                    (AVATAR_SIZE_PX - source.height * scale) / 2f,
                )
            }

            shader.setLocalMatrix(matrix)

            val photoPaint = Paint(
                Paint.ANTI_ALIAS_FLAG,
            ).apply {
                this.shader = shader
            }

            canvas.drawCircle(
                center,
                center,
                radius,
                photoPaint,
            )

            return output
        }

        val backgroundPaint = Paint(
            Paint.ANTI_ALIAS_FLAG,
        ).apply {
            color = CHAT_COLOR
        }

        canvas.drawCircle(
            center,
            center,
            radius,
            backgroundPaint,
        )

        val initial = displayName
            .trim()
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            ?: "?"

        val textPaint = Paint(
            Paint.ANTI_ALIAS_FLAG,
        ).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = AVATAR_SIZE_PX * 0.48f
            typeface =
                android.graphics.Typeface.DEFAULT_BOLD
        }

        val verticalCenter =
            center -
                    (
                            textPaint.ascent() +
                                    textPaint.descent()
                            ) / 2f

        canvas.drawText(
            initial,
            center,
            verticalCenter,
            textPaint,
        )

        return output
    }

    private fun canShowNotifications(): Boolean {
        val permissionGranted =
            Build.VERSION.SDK_INT <
                    Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED

        return permissionGranted &&
                manager.areNotificationsEnabled()
    }

    private fun notificationId(
        workerUid: String,
    ): Int {
        return "chat:$workerUid".hashCode()
    }

    private fun isNotificationActive(
        workerUid: String,
    ): Boolean {
        val notificationManager =
            context.getSystemService(
                NotificationManager::class.java,
            )

        val expectedId = notificationId(workerUid)

        return notificationManager
            .activeNotifications
            .any { notification ->
                notification.id == expectedId
            }
    }

    private data class ChatNotificationMessage(
        val text: String,
        val timestamp: Long,
    )

    companion object {
        const val CHANNEL_ID = "messages"

        private const val MAX_VISIBLE_MESSAGES = 6
        private const val AVATAR_SIZE_PX = 144

        private val CHAT_COLOR =
            Color.rgb(
                76,
                175,
                80,
            )
    }
}