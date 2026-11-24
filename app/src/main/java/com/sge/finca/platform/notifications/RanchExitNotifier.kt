package com.sge.finca.platform.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sge.finca.MainActivity
import com.sge.finca.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Aviso local cuando un animal sale del rancho o vuelve a entrar. */
@Singleton
class RanchExitNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager: NotificationManagerCompat
        get() = NotificationManagerCompat.from(context)

    fun showExit(
        animalId: String,
        animalName: String,
    ) {
        show(
            animalId = animalId,
            title = "¡$animalName salió del rancho!",
            body = "$animalName está fuera del límite " +
                    "de la finca. Toca para verla en el mapa.",
            isAlarm = true,
        )
    }

    fun showReturn(
        animalId: String,
        animalName: String,
    ) {
        show(
            animalId = animalId,
            title = "$animalName volvió al rancho",
            body = "$animalName está otra vez dentro " +
                    "del límite de la finca.",
            isAlarm = false,
        )
    }

    @SuppressLint("MissingPermission")
    private fun show(
        animalId: String,
        title: String,
        body: String,
        isAlarm: Boolean,
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
                NotificationDestinations.FARM_MAP,
            )
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId(animalId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
        )

        val color = if (isAlarm) {
            NotificationVisuals.Red
        } else {
            NotificationVisuals.Green
        }

        val symbol = if (isAlarm) {
            "!"
        } else {
            "OK"
        }

        val summary = if (isAlarm) {
            "Toca para abrir el mapa"
        } else {
            "Animal nuevamente seguro"
        }

        val priority = if (isAlarm) {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }

        val category = if (isAlarm) {
            NotificationCompat.CATEGORY_ALARM
        } else {
            NotificationCompat.CATEGORY_STATUS
        }

        val notification =
            NotificationCompat.Builder(
                context,
                AlertPushNotifier.CHANNEL_ID,
            )
                .setSmallIcon(R.drawable.ic_stat_alert)
                .setLargeIcon(
                    NotificationVisuals.badge(
                        symbol = symbol,
                        backgroundColor = color,
                    ),
                )
                .setContentTitle(title)
                .setContentText(body)
                .setSubText("Estado del ganado")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .bigText(body)
                        .setSummaryText(summary),
                )
                .setColor(color)
                .setColorized(false)
                .setPriority(priority)
                .setCategory(category)
                .setVisibility(
                    NotificationCompat.VISIBILITY_PRIVATE,
                )
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setGroup(GROUP_KEY)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

        manager.notify(
            notificationId(animalId),
            notification,
        )
    }

    private fun notificationId(
        animalId: String,
    ): Int {
        return "ranch:$animalId".hashCode()
    }

    private companion object {
        const val GROUP_KEY =
            "sge_livestock_alerts"
    }
}