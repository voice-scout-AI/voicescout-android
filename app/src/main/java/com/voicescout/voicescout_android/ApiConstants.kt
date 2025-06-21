package com.voicescout.voicescout_android

/**
 * API 관련 상수들을 관리하는 클래스
 */
object ApiConstants {
    /**
     * 기본 서버 URL
     * 안드로이드 에뮬레이터에서 로컬호스트는 10.0.2.2로 접근
     * 실제 디바이스에서 테스트 시에는 실제 IP 주소로 변경 필요
     */
    private const val BASE_URL = "https://ultimate-explicitly-fawn.ngrok-free.app"
    
    /**
     * API 엔드포인트들
     */
    const val UPLOAD_VOICE_URL = "$BASE_URL/upload/voice"
    const val TTS_URL = "$BASE_URL/tts"
    const val USERS_URL = "$BASE_URL/users"
    
    /**
     * 특정 사용자 삭제를 위한 URL 생성
     */
    fun getUserDeleteUrl(userId: Int): String = "$USERS_URL/$userId"
} 