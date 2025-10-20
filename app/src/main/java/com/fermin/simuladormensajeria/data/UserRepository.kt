package com.fermin.simuladormensajeria.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.fermin.simuladormensajeria.model.AppUser
import kotlinx.coroutines.tasks.await

/**
 * Repositorio para manejar operaciones CRUD sobre la colección "usuarios" en Firestore.
 */
class UserRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    // Referencia a la colección correcta
    private fun usersCollection() = db.collection("usuarios")

    /** Inserta o actualiza un usuario */
    suspend fun upsertUser(user: AppUser) {
        require(user.uid.isNotBlank())
        usersCollection().document(user.uid)
            .set(user, SetOptions.merge())
            .await()
    }

    /** Obtiene un usuario por UID */
    suspend fun getUser(uid: String): AppUser? {
        val snapshot = usersCollection().document(uid).get().await()
        return snapshot.toObject(AppUser::class.java)
    }

    /**
     * Crea el documento si no existe o lo actualiza si ya está.
     * Se asegura de que el campo "nombre" quede sincronizado con displayName.
     */
    suspend fun ensureUserDocument(
        uid: String,
        email: String?,
        phone: String?,
        displayName: String?,
        photoUrl: String?
    ): AppUser {
        val existing = getUser(uid)
        val merged = existing?.copy(
            email = existing.email ?: email,
            phone = existing.phone ?: phone,
            displayName = existing.displayName ?: displayName,
            photoUrl = existing.photoUrl ?: photoUrl
        ) ?: AppUser(
            uid = uid,
            email = email,
            phone = phone,
            displayName = displayName,
            photoUrl = photoUrl
        )

        upsertUser(merged)

        // Actualiza también el campo "nombre" para consistencia
        usersCollection().document(uid)
            .set(mapOf("nombre" to (displayName ?: "")), SetOptions.merge())
            .await()

        return merged
    }

    /** Actualiza el nombre del usuario */
    suspend fun updateDisplayName(uid: String, newName: String) {
        usersCollection().document(uid)
            .set(
                mapOf(
                    "displayName" to newName,
                    "nombre" to newName // sincroniza ambos campos
                ),
                SetOptions.merge()
            )
            .await()
    }

    /** Actualiza el campo "about" (biografía o descripción) */
    suspend fun updateAbout(uid: String, about: String) {
        usersCollection().document(uid)
            .set(mapOf("about" to about), SetOptions.merge())
            .await()
    }

    /** Actualiza el campo "lastSeen" (última conexión) */
    suspend fun updateLastSeen(uid: String, timestamp: Long) {
        usersCollection().document(uid)
            .set(mapOf("lastSeen" to timestamp), SetOptions.merge())
            .await()
    }
}

