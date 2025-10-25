package com.fermin.simuladormensajeria.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object AppNotificationUtils {
    const val CHANNEL_ID_MESSAGES = "messages_channel"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Canal principal para mensajes
            val channel = NotificationChannel(
                CHANNEL_ID_MESSAGES,
                "Mensajes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de nuevos mensajes o imágenes"
            }

            manager.createNotificationChannel(channel)
        }
    }
}
