package com.jeongmin.honeymoondoctor.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TravelCurrencyTest {

    @Test
    fun `이미 저장된 통화는 절대 사라지지 않는다`() {
        // 저장된 지출의 통화는 enum 이름으로 Firestore에 들어 있다. 값을 지우거나
        // 이름을 바꾸면 그 지출이 KRW로 읽혀(runCatching 기본값) 금액이 조용히 틀어진다.
        // 초기 3개는 실제 데이터가 존재하므로 여기에 못 박아 둔다.
        val names = TravelCurrency.entries.map { it.name }

        assertThat(names).containsAtLeast("KRW", "EUR", "CZK")
    }

    @Test
    fun `코드는 enum 이름과 같고 중복이 없다`() {
        // 환율 API 호출과 DataStore 저장 키가 모두 code를 쓴다. 이름과 어긋나면
        // "EUR로 저장했는데 EURO로 조회"하는 종류의 버그가 난다.
        TravelCurrency.entries.forEach { currency ->
            assertThat(currency.code).isEqualTo(currency.name)
        }
        assertThat(TravelCurrency.entries.map { it.code }).containsNoDuplicates()
    }

    @Test
    fun `소수점 없는 통화만 minorDigits가 0이다`() {
        // JPY 1000엔을 100000으로 저장하면 환산 금액이 100배가 된다.
        val zeroDigits = TravelCurrency.entries.filter { it.minorDigits == 0 }.map { it.code }

        assertThat(zeroDigits).containsExactly("KRW", "JPY", "VND")
    }

    @Test
    fun `minorDigits는 0 또는 2다`() {
        TravelCurrency.entries.forEach { currency ->
            assertThat(currency.minorDigits).isIn(listOf(0, 2))
        }
    }

    @Test
    fun `유럽중앙은행 고시에 없는 통화는 자동 조회를 끈다`() {
        // 2026-08-12 frankfurter.dev /v1/currencies 실측 결과. 여기 목록이 틀리면
        // 눌러도 실패하는 버튼이 화면에 남는다.
        val notFetchable = TravelCurrency.entries.filterNot { it.autoFetchable }.map { it.code }

        assertThat(notFetchable).containsExactly("TWD", "VND")
    }
}
