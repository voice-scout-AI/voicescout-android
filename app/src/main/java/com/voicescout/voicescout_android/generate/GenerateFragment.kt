package com.voicescout.voicescout_android.generate

import android.content.Intent
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.voicescout.voicescout_android.R
import com.voicescout.voicescout_android.result.TTSResultFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

private const val ARG_SELECTED_VOICE = "SELECTED_VOICE"
private const val ARG_VOICE_ID = "VOICE_ID"

class GenerateFragment : Fragment() {
    private var selectedVoice: String? = null
    private var voiceId: Int = -1
    private var savedAudioFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            selectedVoice = it.getString(ARG_SELECTED_VOICE)
            voiceId = it.getInt(ARG_VOICE_ID, -1)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_generate, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val title = view.findViewById<TextView>(R.id.text_generate_guide)
        val editText = view.findViewById<EditText>(R.id.edit_comment)
        val btnGenerate = view.findViewById<Button>(R.id.btn_generate)

        val selectedVoiceStr = selectedVoice ?: ""
        val fullText = "${selectedVoiceStr}로\n다음 문장의 발화를 생성합니다."

        val spannableString = SpannableString(fullText)
        spannableString.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            selectedVoiceStr.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        title.text = spannableString

        btnGenerate.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotBlank()) {
                sendTTSRequest(text, selectedVoiceStr)
            } else {
                Toast.makeText(requireContext(), "문장을 입력해주세요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendTTSRequest(
        text: String,
        voiceName: String,
    ) {
        val context = requireContext()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()

                // JSON 데이터 생성
                val jsonObject = JSONObject().apply {
                    put("exp_name", voiceName)
                    put("text", text)
                    put("text_lang", "ko")
                    put("prompt_text", "깊은 소리로 책벌레는 책장을 빠르게 넘기며 지친 눈을 부비고 생각에 잠겼다.")
                    put("prompt_lang", "all_ko")
                    put("top_k", "15")
                    put("top_p", "0.8")
                    put("temperature", "0.35")
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = jsonObject.toString().toRequestBody(mediaType)

                val request =
                    Request
                        .Builder()
                        .url("http://10.0.2.2:9880/tts")
                        .post(body)
                        .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful && response.body != null) {
                    val audioFile =
                        File(context.filesDir, "${voiceName}_${System.currentTimeMillis()}.wav")

                    Log.d("FileDebug", "응답 성공! 파일 저장 시작")
                    Log.d("FileDebug", "저장할 파일 경로: ${audioFile.absolutePath}")
                    Log.d("FileDebug", "응답 바디 크기: ${response.body!!.contentLength()} bytes")

                    FileOutputStream(audioFile).use { output ->
                        response.body!!.byteStream().copyTo(output)
                    }
                    savedAudioFile = audioFile

                    Log.d("FileDebug", "파일 저장 완료")
                    Log.d("FileDebug", "저장된 파일 크기: ${audioFile.length()} bytes")
                    Log.d("FileDebug", "파일 존재 여부: ${audioFile.exists()}")

                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "합성 성공!", Toast.LENGTH_SHORT).show()
                        parentFragmentManager
                            .beginTransaction()
                            .replace(
                                R.id.generate_frame,
                                TTSResultFragment.newInstance(audioFile.absolutePath, voiceName)
                            )
                            .addToBackStack(null)
                            .commit()
                    }
                } else {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "합성 실패: ${response.code}", Toast.LENGTH_SHORT)
                            .show()
                        Log.d("abc", response.body.toString())
                        Log.d("abc", response.message)

                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "요청 오류 발생", Toast.LENGTH_SHORT).show()
                    Log.v("abc", e.toString())
                }
            }
        }
    }

    private fun playAudio(file: File) {
        val player = MediaPlayer()
        try {
            player.setDataSource(file.absolutePath)
            player.prepare()
            player.start()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "재생 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun offerShareIntent(file: File) {
        val context = requireContext()
        val uri: Uri =
            FileProvider.getUriForFile(
                context,
                context.applicationContext.packageName + ".fileprovider",
                file,
            )

        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "audio/wav"
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        startActivity(Intent.createChooser(intent, "합성 음성 전송"))
    }

    companion object {
        @JvmStatic
        fun newInstance(selectedVoice: String, voiceId: Int) =
            GenerateFragment().apply {
                arguments =
                    Bundle().apply {
                        putString(ARG_SELECTED_VOICE, selectedVoice)
                        putInt(ARG_VOICE_ID, voiceId)
                    }
            }
    }
}
