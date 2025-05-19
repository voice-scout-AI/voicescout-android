package com.voicescout.voicescout_android.storage

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.voicescout.voicescout_android.R

class StorageAdapter(val storageList: Array<String>) :
    RecyclerView.Adapter<StorageAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val voice_name: TextView

        init {
            voice_name = view.findViewById(R.id.voice_name)
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.item_storage, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.voice_name.text = storageList[position]
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = storageList.size
}