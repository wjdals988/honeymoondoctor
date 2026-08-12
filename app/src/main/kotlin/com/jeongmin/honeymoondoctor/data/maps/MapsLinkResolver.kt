package com.jeongmin.honeymoondoctor.data.maps

import com.jeongmin.honeymoondoctor.domain.usecase.MapsUrlCoordinates
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 구글 지도 단축 링크(`maps.app.goo.gl/...`)를 원래 주소로 펼친다.
 *
 * 왜 필요한가: 구글 지도 **앱**에서 공유하면 거의 항상 단축 링크가 나온다. 단축 링크에는
 * 좌표가 들어 있지 않아 [MapsUrlCoordinates]가 아무것도 뽑지 못했다 — 실사용에서 가장
 * 흔한 경로가 막혀 있던 셈이다.
 *
 * 리다이렉트를 직접 따라가는 이유: `HttpURLConnection`의 자동 추적은 http↔https가 섞이면
 * 멈추고 최종 주소를 알려주지도 않는다. 여기서는 hop마다 `Location`을 읽어 최종 URL
 * 문자열을 돌려준다.
 */
@Singleton
class MapsLinkResolver @Inject constructor() {

    /** 단축 링크면 펼친 주소를, 아니면 입력을 그대로 돌려준다. */
    suspend fun resolve(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = url.trim()
            if (!MapsShortLink.isShortLink(trimmed)) return@runCatching trimmed

            var current = trimmed
            repeat(MAX_HOPS) {
                val hop = request(current)
                when {
                    // 리다이렉트: 다음 주소로 계속 따라간다.
                    hop.location != null -> {
                        current = absolute(current, hop.location)
                        if (MapsUrlCoordinates.parse(current) != null) return@runCatching current
                    }
                    // 리다이렉트 없이 페이지가 왔다. 짧은 주소가 HTML 중간 페이지를 주는
                    // 경우가 있어 본문에서 원래 지도 주소·좌표를 찾아본다.
                    else -> {
                        val fromBody = hop.body?.let { findMapsUrlOrCoordinates(it) }
                        return@runCatching fromBody ?: current
                    }
                }
            }
            current
        }
    }

    private data class Hop(val location: String?, val body: String?)

    private fun request(url: String): Hop {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            // User-Agent 선택이 결과를 가른다. 실제 링크로 확인해 보니 데스크톱 UA를 보내면
            // 구글이 리다이렉트 대신 Firebase 딥링크 안내 페이지(HTML)를 주고, 그 안에는
            // 좌표도 지도 주소도 없다. 안드로이드 UA로는 302 + Location에 좌표가 담긴
            // 지도 주소가 그대로 온다. 이 앱은 안드로이드이므로 자기 자신으로 요청한다.
            setRequestProperty("User-Agent", ANDROID_USER_AGENT)
        }
        try {
            val code = connection.responseCode
            connection.getHeaderField("Location")?.let { return Hop(location = it, body = null) }
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                error("링크를 찾을 수 없습니다. 주소가 정확한지 확인해 주세요.")
            }
            if (code != HttpURLConnection.HTTP_OK) {
                error("링크를 펼치지 못했습니다. (HTTP $code)")
            }
            // 지도 페이지 전체를 받으면 수 MB가 될 수 있어 앞부분만 읽는다. 리다이렉트
            // 안내나 canonical 링크는 문서 앞쪽에 온다.
            val body = connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(MAX_BODY_CHARS)
                val read = reader.read(buffer)
                if (read <= 0) null else String(buffer, 0, read)
            }
            return Hop(location = null, body = body)
        } finally {
            connection.disconnect()
        }
    }

    /** HTML 안에 박힌 지도 주소나 좌표를 찾는다. 없으면 null. */
    private fun findMapsUrlOrCoordinates(body: String): String? {
        MapsUrlCoordinates.parse(body)?.let { return body }
        return Regex("""https://www[.]google[.]com/maps[^"' ]*""").find(body)?.value
    }

    /** `Location`이 `/maps/place/...`처럼 상대 경로로 올 수 있다. */
    private fun absolute(base: String, location: String): String =
        if (location.startsWith("http", ignoreCase = true)) location else URL(URL(base), location).toString()

    private companion object {
        const val MAX_HOPS = 5
        const val MAX_BODY_CHARS = 200_000
        const val TIMEOUT_MS = 8_000
        const val ANDROID_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    }
}

/** 단축 링크인지 판별한다. 네트워크가 필요 없어 화면·테스트에서 그대로 쓸 수 있게 분리했다. */
object MapsShortLink {

    private val shortHosts = listOf("maps.app.goo.gl", "goo.gl/maps", "g.co/kgs")

    fun isShortLink(url: String): Boolean {
        val lower = url.trim().lowercase()
        return shortHosts.any { lower.contains(it) }
    }
}
