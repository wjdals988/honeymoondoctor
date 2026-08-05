package com.jeongmin.honeymoondoctor.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private const val SECRET_BYTE_LENGTH = 20 // 160비트 — 초대코드 추측을 사실상 불가능하게 하기에 충분한 엔트로피
private const val DELIMITER = ":"

/**
 * 초대코드 = "{tripId}:{난수 비밀값}". 초대코드 원문은 절대 Firestore에 저장하지 않고,
 * SHA-256 해시만 trips/{tripId}.inviteCodeHash 에 저장한다(스펙 7-1 초대 보안 요구사항).
 */
object InviteCode {
    private val secureRandom = SecureRandom()

    fun generate(tripId: String): String {
        require(!tripId.contains(DELIMITER)) { "tripId에는 '$DELIMITER' 문자를 포함할 수 없습니다: $tripId" }
        val secretBytes = ByteArray(SECRET_BYTE_LENGTH).also(secureRandom::nextBytes)
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes)
        return "$tripId$DELIMITER$secret"
    }

    fun sha256Hex(inviteCode: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(inviteCode.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** 초대코드 원문에서 여행 ID를 추출한다. 형식이 올바르지 않으면 null을 반환한다. */
    fun extractTripId(inviteCode: String): String? {
        val index = inviteCode.indexOf(DELIMITER)
        if (index <= 0) return null
        return inviteCode.substring(0, index)
    }
}
