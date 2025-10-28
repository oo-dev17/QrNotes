package com.oo_dev17.qrnotes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class DocumentAdapter(val stringList: MutableList<String>) :
    RecyclerView.Adapter<DocumentViewHolder>() {

    var onItemClick: ((String) -> Unit)? = null
    var onItemLongClick: ((String, Int) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val documentView =
            LayoutInflater.from(parent.context).inflate(R.layout.item_document, parent, false)
        return DocumentViewHolder(documentView)
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        val stringEntry = stringList[position]
        holder.stringTextView.text = stringEntry
        val charPosition = stringEntry.lastIndexOf('.')
In Android RecyclerView How to change the color of Alternate rows
Asked 8 years, 1 month ago
Modified 2 years ago
Viewed 21k times
 Part of Mobile Development Collective
16

I am new in android , recently I have learned recyclerview and i want to change the color of rows.

Example: I have 10 rows and I want to change color like 5 rows blue and 5 rows red.Alternate rows color should be like this.

From where I have to change this by Adapter or by MainActivity. Please help me

androidandroid-recyclerview
Share
Improve this question
Follow
edited Sep 9, 2017 at 6:49
Abhishek kumar's user avatar
Abhishek kumar
4,45588 gold badges3333 silver badges4848 bronze badges
asked Sep 9, 2017 at 6:47
Chandra01's user avatar
Chandra01
18511 gold badge11 silver badge77 bronze badges
Add a comment

Report this ad
6 Answers
Sorted by:

Highest score (default)
65

You can change the color of alternate row by adding the following code on your Adapter class. You can also change the images of your row by using this code.

Put this code inside OnBindViewHolder in Adapter Class.

JAVA:

 if(position %2 == 1)
    {
        holder.itemView.setBackgroundColor(Color.parseColor("#FFFFFF"));
        //  holder.imageView.setBackgroundColor(Color.parseColor("#FFFFFF"));
    }
    else
    {
       holder.itemView.setBackgroundColor(Color.parseColor("#FFFAF8FD"));
       //  holder.imageView.setBackgroundColor(Color.parseColor("#FFFAF8FD"));
    }


if (position % 2 == 1)
 {
  holder.itemView.setBackgroundColor(Color.parseColor("#FFFFFF"))
  //  holder.imageView.setBackgroundColor(Color.parseColor("#FFFFFF"))
 } 
 else 
 {
  holder.itemView.setBackgroundColor(Color.parseColor("#FFFAF8FD"))
  // holder.imageView.setBackgroundColor(Color.parseColor("#FFFAF8FD"))
 }
        holder.logoTextView.text =
            if (charPosition == -1) "-" else stringEntry.substring(charPosition +1, stringEntry.length)
                .uppercase()

        // Set click listener for the item
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(stringEntry)
        }
        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(stringEntry, position)
            true
        }
    }

    override fun getItemCount(): Int {
        return stringList.size
    }
}
