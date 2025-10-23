package com.fermin.simuladormensajeria.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fermin.simuladormensajeria.model.AppUser
import com.fermin.simuladormensajeria.vm.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactosScreen(
    authVM: AuthViewModel,
    onChatClick: (String, String) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    var usuarios by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var listener: ListenerRegistration? = null

    // 🔹 Escucha en tiempo real los usuarios
    LaunchedEffect(Unit) {
        listener = db.collection("usuarios")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    usuarios = snapshot.toObjects(AppUser::class.java)
                        .filter { it.uid != currentUid } // excluir el usuario actual
                }
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            listener?.remove()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contactos") },
                actions = {
                    TextButton(onClick = { authVM.signOut() }) {
                        Text("Cerrar sesión", color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (usuarios.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text("No hay otros usuarios registrados aún.")
                }
            } else {
                usuarios.forEach { usuario ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable {
                                onChatClick(
                                    usuario.uid,
                                    usuario.displayName ?: usuario.email ?: "Usuario"
                                )
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = usuario.displayName ?: "Sin nombre",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            usuario.email?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
