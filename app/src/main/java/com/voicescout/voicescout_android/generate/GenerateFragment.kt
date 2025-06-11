package com.voicescout.voicescout_android.generate

import android.content.Intent
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
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
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

private const val ARG_SELECTED_VOICE = "SELECTED_VOICE"

class GenerateFragment : Fragment() {
    private var selectedVoice: String? = null
    private var savedAudioFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            selectedVoice = it.getString(ARG_SELECTED_VOICE)
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
                val body =
                    MultipartBody
                        .Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("text", text)
                        .addFormDataPart("top_k", "5")
                        .addFormDataPart("top_p", "1")
                        .addFormDataPart("temperature", "0.35")
                        .addFormDataPart("seed", "1")
                        .build()

                val request =
                    Request
                        .Builder()
                        .url("https://bd94bc49-87cb-4c12-a711-c6a9284aa21b.mock.pstmn.io/tts/")
                        .post(body)
                        .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful && response.body != null) {
                    val audioFile = File(context.filesDir, "${voiceName}_${System.currentTimeMillis()}.wav")
                    FileOutputStream(audioFile).use { output ->
                        response.body!!.byteStream().copyTo(output)
                    }
                    savedAudioFile = audioFile

                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "합성 성공!", Toast.LENGTH_SHORT).show()
                        parentFragmentManager
                            .beginTransaction()
                            .replace(R.id.generate_frame, TTSResultFragment.newInstance(audioFile.absolutePath, voiceName))
                            .addToBackStack(null)
                            .commit()
                    }
                } else {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "합성 실패: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "요청 오류 발생", Toast.LENGTH_SHORT).show()
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
        fun newInstance(selectedVoice: String) =
            GenerateFragment().apply {
                arguments =
                    Bundle().apply {
                        putString(ARG_SELECTED_VOICE, selectedVoice)
                    }
            }
    }
}
