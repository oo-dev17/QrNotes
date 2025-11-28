package com.oo_dev17.qrnotes

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.oo_dev17.qrnotes.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

auth=FirebaseAuth.getInstance()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val username = binding.username
        val password = binding.password
        val login = binding.login

        // disable login button unless both username / password is valid
        login.isEnabled = auth.currentUser != null

        login.setOnClickListener { _ ->
            auth.signInWithEmailAndPassword(username.text.toString(), password.text.toString())
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Sign in success, update UI with the signed-in user's information
                        val user = auth.currentUser
                        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                        // Navigate to the main activity or perform other actions
                        finish()
                    } else {
                        // If sign in fails, display a message to the user.
                        Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    val builder = androidx.appcompat.app.AlertDialog.Builder(
                        this,
                        R.style.AlertDialogTheme
                    )

                    builder.setTitle("Login failed")
                        .setMessage("Do you want to create a new account for ${username.text}?")
                        // Use setPositiveButton for the primary action (Create account)
                        .setPositiveButton("Create account") { dialog, _ ->
                            auth.createUserWithEmailAndPassword(
                                username.text.toString(),
                                password.text.toString()
                            ).addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Toast.makeText(
                                        this,
                                        "Account created successfully",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    // It's crucial to show why it failed
                                    val errorMessage =
                                        task.exception?.message ?: "Account creation failed"
                                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                                }
                            }
                            dialog.dismiss()
                        }
                        // Use setNeutralButton for the secondary action (Send recovery)
                        .setNeutralButton("Send recovery mail") { dialog, _ ->
                            if (username.text.isNotEmpty()) {
                                auth.sendPasswordResetEmail(username.text.toString())
                                    .addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            Toast.makeText(
                                                this,
                                                "Recovery mail sent",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                this,
                                                "Failed to send recovery mail.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                            } else {
                                Toast.makeText(
                                    this,
                                    "Please enter an email to send recovery mail.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            dialog.dismiss()
                        }
                        // Use setNegativeButton for the cancel action
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()

                }
        }
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