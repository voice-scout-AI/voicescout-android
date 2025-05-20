package com.voicescout.voicescout_android.generate

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.voicescout.voicescout_android.R

private const val ARG_SELECTED_VOICE = "SELECTED_VOICE"

/**
 * A simple [Fragment] subclass.
 * Use the [GenerateFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class GenerateFragment : Fragment() {
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
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_generate, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = view.findViewById<TextView>(R.id.text_generate_guide)
        val btnGenerate = view.findViewById<Button>(R.id.btn_generate)

        val selectedVoiceStr = selectedVoice ?: ""

        val fullText = selectedVoiceStr + "로\n다음 문장의 발화를 생성합니다."

        val spannableString = SpannableString(fullText)
        spannableString.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            selectedVoiceStr.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        title.text = spannableString

        btnGenerate.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.generate_frame, LoadingFragment.newInstance(selectedVoiceStr))
                setReorderingAllowed(true)
                addToBackStack(null)
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(selectedVoice: String) =
            GenerateFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SELECTED_VOICE, selectedVoice)
                }
            }
    }
}