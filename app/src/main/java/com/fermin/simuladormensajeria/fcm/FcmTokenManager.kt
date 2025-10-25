package com.fermin.simuladormensajeria.fcm

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

object FcmTokenManager {
    private const val PREF = "fcm_prefs"
    private const val KEY_TOKEN = "fcm_token"

    // Guarda el token localmente (SharedPreferences)
    fun storeTokenLocally(context: Context, token: String) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp.edit { putString(KEY_TOKEN, token) }
        Log.d("FCM", "Token guardado localmente: $token")
    }

    // Obtiene el token local actual (si ya fue guardado)
    fun currentLocalToken(context: Context): String? {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return sp.getString(KEY_TOKEN, null)
    }

    // Pide un nuevo token a Firebase y lo guarda
    fun fetchAndStore(context: Context, onReady: ((String?) -> Unit)? = null) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (token != null) {
                    storeTokenLocally(context, token)
                    Log.d("FCM", "Token obtenido de Firebase: $token")
                } else {
                    Log.w("FCM", "Token FCM nulo")
                }
                onReady?.invoke(token)
            }
            .addOnFailureListener { e ->
                Log.w("FCM", "No se pudo obtener el token FCM", e)
                onReady?.invoke(null)
            }
    }

    // 🔹 Sincroniza token local con el usuario actual
    fun trySyncWithUser(context: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val token = currentLocalToken(context) ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("usuarios").document(uid)
            .update("fcmTokens", FieldValue.arrayUnion(token))
            .addOnSuccessListener {
                Log.d("FCM", "Token sincronizado correctamente con Firestore")
            }
            .addOnFailureListener { err ->
                Log.w("FCM", "Fallo al sincronizar token con Firestore", err)
            }
    }

    // 🔹 Actualiza token en Firestore (llamado desde AppMessagingService)
    fun updateTokenInFirestore(context: Context, token: String) {
        storeTokenLocally(context, token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("usuarios").document(uid)
            .update("fcmTokens", FieldValue.arrayUnion(token))
            .addOnSuccessListener {
                Log.d("FCM", "✅ Token actualizado correctamente en Firestore")
            }
            .addOnFailureListener { e ->
                Log.w("FCM", "❌ Error al actualizar token en Firestore", e)
            }
    }
}


