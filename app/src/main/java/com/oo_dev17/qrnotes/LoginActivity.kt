package com.oo_dev17.qrnotes

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.oo_dev17.qrnotes.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var login: Button

    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        username = binding.username
        password = binding.password
        login = binding.login

        // disable login button unless both username / password is valid
        updateButton()
        username.afterTextChanged { updateButton() }
        password.afterTextChanged { updateButton() }

        login.setOnClickListener {
            auth.signInWithEmailAndPassword(username.text.toString(), password.text.toString())
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    showLegacyLoginFailureDialog()
                }
        }

        // New: Google Sign-In
        binding.googleSignIn?.setOnClickListener {
            startGoogleSignIn()
        }
    }

    private fun startGoogleSignIn() {
        val serverClientId = getString(R.string.default_web_client_id)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    context = this@LoginActivity,
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

                        signInWithGoogleFirebaseCredential(firebaseCredential, googleCred.id)
                    } catch (e: GoogleIdTokenParsingException) {
                        Toast.makeText(this@LoginActivity, e.message, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@LoginActivity, "Unsupported credential", Toast.LENGTH_SHORT)
                        .show()
                }
            } catch (e: GetCredentialException) {
                // user cancelled, or no credentials available
                Toast.makeText(this@LoginActivity, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun signInWithGoogleFirebaseCredential(
        firebaseCredential: com.google.firebase.auth.AuthCredential,
        googleEmail: String?
    ) {
        auth.signInWithCredential(firebaseCredential)
            .addOnSuccessListener {
                Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { ex ->
                // Collision: email exists with password account -> link instead
                if (ex is FirebaseAuthUserCollisionException) {
                    val email = googleEmail ?: username.text.toString()
                    if (email.isBlank()) {
                        Toast.makeText(
                            this,
                            "Account exists. Please sign in with password to link.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@addOnFailureListener
                    }

                    // Deprecated in Firebase Auth Java API, but still the correct way in Android SDK
                    @Suppress("DEPRECATION")
                    auth.fetchSignInMethodsForEmail(email)
                        .addOnSuccessListener { methodsResult ->
                            val methods = methodsResult.signInMethods.orEmpty()
                            if (methods.contains("password")) {
                                promptForPasswordAndLink(email, firebaseCredential)
                            } else {
                                Toast.makeText(
                                    this,
                                    "Account exists with different sign-in method: $methods",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        .addOnFailureListener { e2 ->
                            Toast.makeText(this, e2.message, Toast.LENGTH_LONG).show()
                        }
                } else {
                    Toast.makeText(
                        this,
                        ex.message ?: "Google login failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun promptForPasswordAndLink(
        email: String,
        googleFirebaseCredential: com.google.firebase.auth.AuthCredential
    ) {
        val input = EditText(this).apply {
            hint = getString(R.string.prompt_password)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle("Link accounts")
            .setMessage(
                "This email already exists. Please enter your old password once to link Google sign-in."
            )
            .setView(input)
            .setPositiveButton("Link") { dialog, _ ->
                val pass = input.text.toString()
                if (pass.isBlank()) {
                    Toast.makeText(this, "Password required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                auth.signInWithEmailAndPassword(email, pass)
                    .addOnSuccessListener {
                        val user = auth.currentUser
                        if (user == null) {
                            Toast.makeText(this, "No user to link", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        user.linkWithCredential(googleFirebaseCredential)
                            .addOnSuccessListener {
                                Toast.makeText(
                                    this,
                                    "Accounts linked. Continue with Google next time.",
                                    Toast.LENGTH_LONG
                                ).show()
                                dialog.dismiss()
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showLegacyLoginFailureDialog() {
        val builder = AlertDialog.Builder(this, R.style.AlertDialogTheme)

        builder.setTitle("Login failed")
            .setMessage("Do you want to create a new account for ${username.text}?")
            .setPositiveButton("Create account") { dialog, _ ->
                auth.createUserWithEmailAndPassword(
                    username.text.toString(),
                    password.text.toString()
                ).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Account created successfully", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        val errorMessage = task.exception?.message ?: "Account creation failed"
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
                dialog.dismiss()
            }
            .setNeutralButton("Send recovery mail") { dialog, _ ->
                if (username.text.isNotEmpty()) {
                    auth.sendPasswordResetEmail(username.text.toString())
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(this, "Recovery mail sent", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Failed to send recovery mail.", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                } else {
                    Toast.makeText(this, "Please enter an email to send recovery mail.", Toast.LENGTH_SHORT)
                        .show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun updateButton() {
        login.isEnabled = username.text.isNotBlank() && password.text.isNotBlank()
    }

    private fun showLoginFailed(@StringRes errorString: Int) {
        Toast.makeText(applicationContext, errorString, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Extension function to simplify setting an afterTextChanged action to EditText components.
 */
fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}
