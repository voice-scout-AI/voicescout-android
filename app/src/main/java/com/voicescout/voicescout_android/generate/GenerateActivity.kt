package com.voicescout.voicescout_android.generate

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.voicescout.voicescout_android.R

class GenerateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generate)

        val selectedVoice = intent.getStringExtra("SELECTED_VOICE") ?: ""

        supportFragmentManager.commit {
            replace(R.id.generate_frame, GenerateFragment.newInstance(selectedVoice))
            setReorderingAllowed(true)
            addToBackStack("")
        }
    }
}