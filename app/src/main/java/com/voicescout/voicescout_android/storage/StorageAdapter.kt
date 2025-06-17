package com.voicescout.voicescout_android.storage

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.voicescout.voicescout_android.R
import com.voicescout.voicescout_android.generate.GenerateActivity

class StorageAdapter(
    val storageList: Array<String>,
) : RecyclerView.Adapter<StorageAdapter.ViewHolder>() {
    class ViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        val voiceName: TextView
        val voiceHolder: LinearLayout

        init {
            voiceName = view.findViewById(R.id.voice_name)
            voiceHolder = view.findViewById(R.id.voice_item)
        }
    }

    override fun onCreateViewHolder(
        viewGroup: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view =
            LayoutInflater
                .from(viewGroup.context)
                .inflate(R.layout.item_storage, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        position: Int,
    ) {
        viewHolder.voiceName.text = storageList[position]

        viewHolder.voiceHolder.setOnClickListener {
            val context = viewHolder.itemView.context
            val intent = Intent(context, GenerateActivity::class.java)

            intent.putExtra("SELECTED_VOICE", storageList[position])
            context.startActivity(intent)
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = storageList.size
}
