package com.jeongmin.honeymoondoctor.domain.model

/** 결정함 카테고리. 시드는 숙소·이동만 쓰지만 자유 입력을 위해 기타를 둔다. */
enum class DecisionCategory(val labelKo: String) {
    LODGING("숙소"),
    TRANSPORT("이동"),
    ACTIVITY("액티비티"),
    ETC("기타"),
}

enum class DecisionStatus(val labelKo: String) {
    NEEDS_DECISION("결정 필요"),
    NEEDS_BOOKING("예약 필요"),
    DECIDED("결정 완료"),
}

data class DecisionOption(
    val id: String,
    val label: String,
)

data class Decision(
    val id: String,
    val title: String,
    val category: DecisionCategory,
    val status: DecisionStatus,
    val options: List<DecisionOption> = emptyList(),
    val selectedOptionId: String? = null,
    val dueAt: java.time.Instant? = null,
    val notes: String? = null,
)
