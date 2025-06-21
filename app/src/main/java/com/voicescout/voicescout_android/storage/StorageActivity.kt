package com.voicescout.voicescout_android.storage

import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voicescout.voicescout_android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class StorageActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var menuButton: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_storage)

        // Initialize views
        recyclerView = findViewById(R.id.storage_rv)
        drawerLayout = findViewById(R.id.drawer_layout)
        menuButton = findViewById(R.id.toolbar_menu)

        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Setup drawer menu button
        menuButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        loadUsers()
    }

    private fun loadUsers() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("http://10.0.2.2:9880/users")
                    .get()
                    .build()

                Log.d("StorageDebug", "사용자 목록 요청 시작")
                val response = client.newCall(request).execute()

                if (response.isSuccessful && response.body != null) {
                    val responseBody = response.body!!.string()
                    Log.d("StorageDebug", "응답 성공: $responseBody")

                    val userList = parseUsersFromJson(responseBody)

                    launch(Dispatchers.Main) {
                        val storageAdapter = StorageAdapter(userList.toMutableList())
                        recyclerView.adapter = storageAdapter
                        Toast.makeText(this@StorageActivity, "사용자 목록 로드 완료", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    Log.e("StorageDebug", "응답 실패: ${response.code}")
                    launch(Dispatchers.Main) {
                        Toast.makeText(
                            this@StorageActivity,
                            "사용자 목록 로드 실패: ${response.code}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("StorageDebug", "사용자 목록 로드 중 오류 발생", e)
                launch(Dispatchers.Main) {
                    Toast.makeText(this@StorageActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    private fun parseUsersFromJson(jsonString: String): List<User> {
        val userList = mutableListOf<User>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val user = User(
                    id = jsonObject.getInt("id"),
                    text = jsonObject.getString("text")
                )
                userList.add(user)
            }
        } catch (e: Exception) {
            Log.e("StorageDebug", "JSON 파싱 오류", e)
        }
        return userList
    }
}