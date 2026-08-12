package com.jeongmin.honeymoondoctor.data.exchange

import com.jeongmin.honeymoondoctor.domain.model.TravelCurrency
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 조회한 환율. [date]는 기준일(ISO-8601)이며 사용자에게 "언제 기준인지" 보여주는 데 쓴다. */
data class FetchedRate(
    val currency: TravelCurrency,
    val krwPerUnit: Double,
    val date: String,
)

/**
 * 1 외화 = ? 원 환율을 받아온다.
 *
 * 왜 필요한가: 지출을 넣을 때마다 환율을 손으로 쳐야 했다. 값을 어디서 찾아 오는지도
 * 사용자 몫이었다.
 *
 * **자동 조회는 "기본값 제안"까지만 한다.** 저장되는 값은 여전히 그 시점의 스냅샷이고
 * (KrwConverter가 보존한다), 사용자가 고치면 고친 값이 이긴다. 나중에 환율이 바뀌어도
 * 과거 지출 금액이 흔들리면 안 되기 때문이다.
 *
 * 데이터는 유럽중앙은행(ECB) 공시 환율이다. **평일 하루 한 번 갱신**되고 주말·공휴일에는
 * 마지막 영업일 값이 나온다. 실제 카드 결제 환율(비자·마스터 환율 + 수수료)과는 다르므로
 * 정산의 근사값으로만 쓴다.
 *
 * frankfurter를 고른 이유: API 키가 필요 없고 응답이 작으며 ECB 원본을 그대로 준다.
 * kotlinx.serialization 대신 내장 JSONObject를 쓰는 이유는 release의 R8 난독화에서
 * 직렬화 클래스가 런타임에만 깨지는 위험을 아예 없애기 위함이다(업데이트 확인과 같은 이유).
 */
@Singleton
class ExchangeRateFetcher @Inject constructor() {

    suspend fun fetch(currency: TravelCurrency): Result<FetchedRate> = withContext(Dispatchers.IO) {
        runCatching {
            require(currency != TravelCurrency.KRW) { "원화는 환율 조회가 필요 없습니다." }
            val url = "$BASE_URL?base=${currency.code}&symbols=KRW"
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    error("환율을 받지 못했습니다. (HTTP ${connection.responseCode})")
                }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val rate = json.optJSONObject("rates")?.optDouble("KRW") ?: Double.NaN
                if (rate.isNaN() || rate <= 0.0) error("환율 값을 읽지 못했습니다.")
                FetchedRate(
                    currency = currency,
                    krwPerUnit = rate,
                    date = json.optString("date").ifBlank { "-" },
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        const val BASE_URL = "https://api.frankfurter.dev/v1/latest"
        const val TIMEOUT_MS = 8_000
    }
}
