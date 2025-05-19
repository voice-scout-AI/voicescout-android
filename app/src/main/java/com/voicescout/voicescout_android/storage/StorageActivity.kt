package com.voicescout.voicescout_android.storage

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voicescout.voicescout_android.R

class StorageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_storage)

        val dataset = Array(30) { "홍지연의 목소리 ${it + 1}" }
        val storageAdapter = StorageAdapter(dataset)

        val recyclerView: RecyclerView = findViewById(R.id.storage_rv)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = storageAdapter
    }
}