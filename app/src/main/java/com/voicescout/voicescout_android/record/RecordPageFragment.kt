package com.voicescout.voicescout_android.record

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.voicescout.voicescout_android.R

class RecordPageFragment : Fragment() {
    private val argSentence = "sentence"
    private val argPosition = "position"

    private var sentence: String? = null
    private var position: Int = 0

    private lateinit var sentenceTextView: TextView
    private lateinit var recordButton: Button
    private lateinit var recordingStatusTextView: TextView
    private lateinit var restartIcon: ImageView

    private var listener: OnRecordButtonClickListener? = null
    private var isRecorded = false // 녹음 완료 상태를 추적

    companion object {
        @JvmStatic
        fun newInstance(
            sentence: String,
            position: Int,
        ) = RecordPageFragment().apply {
            arguments =
                Bundle().apply {
                    putString(argSentence, sentence)
                    putInt(argPosition, position)
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
            sentence = it.getString(argSentence)
            position = it.getInt(argPosition)
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
        restartIcon = view.findViewById(R.id.restartIcon)

        sentenceTextView.text = sentence

        recordButton.setOnClickListener {
            if (isRecorded) {
                // 녹음 완료 상태에서는 다음 페이지로 이동
                listener?.onNextButtonClick(position)
            } else {
                // 녹음 중이거나 녹음 전 상태에서는 녹음 버튼 동작
                listener?.onRecordButtonClick(position)
            }
        }

        restartIcon.setOnClickListener {
            // 재녹음
            isRecorded = false
            updateRecordButtonState(false)
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
                restartIcon.visibility = View.GONE
            } else if (isRecorded) {
                // 녹음 완료 상태
                recordButton.text = "다음\n "  // 아래에 빈 문자 추가로 높이 맞춤
                recordingStatusTextView.text = " "
                recordingStatusTextView.visibility = View.VISIBLE
                restartIcon.visibility = View.VISIBLE
            } else {
                // 녹음 전 상태
                recordButton.setText(R.string.record_start)
                recordingStatusTextView.text = " "
                recordingStatusTextView.visibility = View.VISIBLE
                restartIcon.visibility = View.GONE
            }
        }
    }

    fun setRecordedState(recorded: Boolean) {
        isRecorded = recorded
        updateRecordButtonState(false)
    }

    interface OnRecordButtonClickListener {
        fun onRecordButtonClick(position: Int)
        fun onNextButtonClick(position: Int)
    }
}
