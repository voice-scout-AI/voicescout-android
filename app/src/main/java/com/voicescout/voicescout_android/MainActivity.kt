package com.voicescout.voicescout_android

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.voicescout.voicescout_android.storage.StorageActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.test_btn_1).setOnClickListener {
            val intent = Intent(this, StorageActivity::class.java)
            startActivity(intent)
        }
    }
}