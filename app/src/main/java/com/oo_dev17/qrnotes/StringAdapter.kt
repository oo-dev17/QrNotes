package com.oo_dev17.qrnotes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class StringAdapter(private val stringList: List<String>) :
    RecyclerView.Adapter<StringViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StringViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.fragment_second, parent, false)
        return StringViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: StringViewHolder, position: Int) {
        holder.stringTextView.text = stringList[position]
    }

    override fun getItemCount(): Int {
        return stringList.size
    }
}