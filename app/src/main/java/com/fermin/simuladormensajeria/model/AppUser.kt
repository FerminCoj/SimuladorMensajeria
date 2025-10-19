package com.fermin.simuladormensajeria.model

/**
 * Modelo de usuario para Firestore.
 * Los valores por defecto permiten:
 * - constructor sin argumentos (requisito de Firestore)
 * - mapeo sencillo a/desde documentos
 */
data class AppUser(
    val uid: String = "",
    val phone: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val about: String? = "Disponible",
    // Usa milisegundos epoch; lo actualizaremos desde el ViewModel cuando toque.
    val lastSeen: Long? = null
)
