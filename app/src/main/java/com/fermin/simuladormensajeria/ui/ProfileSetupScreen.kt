
package com.fermin.simuladormensajeria.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.fermin.simuladormensajeria.vm.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun ProfileSetupScreen(
    authVM: AuthViewModel,
    onDone: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val user = auth.currentUser
    val context = LocalContext.current // ✅ Mover aquí, fuera del scope.launch

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Configura tu perfil",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("Tu nombre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (name.isBlank()) {
                            error = "Por favor, ingresa un nombre"
                            return@Button
                        }

                        if (user != null) {
                            isSaving = true
                            scope.launch {
                                var huboError = false

                                try {
                                    // 1️⃣ Actualiza el nombre en Firebase Auth
                                    authVM.updateDisplayName(name)

                                    // 2️⃣ Guarda datos en Firestore
                                    val datos = mapOf(
                                        "uid" to user.uid,
                                        "correo" to user.email,
                                        "nombre" to name,
                                        "displayName" to name,
                                        "fechaCreacion" to System.currentTimeMillis(),
                                        "about" to "Disponible"
                                    )

                                    db.collection("usuarios")
                                        .document(user.uid)
                                        .set(datos, SetOptions.merge())
                                        .await()

                                } catch (e: Exception) {
                                    huboError = true
                                    error = "Error al guardar: ${e.message}"
                                }

                                // 3️⃣ Refresca y finaliza (fuera del try/catch)
                                isSaving = false
                                if (!huboError) {
                                    authVM.refreshUser(context)
                                    onDone()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}
