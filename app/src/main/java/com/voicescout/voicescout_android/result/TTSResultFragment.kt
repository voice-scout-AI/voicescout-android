package com.voicescout.voicescout_android.result

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.voicescout.voicescout_android.R
import java.io.File

private const val ARG_AUDIO_PATH = "AUDIO_PATH"
private const val ARG_SELECTED_VOICE = "SELECTED_VOICE"

class TTSResultFragment : Fragment() {
    private var audioPath: String? = null
    private var selectedVoice: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            audioPath = it.getString(ARG_AUDIO_PATH)
            selectedVoice = it.getString(ARG_SELECTED_VOICE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_tts_result, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val fileNameText = view.findViewById<TextView>(R.id.text_result_filename)
        val playButton = view.findViewById<Button>(R.id.btn_play_audio)
        val shareButton = view.findViewById<Button>(R.id.btn_share_audio)

        val audioFile = File(audioPath ?: return)
        fileNameText.text = audioFile.name

        playButton.setOnClickListener {
            try {
                // 파일 존재 여부 확인
                Log.d("AudioDebug", "오디오 파일 경로: ${audioFile.absolutePath}")
                Log.d("AudioDebug", "파일 존재 여부: ${audioFile.exists()}")
                Log.d("AudioDebug", "파일 크기: ${audioFile.length()} bytes")
                Log.d("AudioDebug", "파일 읽기 가능: ${audioFile.canRead()}")

                if (!audioFile.exists()) {
                    Toast.makeText(context, "오디오 파일이 존재하지 않습니다.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (audioFile.length() == 0L) {
                    Toast.makeText(context, "오디오 파일이 비어있습니다.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                Log.d("AudioDebug", "MediaPlayer 시작")
                MediaPlayer().apply {
                    setDataSource(audioFile.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        Log.d("AudioDebug", "재생 완료")
                        release()
                    }
                    setOnErrorListener { mp, what, extra ->
                        Log.e("AudioDebug", "MediaPlayer 에러: what=$what, extra=$extra")
                        Toast.makeText(context, "오디오 재생 에러: $what", Toast.LENGTH_SHORT).show()
                        false
                    }
                    start()
                }
                Log.d("AudioDebug", "MediaPlayer 재생 시작됨")
                Toast.makeText(context, "오디오 재생 시작", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e("AudioDebug", "오디오 재생 예외 발생", e)
                Toast.makeText(context, "오디오 재생 중 오류: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        shareButton.setOnClickListener {
            val uri =
                FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    audioFile,
                )

            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "audio/wav" // 변경 가능: audio/mp3
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra("sms_body", "이 음성을 들어보세요!")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            val packageManager = requireContext().packageManager
            val resInfos = packageManager.queryIntentActivities(intent, 0)
            val smsApps =
                resInfos.filter {
                    it.activityInfo.packageName.contains("mms") || it.activityInfo.packageName.contains(
                        "message"
                    )
                }

            if (smsApps.isNotEmpty()) {
                val smsIntent =
                    Intent(intent).apply {
                        `package` = smsApps[0].activityInfo.packageName
                    }
                startActivity(smsIntent)
            } else {
                startActivity(Intent.createChooser(intent, "앱 선택"))
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(
            audioPath: String,
            selectedVoice: String,
        ) = TTSResultFragment().apply {
            arguments =
                Bundle().apply {
                    putString(ARG_AUDIO_PATH, audioPath)
                    putString(ARG_SELECTED_VOICE, selectedVoice)
                }
        }
    }
}
