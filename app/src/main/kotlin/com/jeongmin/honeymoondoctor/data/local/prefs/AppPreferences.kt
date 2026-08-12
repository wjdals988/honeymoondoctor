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
}
