package com.voicescout.voicescout_android.record

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.voicescout.voicescout_android.R

class RecordPageFragment : Fragment() {
    private val ARG_SENTENCE = "sentence"
    private val ARG_POSITION = "position"

    private var sentence: String? = null
    private var position: Int = 0

    private lateinit var sentenceTextView: TextView
    private lateinit var recordButton: Button
    private lateinit var recordingStatusTextView: TextView

    private var listener: OnRecordButtonClickListener? = null

    companion object {
        @JvmStatic
        fun newInstance(
            sentence: String,
            position: Int,
        ) = RecordPageFragment().apply {
            arguments =
                Bundle().apply {
                    putString(ARG_SENTENCE, sentence)
                    putInt(ARG_POSITION, position)
                }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnRecordButtonClickListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnRecordButtonClickListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            sentence = it.getString(ARG_SENTENCE)
            position = it.getInt(ARG_POSITION)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_record, container, false)
        sentenceTextView = view.findViewById(R.id.sentenceTextView)
        recordButton = view.findViewById(R.id.recordButton)
        recordingStatusTextView = view.findViewById(R.id.recordingStatusTextView)

        sentenceTextView.text = sentence

        recordButton.setOnClickListener {
            listener?.onRecordButtonClick(position)
        }

        return view
    }

    fun updateRecordButtonState(isRecording: Boolean) {
        if (::recordButton.isInitialized) {
            if (isRecording) {
                recordButton.setText(R.string.record_stop)
                recordingStatusTextView.text = "녹음 중..."
                recordingStatusTextView.visibility = View.VISIBLE
            } else {
                recordButton.setText(R.string.record_start)
                recordingStatusTextView.visibility = View.GONE
            }
        }
    }

    interface OnRecordButtonClickListener {
        fun onRecordButtonClick(position: Int)
    }
}
