package com.fermin.simuladormensajeria

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fermin.simuladormensajeria.ui.ChatScreen
import com.fermin.simuladormensajeria.ui.ContactosScreen
import com.fermin.simuladormensajeria.ui.ProfileSetupScreen
import com.fermin.simuladormensajeria.ui.theme.SimuladorMensajeriaTheme
import com.fermin.simuladormensajeria.vm.AuthUiState
import com.fermin.simuladormensajeria.vm.AuthViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()

        setContent {
            SimuladorMensajeriaTheme {
                val authVM = remember { AuthViewModel() }
                val state by authVM.state.collectAsState()

                // Guarda el chat seleccionado: Pair(uidReceptor, nombreReceptor)
                var selectedChat by remember { mutableStateOf<Pair<String, String>?>(null) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    when (state) {

                        is AuthUiState.Loading -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is AuthUiState.Unauthenticated -> {
                            LoginScreen(
                                auth = auth,
                                onLoginSuccess = { authVM.refreshUser() },
                                onRegisterSuccess = { authVM.refreshUser() }
                            )
                        }

                        is AuthUiState.Authenticated -> {
                            val usuario = (state as AuthUiState.Authenticated).user

                            // Si falta nombre, mandar a configurar perfil
                            if (usuario.displayName.isNullOrBlank() || usuario.displayName == "null") {
                                ProfileSetupScreen(
                                    authVM = authVM,
                                    onDone = { authVM.refreshUser() }
                                )
                            } else {
                                // Si hay chat seleccionado, mostrar ChatScreen
                                if (selectedChat != null) {
                                    ChatScreen(
                                        receptorId = selectedChat!!.first,
                                        receptorNombre = selectedChat!!.second,
                                        onBack = { selectedChat = null }
                                    )
                                } else {
                                    // Lista de contactos con botón Cerrar sesión
                                    ContactosScreen(
                                        authVM = authVM,
                                        onChatClick = { uid, nombre ->
                                            selectedChat = uid to nombre
                                        }
                                    )
                                }
                            }
                        }

                        is AuthUiState.Error -> {
                            val msg = (state as AuthUiState.Error).message
                            ErrorScreen(
                                mensaje = msg,
                                onRetry = { authVM.refreshUser() }
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ============================
   Pantalla de Login/Registro
   ============================ */
@Composable
fun LoginScreen(
    auth: FirebaseAuth,
    onLoginSuccess: () -> Unit,
    onRegisterSuccess: () -> Unit
) {
    val ctx = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLogin by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (isLogin) "Iniciar Sesión" else "Crear Cuenta",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo Electrónico") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (isLogin) {
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(ctx, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()
                                onLoginSuccess()
                            } else {
                                Toast.makeText(ctx, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(ctx, "Registro exitoso", Toast.LENGTH_SHORT).show()
                                onRegisterSuccess()
                            } else {
                                Toast.makeText(ctx, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLogin) "Ingresar" else "Registrarse")
        }

        TextButton(onClick = { isLogin = !isLogin }) {
            Text(if (isLogin) "¿No tienes cuenta? Regístrate" else "¿Ya tienes cuenta? Inicia sesión")
        }
    }
}

/* ============================
   Pantalla de error/reintento
   ============================ */
@Composable
fun ErrorScreen(mensaje: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Error: $mensaje",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onRetry) {
                Text("Reintentar conexión")
            }
        }
    }
}
