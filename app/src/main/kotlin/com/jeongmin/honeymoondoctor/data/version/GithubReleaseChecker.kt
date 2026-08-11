package com.jeongmin.honeymoondoctor.data.version

import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 최신 릴리스 정보. [tagName]은 `v0.1.4` 형식이고 [htmlUrl]은 사람이 열 릴리스 페이지다. */
data class LatestRelease(
    val tagName: String,
    val htmlUrl: String,
)

/**
 * GitHub Releases에서 최신 버전을 조회한다.
 *
 * 왜 필요한가: 이 앱은 Play Store를 거치지 않고 APK를 직접 설치하는 방식이라, 새 버전이
 * 나와도 사용자가 알 방법이 없다. 크래시를 고쳐도 옛 버전을 쓰는 사람에게 도달하지 않는다.
 *
 * 왜 HttpURLConnection + org.json인가: 앱에 HTTP 클라이언트 의존성이 없다(Firebase SDK만
 * 있다). 요청 하나·필드 두 개를 위해 OkHttp/Retrofit을 새로 들일 이유가 없고, 응답 파싱도
 * kotlinx.serialization의 `@Serializable` 대신 안드로이드 내장 `JSONObject`를 쓴다 —
 * release 빌드는 R8 난독화를 켜 두었고, 직렬화 클래스는 난독화 규칙에 걸리면 런타임에만
 * 깨져서 debug에서는 보이지 않는다. 필드 두 개를 직접 꺼내면 그 위험이 아예 없다.
 *
 * 인증 없이 호출하므로 GitHub의 비인증 한도(IP당 시간당 60회)를 따른다. 버전 정보 화면을
 * 열 때만 1회 호출하므로 실사용에서 걸릴 일은 없다.
 */
@Singleton
class GithubReleaseChecker @Inject constructor() {

    suspend fun fetchLatest(): Result<LatestRelease> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                // 응답 형식을 고정한다(GitHub 권장). Accept를 안 보내면 기본 버전이 바뀔 수 있다.
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            try {
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    error("업데이트 정보를 받지 못했습니다. (HTTP $code)")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.optString("tag_name").ifBlank { error("릴리스 태그를 읽지 못했습니다.") }
                LatestRelease(
                    tagName = tag,
                    htmlUrl = json.optString("html_url").ifBlank { FALLBACK_RELEASES_URL },
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        /** `/releases/latest`는 draft·prerelease를 제외한 최신 정식 릴리스만 돌려준다. */
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/wjdals988/honeymoondoctor/releases/latest"

        /** html_url이 비어 오는 경우를 대비한 목록 페이지. */
        const val FALLBACK_RELEASES_URL =
            "https://github.com/wjdals988/honeymoondoctor/releases"

        const val TIMEOUT_MS = 8_000
    }
}
