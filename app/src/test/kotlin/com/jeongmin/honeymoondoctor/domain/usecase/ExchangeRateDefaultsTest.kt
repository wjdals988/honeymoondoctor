package com.jeongmin.honeymoondoctor.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.jeongmin.honeymoondoctor.domain.model.TravelCurrency
import org.junit.Test

class ExchangeRateDefaultsTest {

    @Test
    fun `원화는 환율 1로 고정된다`() {
        assertThat(ExchangeRateDefaults.rateTextFor(TravelCurrency.KRW, emptyMap())).isEqualTo("1")
    }

    @Test
    fun `기억된 환율이 없는 외화는 빈 칸으로 둔다`() {
        // 이게 이 파일의 존재 이유다. 여기서 "1"을 돌려주면 €50이 50원으로 저장된다 —
        // 저장 검증(환율 > 0)이 "1"을 통과시키기 때문에 아무 경고도 나오지 않는다.
        assertThat(ExchangeRateDefaults.rateTextFor(TravelCurrency.EUR, emptyMap())).isEmpty()
    }

    @Test
    fun `통화를 바꾸면 그 통화의 환율만 쓴다`() {
        val remembered = mapOf("EUR" to 1629.64, "JPY" to 9.12)

        assertThat(ExchangeRateDefaults.rateTextFor(TravelCurrency.EUR, remembered)).isEqualTo("1629.64")
        assertThat(ExchangeRateDefaults.rateTextFor(TravelCurrency.JPY, remembered)).isEqualTo("9.12")
    }

    @Test
    fun `다른 통화의 환율이 새 통화로 새지 않는다`() {
        // EUR 환율만 기억된 상태에서 JPY를 고르면 1629.64가 넘어와선 안 된다.
        val onlyEur = mapOf("EUR" to 1629.64)

        assertThat(ExchangeRateDefaults.rateTextFor(TravelCurrency.JPY, onlyEur)).isEmpty()
    }

    @Test
    fun `0 이하로 저장된 값은 없는 것으로 본다`() {
        // DataStore가 깨진 값을 들고 있어도 그걸 그대로 환율 칸에 넣지 않는다.
        assertThat(ExchangeRateDefaults.rateTextFor(TravelCurrency.EUR, mapOf("EUR" to 0.0))).isEmpty()
        assertThat(ExchangeRateDefaults.rateTextFor(TravelCurrency.EUR, mapOf("EUR" to -3.0))).isEmpty()
    }

    @Test
    fun `환율은 소수 둘째 자리까지 반올림해 보여준다`() {
        assertThat(ExchangeRateDefaults.formatRate(1629.6449)).isEqualTo("1629.64")
        assertThat(ExchangeRateDefaults.formatRate(9.125)).isEqualTo("9.13")
        assertThat(ExchangeRateDefaults.formatRate(1.0)).isEqualTo("1.00")
    }
}
