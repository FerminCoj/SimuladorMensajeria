package com.fermin.simuladormensajeria

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.fermin.simuladormensajeria.ui.ChatScreen
import com.fermin.simuladormensajeria.ui.ContactosScreen
import com.fermin.simuladormensajeria.ui.ProfileSetupScreen
import com.fermin.simuladormensajeria.ui.theme.SimuladorMensajeriaTheme
import com.fermin.simuladormensajeria.vm.AuthUiState
import com.fermin.simuladormensajeria.vm.AuthViewModel
import com.fermin.simuladormensajeria.fcm.AppNotificationUtils
import com.fermin.simuladormensajeria.fcm.FcmTokenManager
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ============================================
        // 1. Solicitud de permiso (Android 13+)
        // ============================================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher =
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                    if (isGranted) {
                        println("Permiso de notificaciones concedido")
                    } else {
                        println("El usuario rechazó el permiso de notificaciones")
                    }
                }

            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    println("Permiso de notificaciones ya concedido")
                }

                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        // ============================================
        // 2. Inicialización de Firebase
        // ============================================
        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()

        // ============================================
        // 3. Canales de notificación y token FCM
        // ============================================
        AppNotificationUtils.ensureChannels(this)

        FcmTokenManager.fetchAndStore(this) { token ->
            if (token != null) {
                FcmTokenManager.updateTokenInFirestore(this, token)
            }
        }

        // ============================================
        // 4. Detectar si se abrió desde una notificación
        // ============================================
        val chatUidExtra = intent.getStringExtra("chatUid")
        val chatNombreExtra = intent.getStringExtra("chatNombre")

        // ============================================
        // 5. Interfaz principal con Compose
        // ============================================
        setContent {
            SimuladorMensajeriaTheme {
                val authVM = remember { AuthViewModel() }
                val state by authVM.state.collectAsState()

                // Si la app viene desde una notificación, abrir ese chat
                var selectedChat by remember {
                    mutableStateOf<Pair<String, String>?>(if (chatUidExtra != null && chatNombreExtra != null)
                        chatUidExtra to chatNombreExtra
                    else null)
                }

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
                                onLoginSuccess = { authVM.refreshUser(this) },
                                onRegisterSuccess = { authVM.refreshUser(this) }
                            )
                        }

                        is AuthUiState.Authenticated -> {
                            val usuario = (state as AuthUiState.Authenticated).user

                            if (usuario.displayName.isNullOrBlank() || usuario.displayName == "null") {
                                ProfileSetupScreen(
                                    authVM = authVM,
                                    onDone = { authVM.refreshUser(this) }
                                )
                            } else {
                                if (selectedChat != null) {
                                    ChatScreen(
                                        receptorId = selectedChat!!.first,
                                        receptorNombre = selectedChat!!.second,
                                        onBack = { selectedChat = null }
                                    )
                                } else {
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
                                onRetry = { authVM.refreshUser(this) }
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
   Pantalla de Error / Reintento
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
