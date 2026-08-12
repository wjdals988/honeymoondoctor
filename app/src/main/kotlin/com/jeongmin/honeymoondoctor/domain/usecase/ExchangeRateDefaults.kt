package com.jeongmin.honeymoondoctor.domain.usecase

import com.jeongmin.honeymoondoctor.domain.model.TravelCurrency
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 통화를 바꿨을 때 환율 칸에 넣을 기본값을 정한다.
 *
 * 화면·ViewModel이 아니라 여기 있는 이유: 이 규칙 하나가 틀리면 금액이 조용히 수백 배로
 * 어긋난다. 실제로 그런 버그가 두 개 있었다 —
 *
 * 1. 외화를 골라도 환율 칸이 `"1"`로 남아 €50이 50원으로 저장됐다. 저장 시 검증은
 *    `환율 > 0`이라서 `"1"`을 통과시켰다.
 * 2. EUR 환율(1629.64)을 받아 둔 뒤 통화를 JPY로 바꾸면 그 값이 그대로 남아
 *    1엔이 1629원으로 저장됐다.
 *
 * 둘 다 "환율 칸을 언제 비우고 언제 채우는가"의 문제라, 이 판단만 순수 함수로 떼어
 * 테스트로 못 박아 둔다.
 */
object ExchangeRateDefaults {

    /**
     * [currency]로 바꿨을 때 환율 칸에 넣을 문자열.
     *
     * - KRW: `"1"`. 원화를 원화로 바꾸는 환율은 1이고 사용자가 고칠 여지가 없다.
     * - 외화이고 [rememberedRates]에 그 통화 값이 있으면: 그 값(소수 둘째 자리까지).
     * - 외화이고 기억된 값이 없으면: **빈 문자열**. 여기서 `"1"`을 넣으면 저장 검증을
     *   통과해 버려 금액이 조용히 틀린다. 비워 두면 저장이 막히고 이유가 표시된다.
     */
    fun rateTextFor(currency: TravelCurrency, rememberedRates: Map<String, Double>): String {
        if (currency == TravelCurrency.KRW) return "1"
        val remembered = rememberedRates[currency.code]?.takeIf { it > 0 } ?: return ""
        return formatRate(remembered)
    }

    /** 소수 둘째 자리까지만 남긴다. `1629.6400000001`이 칸에 그대로 들어가면 읽기 나쁘다. */
    fun formatRate(rate: Double): String =
        BigDecimal(rate).setScale(2, RoundingMode.HALF_UP).toPlainString()
}
