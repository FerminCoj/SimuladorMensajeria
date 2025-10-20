package com.fermin.simuladormensajeria.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class UserProfile(
    val uid: String = "",
    val nombre: String = "",
    val email: String? = null
)

class FirebaseRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun currentUser(): FirebaseUser? = auth.currentUser

    suspend fun fetchUserProfileFromFirestore(uid: String): UserProfile? {
        val snap = db.collection("usuarios").document(uid).get().await()
        if (!snap.exists()) return null

        val nombre = (snap.get("nombre") as? String)?.trim().orEmpty()
        val email = (snap.get("email") as? String)?.trim()

        return UserProfile(
            uid = uid,
            nombre = nombre,
            email = email
        )
    }

    fun signOut() {
        auth.signOut()
    }
}
