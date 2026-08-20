package com.jeongmin.honeymoondoctor.core.security

import android.net.Uri
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private const val SECRET_BYTE_LENGTH = 20 // 160비트 — 초대코드 추측을 사실상 불가능하게 하기에 충분한 엔트로피
private const val DELIMITER = ":"
private const val LINK_SCHEME = "honeymoondoctor"
private const val LINK_HOST = "join"

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

    /**
     * 초대코드를 손으로 옮겨 적어야 했던 문제(백로그)를 없애기 위한 딥링크.
     * 카카오톡 등으로 이 링크를 보내면 앱이 설치돼 있을 때 탭 한 번으로 참여 화면에
     * 코드가 채워진다 — [AndroidManifest]의 honeymoondoctor://join 인텐트 필터가 받는다.
     */
    fun buildJoinLink(inviteCode: String): String =
        "$LINK_SCHEME://$LINK_HOST?code=${Uri.encode(inviteCode)}"

    /** 딥링크 URI에서 code 파라미터를 그대로 꺼낸다(형식 검증은 [extractTripId]가 한다). */
    fun extractFromLink(uri: Uri): String? =
        uri.takeIf { it.host == LINK_HOST }?.getQueryParameter("code")
}
