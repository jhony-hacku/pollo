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

/** Notificación FCM de una transacción registrada por otro miembro. */
@Singleton
class TransactionPushNotifier @Inject constructor(
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
                    "Movimientos de la finca",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description =
                        "Ingresos y egresos registrados por otros miembros"
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
        transactionId: String,
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
                NotificationDestinations.TRANSACTION,
            )

            putExtra(
                NotificationDestinations.EXTRA_TRANSACTION_ID,
                transactionId,
            )
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId(transactionId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
        )

        val isExpense =
            title.contains(
                "egreso",
                ignoreCase = true,
            ) ||
                    body.contains(
                        "egreso",
                        ignoreCase = true,
                    ) ||
                    title.contains(
                        "gasto",
                        ignoreCase = true,
                    ) ||
                    body.contains(
                        "gasto",
                        ignoreCase = true,
                    )

        val color = if (isExpense) {
            NotificationVisuals.Red
        } else {
            NotificationVisuals.Green
        }

        val symbol = if (isExpense) {
            "-"
        } else {
            "+"
        }

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID,
            )
                .setSmallIcon(
                    R.drawable.ic_stat_transaction,
                )
                .setLargeIcon(
                    NotificationVisuals.badge(
                        symbol = symbol,
                        backgroundColor = color,
                    ),
                )
                .setContentTitle(title)
                .setContentText(body)
                .setSubText("Movimiento de la finca")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .bigText(body)
                        .setSummaryText(
                            "Toca para ver la transacción",
                        ),
                )
                .setColor(color)
                .setColorized(false)
                .setCategory(
                    NotificationCompat.CATEGORY_STATUS,
                )
                .setPriority(
                    NotificationCompat.PRIORITY_DEFAULT,
                )
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
            notificationId(transactionId),
            notification,
        )
    }

    private fun notificationId(
        transactionId: String,
    ): Int {
        return "transaction:$transactionId".hashCode()
    }

    companion object {
        const val CHANNEL_ID = "movements"

        private const val GROUP_KEY =
            "sge_movements"
    }
}