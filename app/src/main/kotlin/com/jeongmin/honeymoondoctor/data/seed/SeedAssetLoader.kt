package com.jeongmin.honeymoondoctor.data.seed

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

private const val SEED_ASSET_PATH = "seed/honeymoon_trip_seed.json"

private val seedJson = Json { ignoreUnknownKeys = true }

/** 순수 파싱 로직만 분리해 Android Context 없이도(JVM 유닛테스트) 검증할 수 있게 한다. */
fun parseTripSeedBundle(rawJson: String): TripSeedBundle =
    seedJson.decodeFromString(TripSeedBundle.serializer(), rawJson)

/** assets/seed 디렉터리의 JSON에서 여행 시드 번들을 읽어온다. 파싱 실패는 손상된 빌드로 간주해 그대로 전파한다. */
@Singleton
class SeedAssetLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun loadHoneymoonTripSeed(): TripSeedBundle {
        val raw = context.assets.open(SEED_ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parseTripSeedBundle(raw)
    }
}
