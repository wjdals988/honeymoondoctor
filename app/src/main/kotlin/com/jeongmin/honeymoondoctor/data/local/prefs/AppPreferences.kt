package com.jeongmin.honeymoondoctor.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "honeymoon_doctor_prefs")

/**
 * 앱 테마. SYSTEM이 기본 — 사용자가 명시적으로 고르기 전에는 OS 설정을 따른다.
 * 문자열로 저장하고 모르는 값은 SYSTEM으로 읽는다(다운그레이드 안전).
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * "우리 위치" 자동 공유 모드. 셋 다 **앱이 화면에 떠 있는 동안만** 동작한다 —
 * 백그라운드 추적은 지속 알림(포그라운드 서비스)과 백그라운드 위치 권한이 필요해
 * 배터리·심사 부담이 크고, 방침의 "명시적 사용 중에만 전송" 원칙과도 멀어진다.
 */
enum class LocationShareMode {
    /** 버튼을 누를 때만(기본). */
    MANUAL,

    /** 앱을 열 때(포그라운드 진입)마다 1회 자동 공유. */
    ON_APP_OPEN,

    /** 앱을 쓰는 동안 5분마다 갱신. 화면을 끄거나 나가면 멈춘다. */
    EVERY_5_MIN_WHILE_USING,
}

data class LastKnownLocation(
    val latitude: Double,
    val longitude: Double,
    val capturedAtEpochMillis: Long,
)

data class AppPrefsSnapshot(
    val selectedCityId: String?,
    val lastKnownLocation: LastKnownLocation?,
    val pendingJoinTripId: String?,
    /** 지금 보고 있는 여행. null이면 여행 목록 화면에 머문다. */
    val selectedTripId: String?,
    val lastSyncAtEpochMillis: Long?,
    val scheduledReminderKeys: Set<String>,
    /**
     * 통화 코드 → 직전에 쓴 환율(1 외화 = ? KRW). 지출을 넣을 때마다 같은 값을 다시
     * 타이핑하지 않게 하려고 남긴다. 기내·로밍처럼 환율을 못 불러오는 상황이 실제로
     * 흔해서, 네트워크 조회의 대체 수단으로도 쓰인다.
     */
    val lastExchangeRates: Map<String, Double>,
    val themeMode: ThemeMode,
    /**
     * 이동 일정 출발 여유(분). 홈의 "출발 권장 시각" = 이동 일정 시작 − 이 값.
     * 지도 이동시간 데이터가 없어 계산이 아니라 사용자가 정하는 값이다. 기본 60분.
     */
    val transportLeadMinutes: Int,
    val locationShareMode: LocationShareMode,
)

