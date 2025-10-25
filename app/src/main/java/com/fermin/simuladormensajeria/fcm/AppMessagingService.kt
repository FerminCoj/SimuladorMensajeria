package com.fermin.simuladormensajeria.fcm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fermin.simuladormensajeria.MainActivity
import com.fermin.simuladormensajeria.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AppMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Actualiza el token en Firestore si hay usuario autenticado
        FcmTokenManager.updateTokenInFirestore(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        try {
            if (message.data.isNotEmpty()) {
                // Mensaje tipo DATA (más flexible y usado para abrir chats)
                val data = message.data
                val senderId = data["senderId"] ?: ""
                val senderName = data["senderName"] ?: "Nuevo mensaje"
                val text = data["message"] ?: "Tienes un nuevo mensaje"

                Log.d("FCM", "Mensaje recibido de: $senderName ($senderId) -> $text")

                sendNotification(
                    title = senderName,
                    body = text,
                    senderId = senderId,
                    senderName = senderName
                )
            } else {
                // Mensaje tipo NOTIFICATION (sin payload adicional)
                val title = message.notification?.title ?: "Nuevo mensaje"
                val body = message.notification?.body ?: "Tienes un nuevo mensaje"
                sendNotification(title, body, null, null)
            }
        } catch (e: Exception) {
            Log.e("FCM", "Error al procesar el mensaje FCM", e)
        }
    }

    private fun sendNotification(
        title: String,
        body: String,
        senderId: String?,
        senderName: String?
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!senderId.isNullOrBlank() && !senderName.isNullOrBlank()) {
                putExtra("chatUid", senderId)
                putExtra("chatNombre", senderName)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            senderId?.hashCode() ?: (System.currentTimeMillis() % 10000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "chat_messages"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crear canal para Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Mensajes del chat",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Verificar permiso antes de mostrar notificación (Android 13+)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("FCM", "Permiso POST_NOTIFICATIONS no concedido; no se mostrará la notificación.")
            return
        }

        // Mostrar notificación
        NotificationManagerCompat.from(this).notify(
            (System.currentTimeMillis() % 100000).toInt(),
            notificationBuilder.build()
        )
    }
}

