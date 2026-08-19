package com.jeongmin.honeymoondoctor.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.jeongmin.honeymoondoctor.domain.model.Expense
import com.jeongmin.honeymoondoctor.domain.model.ExpenseCategory
import com.jeongmin.honeymoondoctor.domain.model.TravelCurrency
import java.time.Instant
import org.junit.Test

class SettlementCalculatorTest {

    private val a = "uid-a"
    private val b = "uid-b"
    private val members = listOf(a, b)

    private fun expense(amountKrw: Long, paidBy: String?, shared: Boolean = true) = Expense(
        id = "e-$amountKrw-$paidBy-$shared",
        amountMinor = amountKrw,
        currency = TravelCurrency.KRW,
        fxRateToKrw = 1.0,
        amountKrw = amountKrw,
        category = ExpenseCategory.FOOD,
        paidByUid = paidBy,
        shared = shared,
        spentAt = Instant.EPOCH,
    )

    @Test
    fun `한 사람이 전부 냈으면 상대가 절반을 갚는다`() {
        val result = SettlementCalculator.compute(listOf(expense(100_000, a)), members)

        assertThat(result.settlement).isEqualTo(
            SettlementCalculator.Settlement(fromUid = b, toUid = a, amountKrw = 50_000),
        )
        assertThat(result.settledTotalKrw).isEqualTo(100_000)
    }

    @Test
    fun `똑같이 냈으면 주고받을 금액이 없다`() {
        val result = SettlementCalculator.compute(
            listOf(expense(30_000, a), expense(30_000, b)),
            members,
        )

        assertThat(result.settlement).isNull()
        assertThat(result.settledTotalKrw).isEqualTo(60_000)
    }

    @Test
    fun `차액의 절반만 주고받는다`() {
        // A 80,000 + B 20,000 = 100,000 → 각자 50,000 부담 → B가 A에게 30,000
        val result = SettlementCalculator.compute(
            listOf(expense(80_000, a), expense(20_000, b)),
            members,
        )

        assertThat(result.settlement?.fromUid).isEqualTo(b)
        assertThat(result.settlement?.toUid).isEqualTo(a)
        assertThat(result.settlement?.amountKrw).isEqualTo(30_000)
    }

    @Test
    fun `개인 지출은 정산에서 제외한다`() {
        val result = SettlementCalculator.compute(
            listOf(expense(100_000, a), expense(500_000, a, shared = false)),
            members,
        )

        assertThat(result.settledTotalKrw).isEqualTo(100_000)
        assertThat(result.settlement?.amountKrw).isEqualTo(50_000)
    }

    @Test
    fun `결제자가 없는 공동지출은 제외하고 건수를 알려준다`() {
        val result = SettlementCalculator.compute(
            listOf(expense(100_000, a), expense(40_000, null)),
            members,
        )

        assertThat(result.unattributedCount).isEqualTo(1)
        assertThat(result.settledTotalKrw).isEqualTo(100_000)
        assertThat(result.settlement?.amountKrw).isEqualTo(50_000)
    }

    @Test
    fun `구성원이 혼자면 주고받을 상대가 없다`() {
        val result = SettlementCalculator.compute(listOf(expense(100_000, a)), listOf(a))

        assertThat(result.settlement).isNull()
    }

    @Test
    fun `홀수 금액은 내림해 한쪽이 더 받지 않게 한다`() {
        // A가 10,001 전액 부담 → 차액 10,001의 절반 = 5000.5 → 5,000
        val result = SettlementCalculator.compute(listOf(expense(10_001, a)), members)

        assertThat(result.settlement?.amountKrw).isEqualTo(5_000)
    }
}
