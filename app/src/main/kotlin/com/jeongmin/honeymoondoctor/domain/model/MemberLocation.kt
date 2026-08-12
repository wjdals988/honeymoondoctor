package com.jeongmin.honeymoondoctor.domain.model

import java.time.Instant

/**
 * 구성원이 **명시적으로 버튼을 눌러** 공유한 마지막 위치 1건.
 *
 * 상시 추적이 아니라 스냅샷인 이유:
 * - 백그라운드 위치 권한은 배터리를 먹고 스토어 심사도 까다로우며, 무엇보다
 *   개인정보처리방침의 "위치는 명시적 동작으로만 전송" 원칙과 상시 추적은 양립하지 않는다.
 * - 여행 중 실제로 필요한 질문은 "너 지금 어디쯤이야?"이고, 이건 마지막으로 공유한
 *   위치 + 언제 공유했는지로 충분히 답이 된다.
 *
 * 문서 id = uid. 한 사람당 위치 기록이 정확히 1건이라(덮어쓰기) 이동 경로 이력이
 * 서버에 쌓이지 않는다 — 최소 수집 원칙.
 */
data class MemberLocation(
    val uid: String,
    val latitude: Double,
    val longitude: Double,
    /** 공유한 시각. 오래된 위치를 현재 위치처럼 읽지 않도록 화면에 반드시 함께 표시한다. */
    val sharedAt: Instant,
)
