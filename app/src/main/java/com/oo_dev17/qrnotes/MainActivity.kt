package com.oo_dev17.qrnotes

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.oo_dev17.qrnotes.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), SecondFragment.FabVisibilityListener {

    private lateinit var sharedDb: FirebaseFirestore
    private lateinit var fab: FloatingActionButton
    private lateinit var fabQr: FloatingActionButton
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        signInAnonymously()
        sharedDb = Firebase.firestore
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        fab = findViewById(R.id.fab)
        fabQr = findViewById(R.id.fabQr)

        // Show the FAB by default (optional)
        showFab()
        sharedDb = Firebase.firestore

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener { view ->
            showTitleInputDialog()
        }
        binding.fabQr.setOnClickListener { view ->
            launchQRCodeScanner()
        }
    }

    private fun launchQRCodeScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE) // Specify QR code format
            setPrompt("Scan a QR code") // Set a prompt
            setCameraId(0) // Use the default camera
            setBeepEnabled(true) // Play a beep sound
            setBarcodeImageEnabled(true) // Enable saving the barcode image
        }
        scanLauncher.launch(options) // Launch the scanner
    }
    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {

        } else {
            val scannedData = result.contents // Get the scanned QR code data


        }
    }
    private fun showTitleInputDialog() {
        // Create an AlertDialog.Builder using the Activity context
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Create New Note")
        builder.setMessage("Enter a title for the note")

        // Set up the input field
        val input = EditText(this)
        input.hint = "Title"
        builder.setView(input)

        // Set up the buttons
        builder.setPositiveButton("Create") { dialog, _ ->
            val title = input.text.toString()
            if (title.isNotEmpty()) {
                // Create a new QrNote object
                val note = QrNote(title, "") // Empty content for now

                sharedDb.collection("qrNotes").add(note).addOnSuccessListener { docRef ->
                    Log.d("Firestore", "Note added with ID: ${docRef.id}")
                    sharedDb.collection("qrNotes").document(docRef.id).update("id", docRef.id)
                    note.documentId = docRef.id

                    val navHostFragment =
                        supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
                    val firstFragment =
                        navHostFragment?.childFragmentManager?.fragments?.get(0) as? FirstFragment
                    firstFragment?.onNewQrNote(note)
                    // Jump to second fragment
                    val bundle = Bundle()
                    // Put the QrNote into the Bundle
                    bundle.putParcelable("qrNote", note)
                    //  (requireActivity() as MainActivity).sharedQrNote = item
                    // Navigate to SecondFragment with the Bundle
                    val navController = findNavController(R.id.nav_host_fragment_content_main)
                    navController.navigate(R.id.action_FirstFragment_to_SecondFragment, bundle)

                }.addOnFailureListener { e ->
                    Log.w("Firestore", "Error adding note", e)
                }
            } else {
                Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        // Show the dialog
        builder.show()
    }

    val auth = FirebaseAuth.getInstance()

    fun signInAnonymously() {
        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FirebaseAuth", "Anonyme Anmeldung erfolgreich")
                    val user = auth.currentUser
                    Log.d("FirebaseAuth", "Anonyme Benutzer-ID: ${user?.uid}")
                } else {
                    Log.e("FirebaseAuth", "Fehler bei der anonymen Anmeldung", task.exception)
                }
            }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    // Method to show the FAB
    override fun showFab() {
        fab.show()
    }

    // Method to hide the FAB
    override fun hideFab() {
        fab.hide()
    }
}