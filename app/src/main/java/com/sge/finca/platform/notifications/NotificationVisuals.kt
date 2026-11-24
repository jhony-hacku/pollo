package com.sge.finca.platform.notifications

import android.Manifest
import android.app.NotificationChannel
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Recursos visuales compartidos por todas las notificaciones de SGE. */
internal object NotificationVisuals {

    val Green: Int = Color.rgb(76, 175, 80)
    val Red: Int = Color.rgb(211, 47, 47)
    val Orange: Int = Color.rgb(255, 152, 0)

    fun configureChannel(
        channel: NotificationChannel,
        vibrate: Boolean,
    ): NotificationChannel = channel.apply {
        enableVibration(vibrate)
        setShowBadge(true)
        lockscreenVisibility =
            NotificationCompat.VISIBILITY_PRIVATE
    }

    fun canShow(
        context: Context,
        manager: NotificationManagerCompat,
    ): Boolean {
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

    fun badge(
        symbol: String,
        backgroundColor: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(
            BADGE_SIZE_PX,
            BADGE_SIZE_PX,
            Bitmap.Config.ARGB_8888,
        )

        val canvas = Canvas(bitmap)
        val center = BADGE_SIZE_PX / 2f

        val backgroundPaint = Paint(
            Paint.ANTI_ALIAS_FLAG,
        ).apply {
            color = backgroundColor
        }

        canvas.drawCircle(
            center,
            center,
            center,
            backgroundPaint,
        )

        val textPaint = Paint(
            Paint.ANTI_ALIAS_FLAG,
        ).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = if (symbol.length > 1) {
                BADGE_SIZE_PX * 0.35f
            } else {
                BADGE_SIZE_PX * 0.52f
            }
            typeface = Typeface.DEFAULT_BOLD
        }

        val verticalCenter =
            center -
                    (
                            textPaint.ascent() +
                                    textPaint.descent()
                            ) / 2f

        canvas.drawText(
            symbol.take(MAX_SYMBOL_LENGTH),
            center,
            verticalCenter,
            textPaint,
        )

        return bitmap
    }

    private const val BADGE_SIZE_PX = 144
    private const val MAX_SYMBOL_LENGTH = 2
}