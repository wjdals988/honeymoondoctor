package com.jeongmin.honeymoondoctor.domain.model

import java.time.Instant

/**
 * 쪽지 — "보내 두면 상대가 앱을 열 때 확인하는" 비동기 메모(삐삐 모델).
 *
 * 이 앱에는 서버 발송 푸시(FCM)가 없다(Cloud Functions가 Blaze 요금제를 요구해 "월 0원"
 * 제약과 충돌). 그래서 즉시 도착을 약속하는 "메시지" 대신, 도착 시점을 약속하지 않는
 * "쪽지"로 이름부터 기대치를 맞춘다. 상대 앱이 켜져 있으면 몇 초 안에 알림이 오고,
 * 꺼져 있으면 다음에 열 때 "읽지 않은 쪽지"로 남는다.
 *
 * [readAt]은 "확인함" 여부만 화면에 노출한다 — 읽은 시각까지 보여주면 "읽고 답 안 함"이
 * 그대로 드러나 2인 관계에서 갈등 요인이 된다(카카오톡 읽음 논쟁과 같은 이유).
 */
data class TripNote(
    val id: String,
    val senderUid: String,
    val text: String,
    val createdAt: Instant,
    /** 상대가 확인한 시각. null이면 아직 읽지 않음. */
    val readAt: Instant? = null,
)
