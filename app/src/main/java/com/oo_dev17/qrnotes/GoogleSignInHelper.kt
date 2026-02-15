package com.oo_dev17.qrnotes

import android.content.Context
import android.widget.Toast
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

object GoogleSignInHelper {

    /**
     * Launches the Credential Manager Google sign-in flow and signs into Firebase.
     * Call this from a coroutine.
     */
    suspend fun signIn(
        context: Context,
        auth: FirebaseAuth,
        serverClientId: String,
        onSuccess: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val result = credentialManager.getCredential(
                context = context,
                request = request
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                try {
                    val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCredential =
                        GoogleAuthProvider.getCredential(googleCred.idToken, null)

                    auth.signInWithCredential(firebaseCredential)
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { ex -> onFailure(ex) }
                } catch (e: GoogleIdTokenParsingException) {
                    onFailure(e)
                }
            } else {
                onFailure(IllegalStateException("Unsupported credential"))
            }
        } catch (e: GetCredentialException) {
            // user cancelled, or no credentials available
            onFailure(e)
        } catch (t: Throwable) {
            onFailure(t)
        }
    }
}
