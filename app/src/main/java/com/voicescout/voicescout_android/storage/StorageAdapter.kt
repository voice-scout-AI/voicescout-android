package com.voicescout.voicescout_android.storage

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.voicescout.voicescout_android.ApiConstants
import com.voicescout.voicescout_android.R
import com.voicescout.voicescout_android.generate.GenerateActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

class StorageAdapter(
    private val userList: MutableList<User>,
) : RecyclerView.Adapter<StorageAdapter.ViewHolder>() {
    class ViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        val voiceName: TextView
        val voiceHolder: LinearLayout
        val btnCancel: ImageButton
        val btnRestart: ImageButton

        init {
            voiceName = view.findViewById(R.id.voice_name)
            voiceHolder = view.findViewById(R.id.voice_item)
            btnCancel = view.findViewById(R.id.btn_cancel)
            btnRestart = view.findViewById(R.id.btn_restart)
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
        val user = userList[position]
        viewHolder.voiceName.text = user.text

        viewHolder.voiceHolder.setOnClickListener {
            val context = viewHolder.itemView.context
            val intent = Intent(context, GenerateActivity::class.java)

            intent.putExtra("SELECTED_VOICE", user.text)
            intent.putExtra("VOICE_ID", user.id)
            context.startActivity(intent)
        }

        // Cancel button click listener
        viewHolder.btnCancel.setOnClickListener {
            deleteUser(user.id, position, viewHolder)
        }
    }

    private fun deleteUser(userId: Int, position: Int, viewHolder: ViewHolder) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(ApiConstants.getUserDeleteUrl(userId))
                    .delete()
                    .build()

                Log.d("StorageAdapter", "사용자 삭제 요청 시작: ID $userId")
                val response = client.newCall(request).execute()

                launch(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        // 성공 시 리스트에서 제거
                        userList.removeAt(position)
                        notifyItemRemoved(position)
                        notifyItemRangeChanged(position, userList.size)

                        Toast.makeText(
                            viewHolder.itemView.context,
                            "사용자가 삭제되었습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.d("StorageAdapter", "사용자 삭제 성공: ID $userId")
                    } else {
                        Toast.makeText(
                            viewHolder.itemView.context,
                            "삭제 실패: ${response.code}",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e("StorageAdapter", "사용자 삭제 실패: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(
                        viewHolder.itemView.context,
                        "네트워크 오류: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("StorageAdapter", "사용자 삭제 중 오류 발생", e)
                }
            }
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = userList.size
}
