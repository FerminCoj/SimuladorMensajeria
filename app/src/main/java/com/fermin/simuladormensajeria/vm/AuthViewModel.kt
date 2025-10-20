package com.fermin.simuladormensajeria.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.fermin.simuladormensajeria.data.UserRepository
import com.fermin.simuladormensajeria.model.AppUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// =====================================
// ESTADOS DE AUTENTICACIÓN
// =====================================
sealed class AuthUiState {
    data object Loading : AuthUiState()
    data object Unauthenticated : AuthUiState()
    data class Authenticated(val user: AppUser) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

// =====================================
// VIEW MODEL PRINCIPAL DE AUTENTICACIÓN
// =====================================
class AuthViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val repo: UserRepository = UserRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val state: StateFlow<AuthUiState> = _state

    private val db = FirebaseFirestore.getInstance()
    private var userListener: ListenerRegistration? = null

    init {
        // 🔹 Esperar un poco antes de intentar conectarse a Firestore
        viewModelScope.launch {
            delay(1000)
            refreshUser()
        }
    }

    // =====================================
    // SINCRONIZA USUARIO ENTRE AUTH Y FIRESTORE
    // =====================================
    fun refreshUser(retryCount: Int = 0) {
        viewModelScope.launch {
            val fbUser = auth.currentUser
            if (fbUser == null) {
                _state.value = AuthUiState.Unauthenticated
                userListener?.remove()
                userListener = null
                return@launch
            }

            try {
                // 🔹 Asegura que el documento exista en Firestore
                repo.ensureUserDocument(
                    uid = fbUser.uid,
                    email = fbUser.email,
                    phone = fbUser.phoneNumber,
                    displayName = fbUser.displayName,
                    photoUrl = fbUser.photoUrl?.toString()
                )

                val docRef = db.collection("usuarios").document(fbUser.uid)
                val snap = docRef.get().await()
                val nombreFirestore =
                    snap.getString("nombre") ?: snap.getString("displayName")
                val nombreFinal =
                    nombreFirestore ?: fbUser.displayName ?: fbUser.email ?: "Usuario"

                val usuario = AppUser(
                    uid = fbUser.uid,
                    email = fbUser.email,
                    phone = fbUser.phoneNumber,
                    displayName = nombreFinal,
                    photoUrl = fbUser.photoUrl?.toString()
                )

                // 🔹 Estado autenticado correctamente
                _state.value = AuthUiState.Authenticated(usuario)

                // 🔹 Escucha en tiempo real cambios del perfil
                userListener?.remove()
                userListener = docRef.addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null && snapshot.exists()) {
                        val nuevoNombre =
                            snapshot.getString("nombre") ?: snapshot.getString("displayName")
                        val nombreActualizado =
                            nuevoNombre ?: fbUser.displayName ?: fbUser.email
                        val actualizado = usuario.copy(displayName = nombreActualizado)
                        _state.value = AuthUiState.Authenticated(actualizado)
                    }
                }

            } catch (e: Exception) {
                val msg = e.message ?: "Error desconocido"

                // 🔹 Reintenta hasta 3 veces en caso de error de conexión
                if (msg.contains("offline", ignoreCase = true) && retryCount < 3) {
                    _state.value =
                        AuthUiState.Error("Firestore sin conexión... reintentando (${retryCount + 1}/3)")
                    delay(1500)
                    refreshUser(retryCount + 1)
                } else {
                    _state.value = AuthUiState.Error(
                        when {
                            msg.contains("offline", ignoreCase = true) ->
                                "Sin conexión a Firestore. Verifica tu conexión e intenta nuevamente."
                            else -> "Error al sincronizar perfil: $msg"
                        }
                    )
                }
            }
        }
    }

    // =====================================
    // ACTUALIZA EL NOMBRE DE PERFIL
    // =====================================
    fun updateDisplayName(name: String) {
        val fbUser = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                val request = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()

                // 🔹 Actualiza nombre en Auth
                fbUser.updateProfile(request).await()

                // 🔹 Actualiza Firestore
                repo.updateDisplayName(fbUser.uid, name)
                db.collection("usuarios").document(fbUser.uid)
                    .update(mapOf("nombre" to name, "displayName" to name))
                    .await()

                // 🔹 Refresca usuario actualizado
                refreshUser()

            } catch (e: Exception) {
                _state.value = AuthUiState.Error("No se pudo actualizar el nombre: ${e.message}")
            }
        }
    }

    // =====================================
    // OBTENER NOMBRE ACTUAL DEL USUARIO
    // =====================================
    fun getCurrentUserName(): String {
        val currentState = _state.value
        return if (currentState is AuthUiState.Authenticated) {
            currentState.user.displayName?.takeIf { it.isNotBlank() }
                ?: currentState.user.email.orEmpty()
        } else ""
    }

    // =====================================
    // CERRAR SESIÓN
    // =====================================
    fun signOut() {
        userListener?.remove()
        userListener = null
        auth.signOut()
        _state.value = AuthUiState.Unauthenticated
    }
}
