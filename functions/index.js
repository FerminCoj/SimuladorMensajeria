// =====================================================
// Enviar notificaciones push cuando se crea un mensaje
// =====================================================
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const admin = require("firebase-admin");

initializeApp();
const db = getFirestore();

// Trigger: cada vez que se crea un mensaje en Firestore
exports.onMessageCreated = onDocumentCreated("mensajes/{chatId}/mensajes/{messageId}", async (event) => {
  const snap = event.data;
  if (!snap) return;

  const msg = snap.data();
  const { senderId, receiverId, texto, imageUrl } = msg;

  if (!receiverId || senderId === receiverId) return;

  try {
    // Obtener tokens del usuario receptor
    const receiverDoc = await db.collection("usuarios").doc(receiverId).get();
    const tokens = (receiverDoc.get("fcmTokens") || []).filter(Boolean);

    if (!tokens.length) {
      console.log("No hay tokens registrados para el receptor:", receiverId);
      return;
    }

    // Obtener nombre del remitente
    const senderDoc = await db.collection("usuarios").doc(senderId).get();
    const senderName =
      senderDoc.get("nombre") ||
      senderDoc.get("displayName") ||
      "Usuario";

    // Crear título y cuerpo de la notificación
    const title = senderName;
    const body = imageUrl
      ? "Te envió una imagen"
      : texto && texto.trim() !== ""
      ? texto
      : "Tienes un nuevo mensaje";

    // Construir payload compatible con AppMessagingService.kt
    const payload = {
      tokens,
      notification: {
        title,
        body,
      },
      data: {
        senderId: senderId,
        senderName: senderName,
        message: body,
      },
    };

    // Enviar notificación
    const res = await getMessaging().sendEachForMulticast(payload);

    console.log(
      `Notificación enviada a ${receiverId}: ${res.successCount} éxito / ${res.failureCount} error`
    );

    // Eliminar tokens inválidos
    const invalidTokens = res.responses
      .map((r, i) => (!r.success ? tokens[i] : null))
      .filter((t) => t !== null);

    if (invalidTokens.length > 0) {
      console.log("Eliminando tokens inválidos:", invalidTokens);
      await db
        .collection("usuarios")
        .doc(receiverId)
        .update({
          fcmTokens: FieldValue.arrayRemove(...invalidTokens),
        });
    }
  } catch (error) {
    console.error("Error al enviar la notificación:", error);
  }
});

