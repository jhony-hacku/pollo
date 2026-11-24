package com.sge.finca.platform.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sge.finca.MainActivity
import com.sge.finca.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Alerta FCM de ganado fuera de los potreros. */
@Singleton
class AlertPushNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager: NotificationManagerCompat
        get() = NotificationManagerCompat.from(context)

    fun createChannel() {
        if (Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val channel =
            NotificationVisuals.configureChannel(
                channel = NotificationChannel(
                    CHANNEL_ID,
                    "Alertas del ganado",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description =
                        "Vacas que salen de los potreros de la finca"
                },
                vibrate = true,
            )

        context
            .getSystemService(
                NotificationManager::class.java,
            )
            .createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun show(
        alertId: String,
        title: String,
        body: String,
    ) {
        if (
            !NotificationVisuals.canShow(
                context,
                manager,
            )
        ) {
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
                NotificationDestinations.ALERT,
            )

            putExtra(
                NotificationDestinations.EXTRA_ALERT_ID,
                alertId,
            )
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId(alertId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
        )

        val timestamp = System.currentTimeMillis()

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID,
            )
                .setSmallIcon(R.drawable.ic_stat_alert)
                .setLargeIcon(
                    NotificationVisuals.badge(
                        symbol = "!",
                        backgroundColor =
                            NotificationVisuals.Red,
                    ),
                )
                .setContentTitle(title)
                .setContentText(body)
                .setSubText("Alerta del ganado")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .bigText(body)
                        .setSummaryText(
                            "Toca para revisar la alerta",
                        ),
                )
                .setColor(NotificationVisuals.Red)
                .setColorized(false)
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH,
                )
                .setCategory(
                    NotificationCompat.CATEGORY_ALARM,
                )
                .setVisibility(
                    NotificationCompat.VISIBILITY_PRIVATE,
                )
                .setWhen(timestamp)
                .setShowWhen(true)
                .setGroup(GROUP_KEY)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

        manager.notify(
            notificationId(alertId),
            notification,
        )
    }

    private fun notificationId(
        alertId: String,
    ): Int {
        return "alert:$alertId".hashCode()
    }

    companion object {
        const val CHANNEL_ID = "livestock_alerts"

        private const val GROUP_KEY =
            "sge_livestock_alerts"
    }
}