package com.oo_dev17.qrnotes

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
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

        // Sample data
        val items = listOf(
            QrNote("Item 1", "Description 1"),
            QrNote("Item 2", "Description 2"),
            QrNote("Item 3", "Description 3")
        )

        val adapter = ItemAdapter(items, this) // Pass 'this' as the listener
        recyclerView.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onItemClicked(item: QrNote ) {

        findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)

    }

}