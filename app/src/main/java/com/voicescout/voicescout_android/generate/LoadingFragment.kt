package com.voicescout.voicescout_android.generate

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.voicescout.voicescout_android.R

private const val ARG_SELECTED_VOICE = "SELECTED_VOICE"

class LoadingFragment : Fragment() {
    private var selectedVoice: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            selectedVoice = it.getString(ARG_SELECTED_VOICE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_loading, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = view.findViewById<TextView>(R.id.text_generate_guide)
        val selectedVoiceStr = selectedVoice ?: ""

        val fullText = selectedVoiceStr + "로\n발화를 생성중입니다."

        val spannableString = SpannableString(fullText)
        spannableString.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            selectedVoiceStr.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        title.text = spannableString
    }

    companion object {
        @JvmStatic
        fun newInstance(selectedVoice: String) =
            LoadingFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SELECTED_VOICE, selectedVoice)
                }
            }
    }
}