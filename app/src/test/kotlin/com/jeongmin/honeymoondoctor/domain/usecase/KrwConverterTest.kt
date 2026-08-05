package com.jeongmin.honeymoondoctor.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.jeongmin.honeymoondoctor.domain.model.TravelCurrency
import org.junit.Test

class KrwConverterTest {

    @Test
    fun `KRW는 환율과 무관하게 원 금액 그대로다`() {
        assertThat(KrwConverter.toKrw(15000, TravelCurrency.KRW, 999.9)).isEqualTo(15000)
    }

    @Test
    fun `EUR 최소 단위를 환율로 환산하고 HALF_UP 반올림한다`() {
        // 12.34 EUR × 1,532.5 KRW/EUR = 18,911.05 → 18,911
        assertThat(KrwConverter.toKrw(1234, TravelCurrency.EUR, 1532.5)).isEqualTo(18911)
        // 0.5 경계: 1.00 EUR × 1500.5 = 1500.5 → HALF_UP → 1501
        assertThat(KrwConverter.toKrw(100, TravelCurrency.EUR, 1500.5)).isEqualTo(1501)
        // 내림 경계: 1.00 EUR × 1500.4 = 1500.4 → 1500
        assertThat(KrwConverter.toKrw(100, TravelCurrency.EUR, 1500.4)).isEqualTo(1500)
    }

    @Test
    fun `CZK도 최소 단위 2자리로 동일하게 환산한다`() {
        // 250.00 CZK × 61.5 KRW/CZK = 15,375
        assertThat(KrwConverter.toKrw(25000, TravelCurrency.CZK, 61.5)).isEqualTo(15375)
        // 0.5 경계: 0.01 CZK × 50 = 0.5 → HALF_UP → 1
        assertThat(KrwConverter.toKrw(1, TravelCurrency.CZK, 50.0)).isEqualTo(1)
    }

    @Test
    fun `주 단위 입력 문자열을 최소 단위 정수로 파싱한다`() {
        assertThat(KrwConverter.parseAmountMinor("12.34", TravelCurrency.EUR)).isEqualTo(1234)
        assertThat(KrwConverter.parseAmountMinor("12", TravelCurrency.EUR)).isEqualTo(1200)
        assertThat(KrwConverter.parseAmountMinor("15,000", TravelCurrency.KRW)).isEqualTo(15000)
        // KRW는 소수 입력 불가(최소 단위 0자리)
        assertThat(KrwConverter.parseAmountMinor("15000.5", TravelCurrency.KRW)).isNull()
        // EUR는 소수 3자리 초과 불가
        assertThat(KrwConverter.parseAmountMinor("12.345", TravelCurrency.EUR)).isNull()
        assertThat(KrwConverter.parseAmountMinor("abc", TravelCurrency.EUR)).isNull()
        assertThat(KrwConverter.parseAmountMinor("-5", TravelCurrency.EUR)).isNull()
        assertThat(KrwConverter.parseAmountMinor("", TravelCurrency.EUR)).isNull()
    }

    @Test
    fun `최소 단위 정수를 표시용 주 단위 문자열로 되돌린다`() {
        assertThat(KrwConverter.formatMajor(1234, TravelCurrency.EUR)).isEqualTo("12.34")
        assertThat(KrwConverter.formatMajor(15000, TravelCurrency.KRW)).isEqualTo("15000")
    }
}
