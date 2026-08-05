package com.jeongmin.honeymoondoctor.domain.usecase

import com.jeongmin.honeymoondoctor.domain.model.TravelCurrency
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * KRW 환산(스펙 7-6): `amountKrw = round_HALF_UP(amountMinor / 10^minorDigits × fxRateToKrw)`.
 * 환율은 사용자가 입력한 "1 통화 = X KRW"를 그대로 쓰고, 부동소수점 오차를 피하려고
 * BigDecimal로만 계산한다. KRW 입력이면 환율과 무관하게 원 금액 그대로다.
 */
object KrwConverter {

    fun toKrw(amountMinor: Long, currency: TravelCurrency, fxRateToKrw: Double): Long {
        if (currency == TravelCurrency.KRW) return amountMinor
        val major = BigDecimal.valueOf(amountMinor).movePointLeft(currency.minorDigits)
        return major.multiply(BigDecimal.valueOf(fxRateToKrw))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    /** "12.34" 같은 주 단위 입력 문자열 → 최소 단위 정수. 자릿수 초과·숫자 아님이면 null. */
    fun parseAmountMinor(text: String, currency: TravelCurrency): Long? {
        val normalized = text.trim().replace(",", "")
        if (normalized.isEmpty()) return null
        val value = normalized.toBigDecimalOrNull() ?: return null
        if (value.signum() < 0) return null
        return runCatching {
            value.movePointRight(currency.minorDigits).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        }.getOrNull()
    }

    /** 최소 단위 정수 → 화면 표시용 주 단위 문자열(EUR 1234 → "12.34"). */
    fun formatMajor(amountMinor: Long, currency: TravelCurrency): String =
        BigDecimal.valueOf(amountMinor).movePointLeft(currency.minorDigits).toPlainString()
}
