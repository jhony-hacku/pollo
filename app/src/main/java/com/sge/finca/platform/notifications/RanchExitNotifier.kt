package com.sge.finca.platform.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sge.finca.MainActivity
import com.sge.finca.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aviso local de vaca fuera del rancho: lo genera el propio celular con las posiciones de los
 * collares, sin servidor. Usa el canal "Alertas del ganado" de [AlertPushNotifier] (que crea
 * FincaApplication) y al tocarlo abre el mapa de la finca.
 */
@Singleton
class RanchExitNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager get() = NotificationManagerCompat.from(context)

    fun showExit(animalId: String, animalName: String) {
        show(
            animalId = animalId,
            title = "¡$animalName salió del rancho!",
            body = "$animalName está fuera del límite de la finca. Toca para verla en el mapa.",
            alarm = true,
        )
    }

    fun showReturn(animalId: String, animalName: String) {
        show(
            animalId = animalId,
            title = "$animalName volvió al rancho",
            body = "$animalName está otra vez dentro del límite de la finca.",
            alarm = false,
        )
    }

    @SuppressLint("MissingPermission") // se verifica abajo
    private fun show(animalId: String, title: String, body: String, alarm: Boolean) {
        val permissionOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!permissionOk || !manager.areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(NotificationDestinations.EXTRA_DESTINATION, NotificationDestinations.FARM_MAP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId(animalId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, AlertPushNotifier.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (alarm) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Mismo id para la salida y el regreso de la misma vaca: el regreso reemplaza la alerta.
        manager.notify(notificationId(animalId), notification)
    }

    private fun notificationId(animalId: String) = "ranch:$animalId".hashCode()
}
