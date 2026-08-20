package com.jeongmin.honeymoondoctor.domain.model

import java.time.Instant

/** 스펙 7-5의 준비물 카테고리: 서류, 전자기기, 의류, 세면, 건강, 기타 */
enum class ChecklistCategory(val labelKo: String, val emoji: String) {
    DOCUMENT("서류", "📄"),
    ELECTRONICS("전자기기", "🔌"),
    CLOTHING("의류", "👕"),
    TOILETRIES("세면", "🧴"),
    HEALTH("건강", "💊"),
    ETC("기타", "🎒"),
    ;

    /** ExpenseCategory·ReservationType과 같은 이유(스캔 보조). 목록에서 이 형태로 쓴다. */
    val display: String get() = "$emoji $labelKo"
}

/**
 * 준비물 항목. [ownerUid]가 null이면 공용, 아니면 해당 구성원 전용이다.
 * 시드의 ownerScope=SHARED가 ownerUid=null로 매핑된다.
 */
data class ChecklistItem(
    val id: String,
    val title: String,
    val category: ChecklistCategory,
    val ownerUid: String? = null,
    val required: Boolean = false,
    val completed: Boolean = false,
    val completedAt: Instant? = null,
    val dueAt: Instant? = null,
)
