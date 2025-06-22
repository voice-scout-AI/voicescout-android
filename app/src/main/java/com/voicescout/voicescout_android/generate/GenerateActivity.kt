package com.voicescout.voicescout_android.generate

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.commit
import com.voicescout.voicescout_android.MainActivity
import com.voicescout.voicescout_android.R

class GenerateActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var menuButton: ImageView
    private lateinit var logoButton: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generate)

        val selectedVoice = intent.getStringExtra("SELECTED_VOICE") ?: ""
        val voiceId = intent.getIntExtra("VOICE_ID", 0)

        drawerLayout = findViewById(R.id.drawer_layout)
        menuButton = findViewById(R.id.toolbar_menu)
        logoButton = findViewById(R.id.toolbar_logo)

        menuButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        logoButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        supportFragmentManager.commit {
            replace(R.id.generate_frame, GenerateFragment.newInstance(selectedVoice, voiceId))
            setReorderingAllowed(true)
            addToBackStack("")
        }
    }
}