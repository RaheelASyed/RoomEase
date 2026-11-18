package edu.virginia.cs4720.roomease.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import edu.virginia.cs4720.roomease.data.model.Message
import edu.virginia.cs4720.roomease.data.model.toMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val db: FirebaseFirestore,
    private val writeErrors: WriteErrorBus
) {
    private fun messages(householdId: String) =
        db.collection("households").document(householdId).collection("messages")

    /** Last 200 messages, oldest first, live. Pending (unsynced) sends come through with pendingSync = true. */
    fun observeMessages(householdId: String): Flow<List<Message>> =
        messages(householdId).orderBy("createdAt", Query.Direction.ASCENDING).limitToLast(200).snapshots()
            .map { snap -> snap.documents.map { it.toMessage() } }

    /**
     * Queues the message locally (it shows immediately as "Sending…") and lets Firestore deliver it.
     * The SDK retries on its own and keeps the write across process death, so the app never re-sends.
     */
    fun send(householdId: String, text: String, senderId: String, senderName: String) {
        val task = messages(householdId).document().set(
            mapOf(
                "text" to text.trim(),
                "senderId" to senderId,
                "senderName" to senderName,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        writeErrors.track(task, "send your message")
    }
}

/** True if this document is already in the local cache, i.e. an earlier attempt already queued it. */
internal suspend fun isInLocalCache(ref: DocumentReference): Boolean = try {
    ref.get(Source.CACHE).await().exists()
} catch (_: Exception) {
    false
}
