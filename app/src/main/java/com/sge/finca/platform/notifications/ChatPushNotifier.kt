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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/** Notificación de un mensaje nuevo entre el propietario y un trabajador. */
@Singleton
class ChatPushNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val manager: NotificationManagerCompat
        get() = NotificationManagerCompat.from(context)

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mensajes",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Mensajes entre el propietario y los trabajadores"
            enableVibration(true)
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
        if (!canShowNotifications()) return

        val displayName = senderName.ifBlank {
            title
                .removePrefix("Mensaje de ")
                .ifBlank { "Nuevo mensaje" }
        }

        showNotification(
            workerUid = workerUid,
            title = title,
            body = body,
            senderName = displayName,
            senderPhoto = null,
        )

        if (photoUrl.isNotBlank()) {
            applicationScope.launch(Dispatchers.IO) {
                val bitmap = downloadPhoto(photoUrl) ?: return@launch

                if (canShowNotifications()) {
                    showNotification(
                        workerUid = workerUid,
                        title = title,
                        body = body,
                        senderName = displayName,
                        senderPhoto = bitmap,
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(
        workerUid: String,
        title: String,
        body: String,
        senderName: String,
        senderPhoto: Bitmap?,
    ) {
        if (!canShowNotifications()) return

        val intent = Intent(context, MainActivity::class.java).apply {
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
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val personBuilder = Person.Builder()
            .setName(senderName)
            .setKey(senderName)

        if (senderPhoto != null) {
            personBuilder.setIcon(
                IconCompat.createWithBitmap(senderPhoto),
            )
        }

        val sender = personBuilder.build()

        val messagingStyle = NotificationCompat.MessagingStyle(sender)
            .addMessage(
                NotificationCompat.MessagingStyle.Message(
                    body,
                    System.currentTimeMillis(),
                    sender,
                ),
            )
            .setGroupConversation(false)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(senderPhoto != null)
            .setContentIntent(pendingIntent)
            .apply {
                if (senderPhoto != null) {
                    setLargeIcon(senderPhoto)
                }
            }
            .build()

        manager.notify(
            notificationId(workerUid),
            notification,
        )
    }

    private fun downloadPhoto(photoUrl: String): Bitmap? {
        return try {
            val request = Request.Builder()
                .url(photoUrl)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
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

    private fun canShowNotifications(): Boolean {
        val permissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED

        return permissionGranted && manager.areNotificationsEnabled()
    }

    private fun notificationId(workerUid: String): Int {
        return "chat:$workerUid".hashCode()
    }

    companion object {
        const val CHANNEL_ID = "messages"
    }
}