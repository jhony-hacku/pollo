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
import com.sge.finca.domain.model.Reminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Muestra y retira las notificaciones programadas de recordatorios. */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager: NotificationManagerCompat
        get() = NotificationManagerCompat.from(context)

    fun createChannel() {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val channel =
            NotificationVisuals.configureChannel(
                channel = NotificationChannel(
                    CHANNEL_ID,
                    "Recordatorios SGE",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description =
                        "Avisos de vacunas, pagos y tareas de la finca"
                },
                vibrate = true,
            )

        context
            .getSystemService(
                NotificationManager::class.java,
            )
            .createNotificationChannel(channel)
    }

    fun canNotify(): Boolean {
        return NotificationVisuals.canShow(
            context,
            manager,
        )
    }

    @SuppressLint("MissingPermission")
    fun show(reminder: Reminder) {
        if (!canNotify()) {
            return
        }

        val description =
            reminder.description
                ?.takeIf { it.isNotBlank() }
                ?: "Recordatorio de la finca"

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID,
            )
                .setSmallIcon(
                    R.drawable.ic_stat_reminder,
                )
                .setLargeIcon(
                    NotificationVisuals.badge(
                        symbol = "R",
                        backgroundColor =
                            NotificationVisuals.Orange,
                    ),
                )
                .setContentTitle(reminder.title)
                .setContentText(description)
                .setSubText("Recordatorio de la finca")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(
                            reminder.title,
                        )
                        .bigText(description)
                        .setSummaryText(
                            "Toca para ver el recordatorio",
                        ),
                )
                .setColor(NotificationVisuals.Orange)
                .setColorized(false)
                .setCategory(
                    NotificationCompat.CATEGORY_REMINDER,
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH,
                )
                .setVisibility(
                    NotificationCompat.VISIBILITY_PRIVATE,
                )
                .setWhen(reminder.dueAt)
                .setShowWhen(true)
                .setGroup(GROUP_KEY)
                .setAutoCancel(true)
                .setContentIntent(
                    openRemindersIntent(reminder),
                )
                .build()

        manager.notify(
            notificationId(reminder.id),
            notification,
        )
    }

    fun dismiss(reminderId: String) {
        manager.cancel(
            notificationId(reminderId),
        )
    }

    private fun openRemindersIntent(
        reminder: Reminder,
    ): PendingIntent {
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
                NotificationDestinations.REMINDERS,
            )

            putExtra(
                NotificationDestinations.EXTRA_REMINDER_ID,
                reminder.id,
            )
        }

        return PendingIntent.getActivity(
            context,
            notificationId(reminder.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationId(
        reminderId: String,
    ): Int {
        return reminderId.hashCode()
    }

    companion object {
        const val CHANNEL_ID = "reminders"

        private const val GROUP_KEY =
            "sge_reminders"
    }
}

/** Destinos abiertos al tocar una notificación. */
object NotificationDestinations {
    const val EXTRA_DESTINATION =
        "com.sge.finca.extra.DESTINATION"

    const val EXTRA_REMINDER_ID =
        "com.sge.finca.extra.REMINDER_ID"

    const val EXTRA_TRANSACTION_ID =
        "com.sge.finca.extra.TRANSACTION_ID"

    const val EXTRA_ALERT_ID =
        "com.sge.finca.extra.ALERT_ID"

    const val EXTRA_CHAT_WORKER_UID =
        "com.sge.finca.extra.CHAT_WORKER_UID"

    const val REMINDERS = "reminders"
    const val TRANSACTION = "transaction"
    const val ALERT = "alert"
    const val CHAT = "chat"
    const val FARM_MAP = "farm_map"
}