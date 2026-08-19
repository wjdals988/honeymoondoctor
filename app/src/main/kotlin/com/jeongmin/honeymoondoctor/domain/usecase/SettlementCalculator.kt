package com.jeongmin.honeymoondoctor.domain.usecase

import com.jeongmin.honeymoondoctor.domain.model.Expense

/**
 * 공동 지출의 "누가 누구에게 얼마"를 계산한다(스펙 7-6).
 *
 * 종전에는 `공동지출 합계 / 2`만 보여줬는데, 그 값은 결제자를 보지 않아 실제로 주고받을
 * 금액이 아니었다 — 한 사람이 다 냈든 반씩 냈든 같은 숫자가 나왔다. 결제자(paidByUid)는
 * 이미 입력·저장되고 있으므로 그대로 쓴다.
 *
 * **2인 여행 전용이다**(Firestore 규칙의 `memberIds.size() <= 2`와 같은 전제). 인원 제한을
 * 푼다면(백로그 2-5) 이 계산도 다자간 정산으로 다시 짜야 한다.
 *
 * 결제자가 비어 있는 지출은 누구 몫인지 판정할 수 없어 **정산에서 제외**하고 그 건수를
 * [SettlementResult.unattributedCount]로 돌려준다 — 조용히 빼면 합이 안 맞는 이유를
 * 사용자가 알 수 없다.
 */
object SettlementCalculator {

    /** [fromUid]가 [toUid]에게 [amountKrw]원을 줘야 한다. */
    data class Settlement(val fromUid: String, val toUid: String, val amountKrw: Long)

    data class SettlementResult(
        /** null이면 주고받을 금액이 없다(대상 0건이거나 정확히 균형). */
        val settlement: Settlement? = null,
        /** 정산 대상이 된 공동 지출 합계(결제자 미입력 건 제외). */
        val settledTotalKrw: Long = 0,
        /** 결제자가 없어 정산에서 빠진 공동 지출 건수. */
        val unattributedCount: Int = 0,
    )

    fun compute(expenses: List<Expense>, memberUids: List<String>): SettlementResult {
        val shared = expenses.filter { it.shared }
        val unattributed = shared.count { it.paidByUid == null }
        // 구성원이 2명이 아니면(혼자이거나 아직 합류 전) 주고받을 상대가 없다.
        if (memberUids.size != 2) {
            return SettlementResult(unattributedCount = unattributed)
        }
        val attributed = shared.filter { it.paidByUid in memberUids }
        val total = attributed.sumOf { it.amountKrw }
        if (total == 0L) return SettlementResult(unattributedCount = unattributed)

        val (first, second) = memberUids
        val paidByFirst = attributed.filter { it.paidByUid == first }.sumOf { it.amountKrw }
        // 반올림 방향을 한쪽으로 고정하지 않도록, 낸 금액 차이의 절반을 그대로 쓴다.
        // (총액의 1/2을 각자 부담 → 더 낸 사람이 차액의 절반을 돌려받는다)
        val diff = paidByFirst * 2 - total
        val settlement = when {
            diff > 0 -> Settlement(fromUid = second, toUid = first, amountKrw = diff / 2)
            diff < 0 -> Settlement(fromUid = first, toUid = second, amountKrw = -diff / 2)
            else -> null
        }
        return SettlementResult(
            settlement = settlement?.takeIf { it.amountKrw > 0 },
            settledTotalKrw = total,
            unattributedCount = unattributed,
        )
    }
}