/** 앱 설정, 선택 도시, 최근 위치 메타데이터를 담는 DataStore 래퍼. 위치는 화면 진입/새로고침 때만 갱신된다. */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.appDataStore

    private object Keys {
        val SELECTED_CITY_ID = stringPreferencesKey("selected_city_id")
        val LAST_LATITUDE = doublePreferencesKey("last_latitude")
        val LAST_LONGITUDE = doublePreferencesKey("last_longitude")
        val LAST_LOCATION_AT = longPreferencesKey("last_location_at")
        val PENDING_JOIN_TRIP_ID = stringPreferencesKey("pending_join_trip_id")
        val SELECTED_TRIP_ID = stringPreferencesKey("selected_trip_id")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at_epoch_millis")
        val SCHEDULED_REMINDER_KEYS = stringSetPreferencesKey("scheduled_reminder_keys")

        /**
         * `"EUR=1629.64"` 형태의 문자열 집합. 통화별로 키를 따로 만들지 않는 이유:
         * 통화가 늘어날 때마다 키가 늘어나면 스냅샷을 읽는 쪽에서 통화 목록을 미리
         * 알아야 한다. 집합 하나면 앱이 모르는 통화가 들어와도 그대로 읽힌다.
         */
        val LAST_EXCHANGE_RATES = stringSetPreferencesKey("last_exchange_rates")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TRANSPORT_LEAD_MINUTES = longPreferencesKey("transport_lead_minutes")
        val LOCATION_SHARE_MODE = stringPreferencesKey("location_share_mode")
    }

    private companion object {
        const val DEFAULT_TRANSPORT_LEAD_MINUTES = 60
    }

    val snapshot: Flow<AppPrefsSnapshot> = dataStore.data.map { prefs ->
        val lat = prefs[Keys.LAST_LATITUDE]
        val lng = prefs[Keys.LAST_LONGITUDE]
        val capturedAt = prefs[Keys.LAST_LOCATION_AT]
        AppPrefsSnapshot(
            selectedCityId = prefs[Keys.SELECTED_CITY_ID],
            lastKnownLocation = if (lat != null && lng != null && capturedAt != null) {
                LastKnownLocation(lat, lng, capturedAt)
            } else {
                null
            },
            pendingJoinTripId = prefs[Keys.PENDING_JOIN_TRIP_ID],
            selectedTripId = prefs[Keys.SELECTED_TRIP_ID],
            lastSyncAtEpochMillis = prefs[Keys.LAST_SYNC_AT],
            scheduledReminderKeys = prefs[Keys.SCHEDULED_REMINDER_KEYS].orEmpty(),
            lastExchangeRates = decodeRates(prefs[Keys.LAST_EXCHANGE_RATES].orEmpty()),
            themeMode = prefs[Keys.THEME_MODE]
                ?.let { raw -> ThemeMode.entries.firstOrNull { it.name == raw } }
                ?: ThemeMode.SYSTEM,
            transportLeadMinutes = prefs[Keys.TRANSPORT_LEAD_MINUTES]?.toInt()
                ?.takeIf { it in 1..24 * 60 }
                ?: DEFAULT_TRANSPORT_LEAD_MINUTES,
            locationShareMode = prefs[Keys.LOCATION_SHARE_MODE]
                ?.let { raw -> LocationShareMode.entries.firstOrNull { it.name == raw } }
                ?: LocationShareMode.MANUAL,
        )
    }

    suspend fun setSelectedCity(cityId: String) {
        dataStore.edit { it[Keys.SELECTED_CITY_ID] = cityId }
    }

    suspend fun setLastKnownLocation(location: LastKnownLocation) {
        dataStore.edit {
            it[Keys.LAST_LATITUDE] = location.latitude
            it[Keys.LAST_LONGITUDE] = location.longitude
            it[Keys.LAST_LOCATION_AT] = location.capturedAtEpochMillis
        }
    }

    /** 여행 선택/해제. null로 두면 AuthGate가 여행 목록 화면으로 되돌린다. */
    suspend fun setSelectedTripId(tripId: String?) {
        dataStore.edit {
            if (tripId == null) it.remove(Keys.SELECTED_TRIP_ID) else it[Keys.SELECTED_TRIP_ID] = tripId
        }
    }

    suspend fun setPendingJoinTripId(tripId: String?) {
        dataStore.edit {
            if (tripId == null) it.remove(Keys.PENDING_JOIN_TRIP_ID) else it[Keys.PENDING_JOIN_TRIP_ID] = tripId
        }
    }

    /** 프로세스가 재시작돼도 "마지막 동기화 시각"이 유지되도록 DataStore에 저장한다. */
    suspend fun setLastSyncAt(epochMillis: Long) {
        dataStore.edit { it[Keys.LAST_SYNC_AT] = epochMillis }
    }

    /** 현재 예약돼 있는 일정 알림 키 집합. 다음 재계획 시 사라진 키를 취소하는 기준이 된다. */
    suspend fun setScheduledReminderKeys(keys: Set<String>) {
        dataStore.edit { it[Keys.SCHEDULED_REMINDER_KEYS] = keys }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLocationShareMode(mode: LocationShareMode) {
        dataStore.edit { it[Keys.LOCATION_SHARE_MODE] = mode.name }
    }

    suspend fun setTransportLeadMinutes(minutes: Int) {
        if (minutes !in 1..24 * 60) return
        dataStore.edit { it[Keys.TRANSPORT_LEAD_MINUTES] = minutes.toLong() }
    }

    /**
     * 그 통화로 마지막에 쓴 환율을 덮어쓴다. 저장된 지출의 환율은 건드리지 않는다 —
     * 여기 값은 "다음 입력의 기본값"일 뿐이다.
     */
    suspend fun setLastExchangeRate(currencyCode: String, rate: Double) {
        if (rate <= 0) return
        dataStore.edit { prefs ->
            val others = decodeRates(prefs[Keys.LAST_EXCHANGE_RATES].orEmpty())
                .filterKeys { it != currencyCode }
            prefs[Keys.LAST_EXCHANGE_RATES] = encodeRates(others + (currencyCode to rate))
        }
    }

    /**
     * 기기에 남은 이 앱의 설정 전부를 지운다. 로그아웃·회원 탈퇴에서 호출한다.
     *
     * 왜 필요한가: 여기에는 마지막으로 잡은 위치(위도·경도)와 통화별 환율, 보고 있던
     * 여행 ID가 들어 있다. 탈퇴하면 서버 데이터는 지워지는데 기기에는 그대로 남아
     * 있었다 — 다음 사람이 같은 기기로 로그인하면 남의 위치 기록이 깔린 상태로
     * 시작하는 셈이었다. 개인정보처리방침에 적은 "탈퇴 시 삭제"와도 어긋났다.
     */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    private fun decodeRates(raw: Set<String>): Map<String, Double> =
        raw.mapNotNull { entry ->
            val code = entry.substringBefore('=', missingDelimiterValue = "")
            val rate = entry.substringAfter('=', missingDelimiterValue = "").toDoubleOrNull()
            // 형식이 깨진 항목은 조용히 버린다. 환율 기본값 하나 때문에 앱이 멈출 일은 없어야 한다.
            if (code.isBlank() || rate == null || rate <= 0) null else code to rate
        }.toMap()

    private fun encodeRates(rates: Map<String, Double>): Set<String> =
        rates.map { (code, rate) -> "$code=$rate" }.toSet()
}
