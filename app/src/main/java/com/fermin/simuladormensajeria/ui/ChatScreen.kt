package com.fermin.simuladormensajeria.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*

data class Mensaje(
    val senderId: String = "",
    val receiverId: String = "",
    val texto: String = "",
    val imageUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    receptorId: String,
    receptorNombre: String,
    onBack: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val currentUid = auth.currentUser?.uid ?: ""
    val chatId = listOf(currentUid, receptorId).sorted().joinToString("_")

    var mensajes by remember { mutableStateOf<List<Mensaje>>(emptyList()) }
    var nuevoMensaje by remember { mutableStateOf("") }
    var imagenSeleccionada by remember { mutableStateOf<Uri?>(null) }
    var descripcionImagen by remember { mutableStateOf("") }
    var imagenAmpliada by remember { mutableStateOf<String?>(null) }
    var mostrandoVistaPrevia by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }

    // 🔹 Escucha en tiempo real los mensajes
    LaunchedEffect(chatId) {
        db.collection("mensajes")
            .document(chatId)
            .collection("mensajes")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    mensajes = snapshot.toObjects(Mensaje::class.java)
                }
            }
    }

    // 🔹 Selector de imagen
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        imagenSeleccionada = uri
        if (uri != null) mostrandoVistaPrevia = true
    }

    // 🔹 Vista previa antes de enviar
    if (mostrandoVistaPrevia && imagenSeleccionada != null) {
        AlertDialog(
            onDismissRequest = { mostrandoVistaPrevia = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        cargando = true
                        val fileName = "chat_${System.currentTimeMillis()}.jpg"
                        val ref = storage.reference.child("chat_images/$chatId/$fileName")

                        ref.putFile(imagenSeleccionada!!)
                            .continueWithTask { ref.downloadUrl }
                            .addOnSuccessListener { uri ->
                                val msgConImagen = Mensaje(
                                    senderId = currentUid,
                                    receiverId = receptorId,
                                    texto = descripcionImagen.trim(),
                                    imageUrl = uri.toString(),
                                    timestamp = System.currentTimeMillis()
                                )
                                db.collection("mensajes")
                                    .document(chatId)
                                    .collection("mensajes")
                                    .add(msgConImagen)

                                imagenSeleccionada = null
                                descripcionImagen = ""
                                mostrandoVistaPrevia = false
                                cargando = false
                            }
                            .addOnFailureListener {
                                cargando = false
                                mostrandoVistaPrevia = false
                            }
                    }
                ) { Text("Enviar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    imagenSeleccionada = null
                    descripcionImagen = ""
                    mostrandoVistaPrevia = false
                }) {
                    Text("Cancelar")
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (cargando) {
                        CircularProgressIndicator()
                    } else {
                        Image(
                            painter = rememberAsyncImagePainter(imagenSeleccionada),
                            contentDescription = "Vista previa",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(350.dp),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = descripcionImagen,
                            onValueChange = { descripcionImagen = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Añadir descripción...") }
                        )
                    }
                }
            }
        )
    }

    // 🔹 Imagen ampliada (ya enviada)
    if (imagenAmpliada != null) {
        AlertDialog(
            onDismissRequest = { imagenAmpliada = null },
            confirmButton = {},
            text = {
                Image(
                    painter = rememberAsyncImagePainter(imagenAmpliada),
                    contentDescription = "Imagen ampliada",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    contentScale = ContentScale.Crop
                )
            }
        )
    }

    // 🔹 UI principal del chat
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(receptorNombre) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        ) {
            items(mensajes) { msg ->
                val isMine = msg.senderId == currentUid
                val align = if (isMine) Alignment.End else Alignment.Start
                val color = if (isMine)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = align
                ) {
                    Surface(
                        color = color,
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            if (msg.texto.isNotBlank()) {
                                Text(text = msg.texto, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            msg.imageUrl?.let { url ->
                                Image(
                                    painter = rememberAsyncImagePainter(url),
                                    contentDescription = "Imagen enviada",
                                    modifier = Modifier
                                        .size(200.dp)
                                        .clickable { imagenAmpliada = url },
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            Text(
                                text = SimpleDateFormat("HH:mm", Locale.getDefault())
                                    .format(Date(msg.timestamp)),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { imagePicker.launch("image/*") }) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Agregar imagen")
            }

            OutlinedTextField(
                value = nuevoMensaje,
                onValueChange = { nuevoMensaje = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe un mensaje...") }
            )

            Spacer(Modifier.width(8.dp))

            Button(onClick = {
                if (nuevoMensaje.isNotBlank()) {
                    val msg = Mensaje(
                        senderId = currentUid,
                        receiverId = receptorId,
                        texto = nuevoMensaje.trim(),
                        timestamp = System.currentTimeMillis()
                    )
                    db.collection("mensajes")
                        .document(chatId)
                        .collection("mensajes")
                        .add(msg)
                    nuevoMensaje = ""
                }
            }) {
                Text("Enviar")
            }
        }
    }
}
