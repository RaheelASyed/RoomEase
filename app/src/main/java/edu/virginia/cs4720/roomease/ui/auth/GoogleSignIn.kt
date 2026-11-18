package edu.virginia.cs4720.roomease.ui.auth

import android.annotation.SuppressLint
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

sealed interface GoogleSignInResult {
    data class Token(val idToken: String) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data class Failed(val message: String) : GoogleSignInResult
}

/**
 * Google sign-in through Credential Manager. This needs an Activity context to show the
 * account picker, so it lives in the UI layer; the ViewModel only receives the ID token
 * and hands it to Firebase Auth (ViewModels never hold a Context, Lecture 6).
 */
object GoogleSignIn {

    /**
     * The OAuth "Web client" ID that the google-services plugin generates as R.string.default_web_client_id
     * once Google sign-in is enabled in the Firebase console and the app's SHA-1 is registered.
     * Looked up by name so the app still builds before that console step is done.
     */
    @SuppressLint("DiscouragedApi")
    fun webClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
    }

    suspend fun requestIdToken(activityContext: Context): GoogleSignInResult {
        val clientId = webClientId(activityContext)
            ?: return GoogleSignInResult.Failed(
                "Google sign-in isn't set up for this build yet. Enable the Google provider in Firebase and re-download google-services.json."
            )
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(clientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleSignInResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleSignInResult.Failed("That account type isn't supported. Choose a Google account.")
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleSignInResult.Failed("No Google account found on this device. Add one in Settings, or use email.")
        } catch (e: GetCredentialException) {
            GoogleSignInResult.Failed("Google sign-in failed. ${e.message ?: "Please try again."}")
        } catch (e: Exception) {
            GoogleSignInResult.Failed("Google sign-in failed. Please try again.")
        }
    }

    /** On sign-out, also forget the chosen Google account so the picker shows next time. */
    suspend fun clearCredentialState(context: Context) {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
            // Nothing stored or provider unavailable: safe to ignore.
        }
    }
}
