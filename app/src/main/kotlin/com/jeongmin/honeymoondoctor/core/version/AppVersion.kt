package com.jeongmin.honeymoondoctor.core.version

/**
 * 버전 문자열 비교. GitHub 릴리스 태그(`v0.1.4`)와 설치된 `BuildConfig.VERSION_NAME`(`0.1.4`)을
 * 비교해 업데이트가 있는지 판단한다.
 *
 * 문자열 비교(`>`)를 쓰지 않는 이유: "0.1.10"과 "0.1.9"를 문자열로 비교하면 9가 더 크다고
 * 나온다. 마디마다 숫자로 끊어 비교해야 한다.
 */
object AppVersion {

    /** 앞의 `v`를 떼고 숫자 마디만 남긴다. 숫자가 아닌 마디(예: `0.2.0-beta`의 `0-beta`)는 앞의 숫자만 취한다. */
    fun parse(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V")
            .split('.')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    /**
     * [latest]가 [installed]보다 새 버전이면 true. 마디 수가 달라도(`0.2` vs `0.1.4`)
     * 짧은 쪽을 0으로 채워 비교한다. 파싱할 수 없으면 false를 돌려 "업데이트 있음"을
     * 잘못 띄우지 않는다 — 헛된 업데이트 알림이 못 띄우는 것보다 나쁘다.
     */
    fun isNewerThan(latest: String, installed: String): Boolean {
        val l = parse(latest)
        val i = parse(installed)
        if (l.isEmpty() || i.isEmpty()) return false
        for (index in 0 until maxOf(l.size, i.size)) {
            val a = l.getOrElse(index) { 0 }
            val b = i.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
