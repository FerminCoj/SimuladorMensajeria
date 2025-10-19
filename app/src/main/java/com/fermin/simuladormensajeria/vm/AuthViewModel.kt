package com.fermin.simuladormensajeria.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.fermin.simuladormensajeria.data.UserRepository
import com.fermin.simuladormensajeria.model.AppUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel que coordina FirebaseAuth con Firestore (usuarios).
 */
sealed class AuthUiState {
    data object Loading : AuthUiState()
    data object Unauthenticated : AuthUiState()
    data class Authenticated(val user: AppUser) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val repo: UserRepository = UserRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val state: StateFlow<AuthUiState> = _state

    init {
        refreshUser()
    }

    /** Verifica si hay sesión activa y sincroniza con Firestore */
    fun refreshUser() {
        viewModelScope.launch {
            val fbUser = auth.currentUser
            if (fbUser == null) {
                _state.value = AuthUiState.Unauthenticated
                return@launch
            }

            try {
                val appUser = repo.ensureUserDocument(
                    uid = fbUser.uid,
                    email = fbUser.email,
                    phone = fbUser.phoneNumber,
                    displayName = fbUser.displayName,
                    photoUrl = fbUser.photoUrl?.toString()
                )
                _state.value = AuthUiState.Authenticated(appUser)
            } catch (e: Exception) {
                _state.value = AuthUiState.Error("Error al sincronizar perfil: ${e.message}")
            }
        }
    }

    /** Actualiza el nombre del usuario */
fun updateDisplayName(name: String) {
    val fbUser = auth.currentUser ?: return
    viewModelScope.launch {
        try {
            val request = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build()
            fbUser.updateProfile(request).addOnCompleteListener {
                // ignoramos el resultado
            }
            repo.updateDisplayName(fbUser.uid, name)
            refreshUser()
        } catch (e: Exception) {
            _state.value = AuthUiState.Error("No se pudo actualizar el nombre: ${e.message}")
        }
    }
}

    /** Cierra la sesión actual */
    fun signOut() {
        auth.signOut()
        _state.value = AuthUiState.Unauthenticated
    }
}
