package com.oo_dev17.qrnotes

import android.app.Activity
import android.app.AlertDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.oo_dev17.qrnotes.databinding.FragmentFirstBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment(), ItemClickListener,NewQrNoteListener {

    private lateinit var itemAdapter: ItemAdapter
    private lateinit var qrNotes: MutableList<QrNote>
    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root


        checkStoragePermission()
    }

    private val REQUEST_CODE_READ_EXTERNAL_STORAGE = 100

    private fun checkStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Use READ_MEDIA_IMAGES for Android 13 and above
           Manifest.permission.READ_MEDIA_IMAGES
        } else {
            // Use READ_EXTERNAL_STORAGE for older versions
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(), permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request the permission
            ActivityCompat.requestPermissions(
                requireActivity(), arrayOf(permission), REQUEST_CODE_READ_EXTERNAL_STORAGE
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Toast.makeText(requireContext(), "Scan cancelled", Toast.LENGTH_SHORT).show()
        } else {
            val scannedData = result.contents // Get the scanned QR code data
            Toast.makeText(requireContext(), "Scanned: $scannedData", Toast.LENGTH_SHORT).show()
            binding.searchText.setText(  scannedData.toString())
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = binding.myRecyclerView // Assuming you have a RecyclerView in your layout
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        getAllQrNotes { notes ->
            notes.forEach { qrNote ->
                Log.d("Firestore", "QrNote: ${qrNote.title}, ${qrNote.content}")
            }
            qrNotes = notes.toMutableList()
            itemAdapter = ItemAdapter(qrNotes, this)

            binding.myRecyclerView.adapter = itemAdapter
            binding.myRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = itemAdapter
        }
    }

    private fun getAllQrNotes(callback: (List<QrNote>) -> Unit) {


        val db = FirebaseFirestore.getInstance()
        db.collection("qrNotes")
            .get()
            .addOnSuccessListener { result ->
                Log.w("Firestore", "Successful getting QrNotes")
                try {
                    val notes = result.map { qrNote ->
                        val qr = qrNote.toObject(QrNote::class.java)
                        qr.copy(documentId = qrNote.id)
                    }
                    callback(notes)
                } catch (e: Exception) {
                    Log.e("Firestore", "Error converting Firestore documents to QrNote objects", e)
                }


            }
            .addOnFailureListener { exception ->
                Log.w("Firestore", "Error getting QrNotes", exception)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onItemClicked(item: QrNote) {
        //  (requireActivity() as MainActivity).sharedQrNote = item
        findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)

    }

    override fun showQrNoteOptions(qrNote: QrNote) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("QrNote Options")
            .setMessage("What do you want to do with this QrNote?")
            .setPositiveButton("Delete") { dialog, _ ->
                deleteQrNote(qrNote)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun deleteQrNote(qrNote: QrNote) {
        val position = qrNotes.indexOf(qrNote)
        if (position != -1) {
            qrNotes.removeAt(position)
            FirebaseFirestore.getInstance().collection("qrNotes").document(qrNote.documentId!!)
                .delete()
            itemAdapter.notifyItemRemoved(position)
            Toast.makeText(requireContext(), "QrNote ${qrNote.title} deleted", Toast.LENGTH_SHORT)
                .show()
        }
    }
    override fun onNewQrNote(qrNote: QrNote) {
        qrNotes.add(0, qrNote)
        itemAdapter.notifyItemInserted(0)
    }
}