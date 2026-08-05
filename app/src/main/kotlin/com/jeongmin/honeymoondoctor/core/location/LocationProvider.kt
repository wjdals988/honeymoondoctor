package com.jeongmin.honeymoondoctor.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.jeongmin.honeymoondoctor.data.local.prefs.AppPreferences
import com.jeongmin.honeymoondoctor.data.local.prefs.LastKnownLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * 위치는 화면 진입 또는 Pull-to-Refresh에서만 이 클래스를 통해 1회 취득한다(스펙 2장 —
 * 백그라운드 추적 금지). 성공하면 AppPreferences에 최근 위치 메타데이터로 저장해
 * 오프라인·권한 거절 시에도 마지막 위치 기준 표시가 가능하게 한다.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
) {
    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 현재 위치 1회 취득. 권한이 없거나 실패하면 null. 성공 시 DataStore에 최근 위치로 저장.
     * 신규 픽스가 10초 안에 안 오면 마지막 캐시 위치로 폴백한다(무한 스피너 방지).
     */
    suspend fun refreshCurrentLocation(): LastKnownLocation? {
        if (!hasLocationPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        @Suppress("MissingPermission")
        val location = kotlinx.coroutines.withTimeoutOrNull(10_000) {
            runCatching {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                    .await()
            }.getOrNull()
        } ?: runCatching {
            @Suppress("MissingPermission")
            client.lastLocation.await()
        }.getOrNull() ?: return null

        val result = LastKnownLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            capturedAtEpochMillis = Instant.now().toEpochMilli(),
        )
        appPreferences.setLastKnownLocation(result)
        return result
    }
}
