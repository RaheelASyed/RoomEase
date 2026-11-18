package edu.virginia.cs4720.roomease.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Lecture 11: "Model auth state as a Flow in a repository -> the UI just reacts".
 * Sessions persist across restarts; the SDK restores currentUser on launch.
 */
class AuthRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun createAccount(displayName: String, email: String, password: String) {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: return
        user.updateProfile(userProfileChangeRequest { this.displayName = displayName.trim() }).await()
        ensureUserDocument(user, displayName.trim())
        // The session observer may have created the doc first (before the profile name was set),
        // so always write the name the user typed.
        db.collection("users").document(user.uid).update("displayName", displayName.trim().take(50)).await()
    }

    suspend fun signInWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val user = auth.signInWithCredential(credential).await().user ?: return
        ensureUserDocument(user, user.displayName.orEmpty())
    }

    fun signOut() = auth.signOut()

    /**
     * Creates the private users/{uid} routing document the first time an account signs in.
     * householdId starts null; only joining or creating a household can set it (enforced by rules).
     */
    suspend fun ensureUserDocument(user: FirebaseUser, fallbackName: String = "") {
        val ref = db.collection("users").document(user.uid)
        if (ref.get().await().exists()) return
        val name = user.displayName?.takeIf { it.isNotBlank() }
            ?: fallbackName.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore('@')
            ?: "Roommate"
        try {
            ref.set(
            mapOf(
                "uid" to user.uid,
                "displayName" to name.take(50),
                "email" to (user.email ?: "").take(120),
                "householdId" to null,
                "pendingHouseholdId" to null,
                "createdAt" to FieldValue.serverTimestamp()
            )
            ).await()
        } catch (e: Exception) {
            // Two callers raced to create it; the loser's write is rejected as an update. That's fine.
            if (!ref.get().await().exists()) throw e
        }
    }
}

/** Turns FirebaseAuthException subtypes into "why it failed" (Lecture 11). */
fun Throwable.authMessage(): String = when (this) {
    is FirebaseAuthWeakPasswordException -> "Password must be at least 6 characters."
    is FirebaseAuthUserCollisionException -> "An account already exists with that email. Try signing in."
    is FirebaseAuthInvalidUserException -> "No account found for that email."
    is FirebaseAuthInvalidCredentialsException -> "Email or password is incorrect."
    is FirebaseNetworkException -> "No internet connection. Check your network and try again."
    is FirebaseAuthException -> "Sign-in failed. Please try again."
    else -> friendlyMessage()
}
