package com.oo_dev17.qrnotes

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.oo_dev17.qrnotes.databinding.FragmentFirstBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment(), ItemClickListener {

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

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = binding.myRecyclerView // Assuming you have a RecyclerView in your layout
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        /*
                // Sample data
                val items = listOf(
                    QrNote("Item 1", "Description 1"),
                    QrNote("Item 2", "Description 2"),
                    QrNote("Item 3", "Description 3")
                )
        */
        getAllQrNotes { qrNotes ->
            qrNotes.forEach { qrNote ->
                Log.d("Firestore", "QrNote: ${qrNote.title}, ${qrNote.content}")
            }
            val adapter = ItemAdapter(qrNotes)
            recyclerView.adapter = adapter
        }
    }

    private fun getAllQrNotes(callback: (List<QrNote>) -> Unit) {


        val db = FirebaseFirestore.getInstance()
        db.collection("qrNotes")
            .get()
            .addOnSuccessListener {
                result ->
                Log.w("Firestore", "Successful getting QrNotes")
                try {
                    val qrNotes =
                        result.toObjects(QrNote::class.java) // Convert Firestore documents to QrNote objects
                    callback(qrNotes)
                }
                catch (e: Exception){
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

}