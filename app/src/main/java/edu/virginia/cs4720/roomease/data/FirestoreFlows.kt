package edu.virginia.cs4720.roomease.data

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow

// Lecture 12 "Realtime: the killer feature": addSnapshotListener, and .remove() when done.
// callbackFlow turns the listener into a Flow; awaitClose removes the registration
// as soon as the collector (a ViewModel) stops listening.
// MetadataChanges.INCLUDE re-emits when a pending local write is confirmed by the server,
// which is how the UI switches "Sending…" to "Sent".

fun Query.snapshots(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
        if (error != null) close(error) else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

fun DocumentReference.snapshots(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
        if (error != null) close(error) else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

/** Human error messages, not exception.toString() (Lecture 11 "Login UX that doesn't fail the rubric"). */
fun Throwable.friendlyMessage(): String = when (this) {
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "You don't have permission to do that in this household."
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "You're offline. Changes will sync when you reconnect."
        FirebaseFirestoreException.Code.NOT_FOUND -> "That item no longer exists."
        else -> "Something went wrong talking to the server. Please try again."
    }
    is FirebaseNetworkException -> "No internet connection. Check your network and try again."
    else -> message ?: "Something went wrong. Please try again."
}

/**
 * Firestore writes are queued locally and acknowledged later (offline cache, Lecture 12).
 * We don't block the UI waiting for the server; the list shows the item as "pending sync"
 * right away. If the server later rejects the write (e.g. a security rule), this bus
 * reports it so the app can show a snackbar wherever the user is.
 */
class WriteErrorBus {
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    fun track(task: Task<*>, what: String) {
        task.addOnFailureListener { e ->
            _errors.tryEmit("Couldn't $what: ${e.friendlyMessage()}")
        }
    }
}
