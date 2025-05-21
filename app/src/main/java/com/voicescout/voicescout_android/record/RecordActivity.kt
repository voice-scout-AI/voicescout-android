package com.voicescout.voicescout_android.record

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.textfield.TextInputEditText
import com.voicescout.voicescout_android.R
import com.voicescout.voicescout_android.record.RecordPageFragment
import com.voicescout.voicescout_android.record.RecordPagerAdapter
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

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

    private var currentFragment: RecordPageFragment? = null
    private var currentRecordingPosition: Int = -1 // 현재 녹음 중인 문장의 인덱스

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record)

        viewPager = findViewById(R.id.viewPager)
        pageIndicatorTextView = findViewById(R.id.pageIndicatorTextView)
        sentences = resources.getStringArray(R.array.guide_sentences)

        if (!checkPermissions()) {
            requestPermissions()
        } else {
            setupViewPager()
        }
    }

    private fun checkPermissions(): Boolean {
        val recordAudioGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val writeStorageGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                true // Android 10 이상에서는 WRITE_EXTERNAL_STORAGE가 일반적으로 필요 없음 (Scoped Storage 사용 권장)
            } else {
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
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
                    // 페이지가 변경되면 녹음 중인 경우 중지
                    if (isRecording) {
                        // 페이지 전환 시에는 자동으로 녹음 중지. 이 경우 파일명 입력 다이얼로그는 띄우지 않음.
                        // 해당 임시 파일은 유효하지 않으므로 삭제.
                        Toast.makeText(this@RecordActivity, "페이지가 변경되어 이전 녹음이 취소되었습니다.", Toast.LENGTH_SHORT).show()
                        deleteTempRecordingFile(recordedTempFilePaths.lastOrNull()) // 마지막으로 녹음 시도했던 파일 삭제
                        releaseMediaRecorder() // MediaRecorder 해제
                    }
                    // 현재 활성화된 프래그먼트 업데이트
                    currentFragment = supportFragmentManager.findFragmentByTag("f${viewPager.currentItem}") as? RecordPageFragment
                }
            },
        )

        updatePageIndicator(viewPager.currentItem)
    }

    private fun updatePageIndicator(position: Int) {
        val indicatorText = "${position + 1} / ${sentences.size}"
        pageIndicatorTextView.text = indicatorText
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaRecorder()
        // 앱 종료 시 아직 저장되지 않은 임시 파일들 삭제
        recordedTempFilePaths.forEach { filePath ->
            deleteTempRecordingFile(filePath)
        }
    }

    // RecordPageFragment.OnRecordButtonClickListener 인터페이스 구현
    override fun onRecordButtonClick(position: Int) {
        currentRecordingPosition = position // 현재 녹음 중인 문장의 인덱스 저장

        if (isRecording) {
            stopRecording() // 녹음 중지
        } else {
            startRecording(position) // 녹음 시작
        }
    }

    private fun startRecording(position: Int) {
        // 임시 파일 경로 생성 (저장될 최종 폴더가 아닌 임시 위치)
        // 앱의 캐시 디렉토리에 저장하여, 앱이 종료되거나 문제가 생길 경우 자동으로 삭제되도록 유도
        val tempDir = File(cacheDir, "temp_audio")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val tempFileName = "TEMP_${timeStamp}_$position.mp4"
        val tempFilePath = File(tempDir, tempFileName).absolutePath
        recordedTempFilePaths.add(tempFilePath) // 임시 파일 경로 리스트에 추가

        mediaRecorder =
            MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(tempFilePath) // 임시 경로에 저장

                try {
                    prepare()
                    start()
                    isRecording = true
                    currentFragment?.updateRecordButtonState(true)
                    Toast.makeText(this@RecordActivity, "녹음 시작: ${sentences[position]}", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this@RecordActivity, "녹음 시작 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    releaseMediaRecorder()
                    deleteTempRecordingFile(tempFilePath) // 실패 시 임시 파일 삭제
                }
            }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            try {
                stop()
                release()
                mediaRecorder = null
                isRecording = false
                currentFragment?.updateRecordButtonState(false)
                Toast.makeText(this@RecordActivity, "녹음 중지: 임시 저장됨", Toast.LENGTH_SHORT).show()

                // 모든 문장을 다 녹음했는지 확인
                if (recordedTempFilePaths.size == sentences.size) {
                    showFileNameInputDialog() // 모든 녹음 완료 시 파일명 입력 다이얼로그 표시
                } else {
                    // 모든 녹음이 완료되지 않았다면 다음 페이지로 자동 이동
                    viewPager.currentItem = viewPager.currentItem + 1
                }
            } catch (stopException: RuntimeException) {
                Toast.makeText(this@RecordActivity, "녹음 중지 오류: ${stopException.message}", Toast.LENGTH_SHORT).show()
                // 오류 발생 시 해당 임시 파일 삭제 및 리스트에서 제거
                val failedFilePath = recordedTempFilePaths.lastOrNull()
                deleteTempRecordingFile(failedFilePath)
                if (failedFilePath != null) recordedTempFilePaths.remove(failedFilePath)
                releaseMediaRecorder()
            }
        }
    }

    private fun releaseMediaRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
        currentFragment?.updateRecordButtonState(false)
    }

    // 임시 파일 삭제 유틸리티 함수
    private fun deleteTempRecordingFile(filePath: String?) {
        filePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
                // Toast.makeText(this, "임시 파일 삭제됨: ${file.name}", Toast.LENGTH_SHORT).show()
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
        // 최종 저장될 디렉토리 (예: /Android/data/com.your.package.name/files/AudioSessions/입력값/)
        val finalSaveDir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "AudioSessions/$sessionName")
        if (!finalSaveDir.exists()) {
            finalSaveDir.mkdirs()
        }

        if (!finalSaveDir.exists()) {
            Toast.makeText(this, "저장 폴더 생성 실패: ${finalSaveDir.absolutePath}", Toast.LENGTH_LONG).show()
            // 폴더 생성 실패 시 임시 파일 삭제
            recordedTempFilePaths.forEach { filePath ->
                deleteTempRecordingFile(filePath)
            }
            recordedTempFilePaths.clear()
            return
        }

        var successCount = 0
        recordedTempFilePaths.forEachIndexed { index, tempPath ->
            val tempFile = File(tempPath)
            if (tempFile.exists()) {
                // 새로운 파일명: 예: 입력값_0.mp4, 입력값_1.mp4 ...
                val newFileName = "${sessionName}_$index.mp4"
                val finalFile = File(finalSaveDir, newFileName)
                try {
                    tempFile.renameTo(finalFile) // 파일 이동 (더 효율적)
                    successCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "파일 저장 실패: ${tempFile.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (successCount == sentences.size) {
            Toast.makeText(this, "모든 녹음이 '$sessionName'으로 저장되었습니다.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "일부 녹음 파일 저장에 실패했습니다. ($successCount/${sentences.size})", Toast.LENGTH_LONG).show()
        }

        recordedTempFilePaths.clear() // 저장 완료 후 리스트 비우기
        finish() // 녹음 액티비티 종료
    }
}
