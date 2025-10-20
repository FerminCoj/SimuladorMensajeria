package com.fermin.simuladormensajeria.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fermin.simuladormensajeria.vm.AuthViewModel
import com.fermin.simuladormensajeria.vm.AuthUiState

@Composable
fun BienvenidaScreen(
    authVM: AuthViewModel,
    onLogout: () -> Unit
) {
    val state by authVM.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is AuthUiState.Loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Cargando usuario...")
                }
            }

            is AuthUiState.Error -> {
                val msg = (state as AuthUiState.Error).message
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error: $msg", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onLogout) {
                        Text("Volver al inicio")
                    }
                }
            }

            is AuthUiState.Unauthenticated -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sesión cerrada o no iniciada.")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onLogout) {
                        Text("Iniciar sesión")
                    }
                }
            }

        is AuthUiState.Authenticated -> {
    val usuario = (state as AuthUiState.Authenticated).user
    val nombre = usuario.displayName?.takeIf { it.isNotBlank() } ?: usuario.email ?: "Usuario"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "¡Hola, $nombre!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Bienvenido al simulador de mensajería.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                authVM.signOut()
                onLogout()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Cerrar sesión")
        }
    }
}

        }
    }
}
