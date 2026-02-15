package com.oo_dev17.qrnotes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.launch
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.oo_dev17.qrnotes.databinding.ActivityMainBinding
import java.io.File
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var sharedDb: FirebaseFirestore
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private val sharedViewModel: SecondSharedViewModel by viewModels()
    private val authStateListener: FirebaseAuth.AuthStateListener
        get() = FirebaseAuth.AuthStateListener { firebaseAuth ->
            updateToolbarTitle(firebaseAuth.currentUser)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        auth = FirebaseAuth.getInstance()
        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Get intent, action and MIME type
        val intent = intent
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            if (type.startsWith("image/")) {
                handleSendImage(intent)
            } else {
                handleSendText(intent)
            }
        }
        updateToolbarTitle(FirebaseAuth.getInstance().currentUser)
    }

    val TAG = "MainActivity"
    private fun handleSendText(intent: Intent) {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            Log.d(TAG, "handleSendText: $it")
        }
    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(authStateListener)
    }

    override fun onStop() {
        super.onStop()
        auth.removeAuthStateListener(authStateListener)
    }

    private fun updateToolbarTitle(user: com.google.firebase.auth.FirebaseUser?) {

        if (user != null) {
            val displayName = user.displayName?.ifEmpty { user.email } ?: user.email
            if (!displayName.isNullOrBlank()) {
                supportActionBar?.subtitle = displayName
            } else {
                supportActionBar?.subtitle = "displayName is null"
            }
        } else {
            supportActionBar?.subtitle = "No user!"
        }
    }

    private fun handleSendImage(intent: Intent) {
        (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let {
            val imageUri: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Log.d(TAG, "handleSendImage: $it")
            val floatingAddImage = FloatingAddImage(this)
            floatingAddImage.show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    fun getCacheSize(context: Context): Long {
        var size: Long = 0
        val cacheDirectory: File = context.cacheDir
        cacheDirectory.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }

        return size
    }

    fun formatSize(sizeInBytes: Long): String {
        if (sizeInBytes < 1024) return "$sizeInBytes B"
        val kb = sizeInBytes / 1024
        if (kb < 1024) return "$kb KB"
        val mb = kb / 1024
        if (mb < 1024) return String.format("%.2f MB", mb.toFloat())
        val gb = mb / 1024
        return String.format("%.2f GB", gb.toFloat())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_scanNewQrCode -> {
                sharedViewModel.requestAction(NoteAction.SCAN_NEW_QR_CODE)
                true
            }

            R.id.action_login -> {
                // If already logged in, do nothing (or show a message)
                if (FirebaseAuth.getInstance().currentUser != null) {
                    Toast.makeText(this, "Already logged in", Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(Intent(this, LoginActivity::class.java))
                }
                true
            }

            R.id.action_logout -> {
                FirebaseAuth.getInstance().signOut()
                true
            }

            R.id.action_quitApp -> {
                finish()
                true
            }

            R.id.action_settings -> {
                val totalCacheSizeBytes = getCacheSize(this)
                val builder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
                val formattedCacheSize = formatSize(totalCacheSizeBytes)
                builder.setTitle("QrNote App Options")
                    .setMessage("Cache Size $formattedCacheSize\nCache folder: ${cacheDir.absolutePath}")
                    .setNeutralButton("Clear Cache") { _, _ ->
                        val deleted = cacheDir.deleteRecursively()
                        if (deleted) {
                            Toast.makeText(
                                this, "Cache cleared successfully", Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(this, "Failed to clear cache", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
