package com.jeongmin.honeymoondoctor.data.seed

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

private const val SEED_ASSET_PATH = "seed/new_trip_defaults.json"

private val seedJson = Json { ignoreUnknownKeys = true }

/** 순수 파싱 로직만 분리해 Android Context 없이도(JVM 유닛테스트) 검증할 수 있게 한다. */
fun parseNewTripDefaults(rawJson: String): NewTripDefaults =
    seedJson.decodeFromString(NewTripDefaults.serializer(), rawJson)

/** assets/seed 디렉터리의 JSON에서 새 여행의 기본 준비물 체크리스트를 읽어온다. 파싱 실패는 손상된 빌드로 간주해 그대로 전파한다. */
@Singleton
class SeedAssetLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun loadNewTripDefaults(): NewTripDefaults {
        val raw = context.assets.open(SEED_ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parseNewTripDefaults(raw)
    }
}
