package com.voicescout.voicescout_android.record

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.textfield.TextInputEditText
import com.voicescout.voicescout_android.ApiConstants
import com.voicescout.voicescout_android.R
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.Executors

class RecordActivity :
    AppCompatActivity(),
    RecordPageFragment.OnRecordButtonClickListener {
    private val REQUEST_PERMISSIONS = 1001
    private val PERMISSIONS =
        arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE, // Android 10 (API 29) 미만 또는 requestLegacyExternalStorage 사용 시
        )

    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicatorTextView: TextView
    private lateinit var sentences: Array<String>

    private var mediaRecorder: MediaRecorder? = null

    // 각 녹음의 임시 파일 경로를 저장할 리스트
    private val recordedTempFilePaths = mutableListOf<String>()
    private var isRecording = false

    private var currentRecordingPosition: Int = -1 // 현재 녹음 중인 문장의 인덱스
    private var isAutoMovingPage: Boolean = false // 자동 페이지 이동 중인지 확인하는 플래그

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val executor = Executors.newSingleThreadExecutor()
    
    private lateinit var audioManager: AudioManager
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d("RecordActivity", "★ 오디오 포커스 변경: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d("RecordActivity", "★ 오디오 포커스 완전 상실 - 녹음 중단")
                if (isRecording) {
                    stopRecording()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d("RecordActivity", "★ 오디오 포커스 일시적 상실 - 녹음 중단")
                if (isRecording) {
                    stopRecording()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d("RecordActivity", "★ 오디오 포커스 복구")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record)

        viewPager = findViewById(R.id.viewPager)
        pageIndicatorTextView = findViewById(R.id.pageIndicatorTextView)
        sentences = resources.getStringArray(R.array.guide_sentences)
        
        // AudioManager 초기화
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        if (!checkPermissions()) {
            requestPermissions()
        } else {
            setupViewPager()
        }
    }

    private fun checkPermissions(): Boolean {
        val recordAudioGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        val writeStorageGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                true // Android 10 이상에서는 WRITE_EXTERNAL_STORAGE가 일반적으로 필요 없음 (Scoped Storage 사용 권장)
            } else {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        return recordAudioGranted && writeStorageGranted
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }
            if (allGranted) {
                setupViewPager()
            } else {
                Toast.makeText(this, R.string.permission_denied_message, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun setupViewPager() {
        val adapter = RecordPagerAdapter(this, sentences)
        viewPager.adapter = adapter

        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    updatePageIndicator(position)

                    // 자동 페이지 이동 중이면 녹음 취소 로직을 건너뜀
                    if (isAutoMovingPage) {
                        isAutoMovingPage = false // 플래그 리셋
                        Log.d("RecordActivity", "자동 페이지 이동 완료 - 페이지: $position")
                        return
                    }

                    // 사용자가 수동으로 페이지를 변경한 경우에만 녹음 중지
                    // 현재 녹음 중이고, 녹음 중인 페이지와 다른 페이지로 이동한 경우
                    if (isRecording && currentRecordingPosition != -1 && currentRecordingPosition != position) {
                        Log.d(
                            "RecordActivity",
                            "★ 수동 페이지 변경으로 인한 녹음 취소 - 이전: $currentRecordingPosition, 현재: $position"
                        )

                        // 현재 녹음 중인 파일을 찾아서 삭제
                        val cancelledFilePath = recordedTempFilePaths.find { path ->
                            path.endsWith("voice$currentRecordingPosition.mp4")
                        }

                        if (cancelledFilePath != null) {
                            deleteTempRecordingFile(cancelledFilePath)
                            recordedTempFilePaths.remove(cancelledFilePath)
                            Log.d("RecordActivity", "취소된 녹음 파일 삭제: $cancelledFilePath")
                        }

                        Toast.makeText(
                            this@RecordActivity,
                            "페이지가 변경되어 이전 녹음이 취소되었습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                        releaseMediaRecorder() // MediaRecorder 해제
                    }
                }
            },
        )

        updatePageIndicator(viewPager.currentItem)
    }

    private fun updatePageIndicator(position: Int) {
        val indicatorText = "${position + 1} / ${sentences.size}"
        pageIndicatorTextView.text = indicatorText
    }

    override fun onPause() {
        super.onPause()
        Log.d("RecordActivity", "★ onPause 호출됨 - 녹음 중: $isRecording")
        if (isRecording) {
            Log.d("RecordActivity", "★ onPause에서 녹음 중단")
            stopRecording()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("RecordActivity", "onResume 호출됨")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("RecordActivity", "onDestroy 호출됨")
        releaseMediaRecorder()
        executor.shutdown()
        // 앱 종료 시 아직 저장되지 않은 임시 파일들 삭제
        recordedTempFilePaths.forEach { filePath ->
            deleteTempRecordingFile(filePath)
        }
    }

    // RecordPageFragment.OnRecordButtonClickListener 인터페이스 구현
    override fun onRecordButtonClick(position: Int) {
        Log.d("RecordActivity", "onRecordButtonClick - position: $position, isRecording: $isRecording, currentRecordingPosition: $currentRecordingPosition")
        
        // 현재 페이지와 다른 위치에서 녹음 중인 경우에만 체크
        if (isRecording && currentRecordingPosition != position && currentRecordingPosition != -1) {
            // 현재 페이지가 실제로 녹음 중인 페이지와 다른 경우만 경고
            val currentPage = viewPager.currentItem
            if (currentPage != currentRecordingPosition) {
                Log.d("RecordActivity", "다른 문장 녹음 중이므로 현재 요청 무시")
                Toast.makeText(this, "다른 문장 녹음을 먼저 완료해주세요.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (isRecording) {
            Log.d("RecordActivity", "녹음 중지 요청 - 버튼 클릭으로 인한 중지")
            stopRecording() // 녹음 중지
        } else {
            Log.d("RecordActivity", "녹음 시작 요청")
            startRecording(position) // 녹음 시작
        }
    }

    override fun onNextButtonClick(position: Int) {
        // 다음 페이지로 이동
        val nextPage = position + 1
        if (nextPage < sentences.size) {
            isAutoMovingPage = true
            viewPager.currentItem = nextPage
        } else {
            // 마지막 페이지인 경우 저장 다이얼로그 표시
            showFileNameInputDialog()
        }
    }

    private fun startRecording(position: Int) {
        // MediaRecorder가 이미 사용 중인지 확인
        if (mediaRecorder != null) {
            Log.w("RecordActivity", "이미 녹음이 진행 중입니다.")
            return
        }

        // 임시 파일 경로 생성
        val tempDir = File(cacheDir, "temp_audio")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }

        val tempFileName = "voice$position.mp4"
        val tempFilePath = File(tempDir, tempFileName).absolutePath

        // 해당 위치에 이미 녹음된 파일이 있다면 기존 파일 삭제
        val existingFileIndex = recordedTempFilePaths.indexOfFirst { path ->
            path.endsWith("voice$position.mp4")
        }
        if (existingFileIndex != -1) {
            val existingFilePath = recordedTempFilePaths[existingFileIndex]
            deleteTempRecordingFile(existingFilePath)
            recordedTempFilePaths.removeAt(existingFileIndex)
            Log.d("RecordActivity", "기존 임시 파일 삭제: $existingFilePath")
        }

        // 실제 파일도 삭제 (혹시 남아있을 수 있음)
        val tempFile = File(tempFilePath)
        if (tempFile.exists()) {
            tempFile.delete()
            Log.d("RecordActivity", "기존 임시 파일 직접 삭제: $tempFilePath")
        }

        try {
            Log.d("RecordActivity", "녹음 시작 - 임시 파일 경로: $tempFilePath")

            // 오디오 포커스 요청
            val result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w("RecordActivity", "오디오 포커스 요청 실패")
                Toast.makeText(this, "오디오 포커스를 얻을 수 없습니다", Toast.LENGTH_SHORT).show()
                return
            }
            
            Log.d("RecordActivity", "오디오 포커스 획득 성공")

            mediaRecorder = MediaRecorder().apply {
                // 에러 리스너 설정으로 예기치 않은 녹음 중단 감지
                setOnErrorListener { mr, what, extra ->
                    Log.e("RecordActivity", "★ MediaRecorder 에러 발생! what: $what, extra: $extra")
                    runOnUiThread {
                        Toast.makeText(this@RecordActivity, "녹음 에러 발생: $what", Toast.LENGTH_SHORT).show()
                        stopRecording()
                    }
                }

                setOnInfoListener { mr, what, extra ->
                    Log.d("RecordActivity", "★ MediaRecorder 정보: what: $what, extra: $extra")
                }

                try {
                    // 음성 인식에 최적화된 오디오 소스 사용
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

                    // 고품질 음성 녹음을 위한 설정
                    setAudioSamplingRate(48000) // 48kHz 샘플링 레이트
                    setAudioEncodingBitRate(128000) // 128kbps 비트레이트
                    setAudioChannels(1) // 모노 채널 (음성용)

                    setOutputFile(tempFilePath)

                    Log.d("RecordActivity", "고품질 설정 적용: 48kHz, 128kbps, 모노")
                } catch (e: Exception) {
                    Log.w("RecordActivity", "고품질 설정 실패, 기본 설정으로 fallback", e)
                    // 고품질 설정 실패 시 기본 설정으로 재시도
                    reset()
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(44100) // 표준 CD 품질
                    setAudioEncodingBitRate(64000) // 음성용 적절한 비트레이트
                    setAudioChannels(1) // 모노
                    setOutputFile(tempFilePath)

                    Log.d("RecordActivity", "기본 품질 설정 적용: 44.1kHz, 64kbps, 모노")
                }

                prepare()
                start()
            }

            // 성공한 경우에만 상태 업데이트
            isRecording = true
            currentRecordingPosition = position
            recordedTempFilePaths.add(tempFilePath)

            Log.d(
                "RecordActivity",
                "녹음 시작 성공 - 현재 임시 파일 수: ${recordedTempFilePaths.size}"
            )

            // 현재 활성화된 프래그먼트 찾기 및 UI 업데이트
            updateCurrentFragmentUI(true)
        } catch (e: Exception) {
            Log.e("RecordActivity", "녹음 시작 실패", e)
            Toast.makeText(this, "녹음 시작 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            releaseMediaRecorder()
            deleteTempRecordingFile(tempFilePath)
        }
    }

    private fun stopRecording() {
        if (!isRecording || mediaRecorder == null) {
            Log.w("RecordActivity", "녹음이 진행 중이지 않습니다.")
            return
        }

        Log.d("RecordActivity", "stopRecording 호출됨 - 현재 녹음 위치: $currentRecordingPosition")
        val stackTrace = Thread.currentThread().stackTrace
        Log.d("RecordActivity", "stopRecording 호출 스택: ${stackTrace.take(5).joinToString { it.toString() }}")

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            // 현재 녹음된 파일 확인
            val currentTempFile = recordedTempFilePaths.lastOrNull()
            Log.d("RecordActivity", "녹음 중지 - 마지막 임시 파일: $currentTempFile")
            currentTempFile?.let { path ->
                val file = File(path)
                Log.d(
                    "RecordActivity",
                    "임시 파일 존재: ${file.exists()}, 크기: ${file.length()}"
                )
            }

            // UI 업데이트 (currentRecordingPosition 리셋 전에 수행)
            updateCurrentFragmentUI(false)
            currentRecordingPosition = -1

            // 현재 프래그먼트에 녹음 완료 상태 설정
            val currentPage = viewPager.currentItem
            val fragmentTag = "f$currentPage"
            val currentFragment =
                supportFragmentManager.findFragmentByTag(fragmentTag) as? RecordPageFragment
            currentFragment?.setRecordedState(true)

            // 오디오 포커스 해제
            audioManager.abandonAudioFocus(audioFocusChangeListener)
            Log.d("RecordActivity", "오디오 포커스 해제됨")

            // 모든 문장을 다 녹음했는지 확인
            if (recordedTempFilePaths.size == sentences.size) {
                Log.d(
                    "RecordActivity",
                    "모든 녹음 완료 - 총 ${recordedTempFilePaths.size}개 파일"
                )
                showFileNameInputDialog() // 모든 녹음 완료 시 파일명 입력 다이얼로그 표시
            }
        } catch (stopException: RuntimeException) {
            Toast.makeText(this, "녹음 중지 오류: ${stopException.message}", Toast.LENGTH_SHORT).show()
            // 오류 발생 시 해당 임시 파일 삭제 및 리스트에서 제거
            val failedFilePath = recordedTempFilePaths.lastOrNull()
            deleteTempRecordingFile(failedFilePath)
            if (failedFilePath != null) recordedTempFilePaths.remove(failedFilePath)
            releaseMediaRecorder()
        }
    }

    private fun releaseMediaRecorder() {
        // 이전 녹음 중이던 프래그먼트의 UI 업데이트
        if (currentRecordingPosition != -1) {
            updateFragmentUI(currentRecordingPosition, false)
        }

        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
        currentRecordingPosition = -1

        Log.d("RecordActivity", "MediaRecorder 해제 및 상태 리셋 완료")
    }

    // 현재 활성화된 프래그먼트의 UI를 업데이트하는 메서드
    private fun updateCurrentFragmentUI(isRecording: Boolean) {
        val currentPosition = viewPager.currentItem
        updateFragmentUI(currentPosition, isRecording)
    }

    // 특정 위치의 프래그먼트 UI를 업데이트하는 메서드
    private fun updateFragmentUI(position: Int, isRecording: Boolean) {
        val fragmentTag = "f$position"
        val fragment = supportFragmentManager.findFragmentByTag(fragmentTag) as? RecordPageFragment
        fragment?.updateRecordButtonState(isRecording)

        // 디버깅을 위한 로그
        if (fragment == null) {
            Log.w(
                "RecordActivity",
                "Fragment not found for position $position with tag $fragmentTag"
            )
        } else {
            Log.d("RecordActivity", "UI 업데이트 - 위치: $position, 녹음중: $isRecording")
        }
    }

    // 임시 파일 삭제 유틸리티 함수
    private fun deleteTempRecordingFile(filePath: String?) {
        filePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    // 녹음 세트의 최종 파일명을 입력받는 다이얼로그 표시
    private fun showFileNameInputDialog() {
        val inputEditText =
            TextInputEditText(this).apply {
                hint = "녹음 세트의 이름 (예: 화자_날짜)"
                setSingleLine(true)
            }

        AlertDialog
            .Builder(this)
            .setTitle("녹음 세트 저장")
            .setMessage("모든 녹음이 완료되었습니다. 저장할 이름을 입력해주세요.")
            .setView(inputEditText)
            .setPositiveButton("저장") { dialog, _ ->
                val enteredFileName = inputEditText.text.toString().trim()
                if (enteredFileName.isNotEmpty()) {
                    saveAllRecordings(enteredFileName)
                } else {
                    Toast.makeText(this, "파일명을 입력해주세요.", Toast.LENGTH_SHORT).show()
                    showFileNameInputDialog() // 다시 다이얼로그 띄우기
                }
                dialog.dismiss()
            }.setNegativeButton("취소") { dialog, _ ->
                // 취소 시 모든 임시 파일 삭제
                recordedTempFilePaths.forEach { filePath ->
                    deleteTempRecordingFile(filePath)
                }
                recordedTempFilePaths.clear()
                Toast.makeText(this, "녹음 저장이 취소되었습니다.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                finish() // 녹음 액티비티 종료 또는 초기 화면으로 돌아가기
            }.setCancelable(false) // 백 버튼으로 닫히지 않도록
            .show()
    }

    // 모든 임시 녹음 파일을 최종 폴더로 이동/복사하고 이름 변경
    private fun saveAllRecordings(sessionName: String) {
        Log.d("RecordActivity", "★ 파일 저장 시작 - 세션명: $sessionName, 임시파일 수: ${recordedTempFilePaths.size}")
        
        // 최종 저장될 디렉토리 (예: /Android/data/com.your.package.name/files/AudioSessions/입력값/)
        val finalSaveDir =
            File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "AudioSessions/$sessionName")
        Log.d("RecordActivity", "★ 최종 저장 디렉토리: ${finalSaveDir.absolutePath}")
        
        if (!finalSaveDir.exists()) {
            Log.d("RecordActivity", "★ 디렉토리가 존재하지 않아 생성 시도")
            val created = finalSaveDir.mkdirs()
            Log.d("RecordActivity", "★ 디렉토리 생성 결과: $created")
        }

        if (!finalSaveDir.exists()) {
            Log.e("RecordActivity", "★ 저장 폴더 생성 실패: ${finalSaveDir.absolutePath}")
            Toast.makeText(this, "저장 폴더 생성 실패: ${finalSaveDir.absolutePath}", Toast.LENGTH_LONG)
                .show()
            // 폴더 생성 실패 시 임시 파일 삭제
            recordedTempFilePaths.forEach { filePath ->
                deleteTempRecordingFile(filePath)
            }
            recordedTempFilePaths.clear()
            return
        }
        
        // 디렉토리 권한 확인
        if (!finalSaveDir.canWrite()) {
            Log.e("RecordActivity", "★ 저장 폴더 쓰기 권한 없음: ${finalSaveDir.absolutePath}")
            Toast.makeText(this, "저장 폴더에 쓰기 권한이 없습니다", Toast.LENGTH_LONG).show()
            return
        }

        var successCount = 0
        val savedFiles = mutableListOf<File>()

        recordedTempFilePaths.forEachIndexed { index, tempPath ->
            val tempFile = File(tempPath)
            Log.d("RecordActivity", "★ 처리 중인 임시 파일: $tempPath")
            Log.d("RecordActivity", "★ 임시파일 존재: ${tempFile.exists()}, 크기: ${if (tempFile.exists()) tempFile.length() else 0}")

            if (tempFile.exists()) {
                // 파일이 비어있는지 확인
                if (tempFile.length() == 0L) {
                    Log.w("RecordActivity", "★ 임시 파일이 비어있음: $tempPath")
                    Toast.makeText(this, "빈 녹음 파일 발견: ${tempFile.name}", Toast.LENGTH_SHORT).show()
                    return@forEachIndexed
                }
                
                // 파일명을 voice{position}으로 찾기
                val voicePosition =
                    tempFile.name.removePrefix("voice").removeSuffix(".mp4").toIntOrNull() ?: index
                val newFileName = "voice$voicePosition.mp4"
                val finalFile = File(finalSaveDir, newFileName)

                Log.d("RecordActivity", "★ 최종 파일 경로: ${finalFile.absolutePath}")

                // 기존 최종 파일이 있다면 삭제
                if (finalFile.exists()) {
                    val deleted = finalFile.delete()
                    Log.d("RecordActivity", "★ 기존 최종 파일 삭제: ${finalFile.absolutePath}, 성공: $deleted")
                }

                try {
                    // 먼저 부모 디렉토리가 존재하는지 확인
                    if (!finalFile.parentFile?.exists()!!) {
                        Log.w("RecordActivity", "★ 부모 디렉토리가 존재하지 않음, 생성 시도")
                        finalFile.parentFile?.mkdirs()
                    }
                    
                    // renameTo는 실패할 수 있으므로 반환값 확인
                    Log.d("RecordActivity", "★ renameTo 시도: ${tempFile.absolutePath} → ${finalFile.absolutePath}")
                    val success = tempFile.renameTo(finalFile)
                    Log.d("RecordActivity", "★ renameTo 결과: $success, 최종파일 존재: ${finalFile.exists()}")
                    
                    if (success && finalFile.exists()) {
                        val finalSize = finalFile.length()
                        Log.d("RecordActivity", "★ 파일 이동 성공: ${finalFile.absolutePath}, 크기: $finalSize")
                        if (finalSize > 0) {
                            savedFiles.add(finalFile)
                            successCount++
                        } else {
                            Log.w("RecordActivity", "★ 이동된 파일이 비어있음")
                        }
                    } else {
                        // renameTo 실패 시 복사 방식으로 재시도
                        Log.w("RecordActivity", "★ renameTo 실패, 복사 방식으로 재시도")
                        Log.d("RecordActivity", "★ 복사 시도: ${tempFile.absolutePath} → ${finalFile.absolutePath}")
                        
                        tempFile.copyTo(finalFile, overwrite = true)
                        val finalExists = finalFile.exists()
                        val finalSize = if (finalExists) finalFile.length() else 0
                        
                        Log.d("RecordActivity", "★ 복사 완료: 존재=$finalExists, 크기=$finalSize")
                        
                        if (finalExists && finalSize > 0) {
                            val tempDeleted = tempFile.delete() // 복사 성공 시 원본 삭제
                            Log.d("RecordActivity", "★ 원본 임시파일 삭제: $tempDeleted")
                            savedFiles.add(finalFile)
                            successCount++
                            Log.d("RecordActivity", "★ 파일 복사 성공: ${finalFile.absolutePath}")
                        } else {
                            Log.e("RecordActivity", "★ 파일 복사 실패: ${finalFile.absolutePath}")
                            throw Exception("파일 이동 및 복사 모두 실패")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RecordActivity", "★ 파일 저장 실패: ${tempFile.name}", e)
                    Toast.makeText(
                        this,
                        "파일 저장 실패: ${tempFile.name} - ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Log.e("RecordActivity", "★ 임시 파일이 존재하지 않음: $tempPath")
                Toast.makeText(this, "임시 파일이 존재하지 않음: ${File(tempPath).name}", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        Log.d("RecordActivity", "★ 파일 저장 완료 - 성공: $successCount, 전체: ${sentences.size}")
        
        if (successCount == sentences.size) {
            Log.d("RecordActivity", "★ 모든 파일 저장 성공, 업로드 시작")
            uploadVoiceFiles(sessionName, savedFiles)
        } else {
            Log.w("RecordActivity", "★ 일부 파일 저장 실패 - 성공: $successCount, 전체: ${sentences.size}")
            Toast.makeText(
                this,
                "일부 녹음 파일 저장에 실패했습니다. ($successCount/${sentences.size})",
                Toast.LENGTH_LONG
            ).show()
        }

        recordedTempFilePaths.clear() // 저장 완료 후 리스트 비우기
    }

    private fun uploadVoiceFiles(sessionName: String, files: List<File>) {
        // 업로드 진행 다이얼로그 표시
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("파일 업로드 중")
            .setMessage("음성 파일을 서버로 업로드하고 있습니다...\n잠시만 기다려주세요.")
            .setCancelable(false)
            .create()
        
        progressDialog.show()
        Log.d("RecordActivity", "★ 업로드 진행 다이얼로그 표시")
        
        executor.execute {
            try {
                val requestBodyBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("exp_name", sessionName)

                // 각 파일을 files 필드에 추가
                files.forEach { file ->
                    Log.d(
                        "RecordActivity",
                        "업로드할 파일: ${file.absolutePath}, 존재: ${file.exists()}, 크기: ${file.length()}"
                    )

                    if (!file.exists()) {
                        throw Exception("업로드할 파일이 존재하지 않음: ${file.absolutePath}")
                    }

                    val fileBody = file.asRequestBody("audio/mp4".toMediaType())
                    requestBodyBuilder.addFormDataPart("files", file.name, fileBody)
                }

                val requestBody = requestBodyBuilder.build()

                val request = Request.Builder()
                    .url(ApiConstants.UPLOAD_VOICE_URL)
                    .post(requestBody)
                    .build()

                Log.d("RecordActivity", "★ HTTP 요청 시작 - URL: ${ApiConstants.UPLOAD_VOICE_URL}")
                val startTime = System.currentTimeMillis()
                
                val response = httpClient.newCall(request).execute()
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                
                Log.d("RecordActivity", "★ HTTP 응답 받음 - 소요시간: ${duration}ms")
                Log.d("RecordActivity", "★ 응답 코드: ${response.code}, 성공여부: ${response.isSuccessful}")
                
                val responseBody = response.body?.string()
                Log.d("RecordActivity", "★ 응답 내용: $responseBody")

                runOnUiThread {
                    progressDialog.dismiss()
                    
                    if (response.isSuccessful) {
                        Log.d("RecordActivity", "★ 업로드 성공!")
                        Toast.makeText(this, "음성 파일 업로드 완료!", Toast.LENGTH_LONG).show()
                    } else {
                        Log.e("RecordActivity", "★ 업로드 실패 - 응답코드: ${response.code}")
                        Toast.makeText(this, "업로드 실패: ${response.code}\n${responseBody?.take(100)}", Toast.LENGTH_LONG).show()
                    }
                    finish() // 녹음 액티비티 종료
                }

            } catch (e: java.net.SocketTimeoutException) {
                Log.e("RecordActivity", "★ 업로드 타임아웃 발생", e)
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this, "업로드 타임아웃\n네트워크 연결을 확인해주세요", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: java.net.ConnectException) {
                Log.e("RecordActivity", "★ 서버 연결 실패", e)
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this, "서버 연결 실패\n인터넷 연결을 확인해주세요", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e("RecordActivity", "★ 업로드 중 예외 발생", e)
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this, "업로드 중 오류 발생: ${e.javaClass.simpleName}\n${e.message?.take(50)}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }
}
