package com.oo_dev17.qrnotes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
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
import com.google.firebase.Firebase
import kotlin.jvm.java

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
            // Apply the insets as padding to the view.
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            // Return the insets so that other views can also use them
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
                handleSendImage(intent) // Handle single image being sent
            } else {
                handleSendText(intent)
            }
        }
    }
    val TAG="MainActivity"
    private fun handleSendText(intent: Intent) {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            // Update UI to reflect text being shared
            Log.d(TAG, "handleSendText: $it")
            //create a new document
        }
    }
    override fun onStart() {
        super.onStart()
        // Start listening for authentication state changes
        auth.addAuthStateListener(authStateListener)
    }
    override fun onStop() {
        super.onStop()
        // Stop listening to avoid memory leaks
        auth.removeAuthStateListener(authStateListener)
    }
    private fun updateToolbarTitle(user: com.google.firebase.auth.FirebaseUser?) {
        val baseTitle = "QrNotes"
        if (user != null) {
            // User is signed in. Use their display name, or fall back to email.
            val displayName = user.displayName?.ifEmpty { user.email } ?: user.email
            if (!displayName.isNullOrBlank()) {
                supportActionBar?.title = "$baseTitle - $displayName"
            } else {
                supportActionBar?.title = "$baseTitle - displayName is null" // Fallback if no name/email
            }
        } else {
            // No user is signed in
            supportActionBar?.title = "$baseTitle - No user"
        }
    }


    private fun handleSendImage(intent: Intent) {
        (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let {
            val imageUri: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
            // Update UI to reflect image being shared
            Log.d(TAG, "handleSendImage: $it")
            val floatingAddImage = FloatingAddImage(this)
            floatingAddImage.show()
        }
    }



    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    fun getCacheSize(context: Context): Long {
        var size: Long = 0
        // context.cacheDir gives you the path to your app's internal cache
        val cacheDirectory: File = context.cacheDir
        // walkTopDown() iterates through all files and subdirectories
        // forEach adds up the size of each file
        cacheDirectory.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }

        return size
    }

    /**
     * Formats the cache size from bytes into a human-readable string (KB, MB, GB).
     * @param sizeInBytes The size in bytes.
     * @return A formatted string like "12.34 MB".
     */
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
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_scanNewQrCode -> {
                // --- THIS IS THE TRIGGER ---
                // Notify the SharedViewModel that the user wants to scan.
                // DO NOT call any fragment-specific code here.
                sharedViewModel.requestAction(NoteAction.SCAN_NEW_QR_CODE)

                true // Return true to indicate the click was handled
            }
            R.id.action_login->{
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_settings -> {
                val totalCacheSizeBytes = getCacheSize(this)

                val builder = AlertDialog.Builder(this,R.style.AlertDialogTheme)
                val formattedCacheSize = formatSize(totalCacheSizeBytes)
                builder.setTitle("QrNote App Options")
                    .setMessage("Cache Size $formattedCacheSize\nCache folder: ${cacheDir.absolutePath}" )
                    .setNeutralButton("Clear Cache"){dialog, _ ->
                        val deleted = cacheDir.deleteRecursively()
                        if (deleted) {
                            Toast.makeText(this, "Cache cleared successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Failed to clear cache", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
                /*
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)*/
                true}
            else -> super.onOptionsItemSelected(item)

        }
    }
    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}