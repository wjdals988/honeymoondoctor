package com.jeongmin.honeymoondoctor.domain.model

/**
 * 도시를 고르면 국가코드와 시간대가 따라오게 하는 프리셋.
 *
 * 왜 필요한가: 도시 추가 화면이 국가코드와 IANA 시간대(`Asia/Seoul`)를 직접 타이핑하게
 * 했다. 오타가 나면 `ZoneId.of()`가 실패해 그 도시가 현지 시각 판정에서 **조용히
 * 제외된다**(CurrentCityResolver). 사용자는 왜 시계가 안 바뀌는지 알 방법이 없었다.
 *
 * 목록은 한국에서 출발하는 여행지를 우선으로 담았다. 여기 없는 도시는 화면의 "직접 입력"
 * 으로 넣을 수 있으므로, 이 목록이 완전할 필요는 없다.
 */
data class CityPreset(
    val displayName: String,
    /** 검색용 다른 이름(영문·별칭). 사용자가 "Tokyo"로도 "도쿄"로도 찾을 수 있게 한다. */
    val aliases: List<String>,
    val countryCode: String,
    val timeZoneId: String,
    val countryName: String,
)

object CityPresets {

    val all: List<CityPreset> = listOf(
        // 일본
        preset("도쿄", "Tokyo", "JP", "Asia/Tokyo", "일본"),
        preset("오사카", "Osaka", "JP", "Asia/Tokyo", "일본"),
        preset("교토", "Kyoto", "JP", "Asia/Tokyo", "일본"),
        preset("후쿠오카", "Fukuoka", "JP", "Asia/Tokyo", "일본"),
        preset("삿포로", "Sapporo", "JP", "Asia/Tokyo", "일본"),
        preset("오키나와", "Okinawa", "JP", "Asia/Tokyo", "일본"),
        // 동남아시아
        preset("방콕", "Bangkok", "TH", "Asia/Bangkok", "태국"),
        preset("치앙마이", "Chiang Mai", "TH", "Asia/Bangkok", "태국"),
        preset("푸껫", "Phuket", "TH", "Asia/Bangkok", "태국"),
        preset("다낭", "Da Nang", "VN", "Asia/Ho_Chi_Minh", "베트남"),
        preset("하노이", "Hanoi", "VN", "Asia/Ho_Chi_Minh", "베트남"),
        preset("호치민", "Ho Chi Minh City", "VN", "Asia/Ho_Chi_Minh", "베트남"),
        preset("나트랑", "Nha Trang", "VN", "Asia/Ho_Chi_Minh", "베트남"),
        preset("싱가포르", "Singapore", "SG", "Asia/Singapore", "싱가포르"),
        preset("쿠알라룸푸르", "Kuala Lumpur", "MY", "Asia/Kuala_Lumpur", "말레이시아"),
        preset("코타키나발루", "Kota Kinabalu", "MY", "Asia/Kuala_Lumpur", "말레이시아"),
        preset("발리", "Bali", "ID", "Asia/Makassar", "인도네시아"),
        preset("세부", "Cebu", "PH", "Asia/Manila", "필리핀"),
        preset("보라카이", "Boracay", "PH", "Asia/Manila", "필리핀"),
        // 동아시아·오세아니아
        preset("타이베이", "Taipei", "TW", "Asia/Taipei", "대만"),
        preset("홍콩", "Hong Kong", "HK", "Asia/Hong_Kong", "홍콩"),
        preset("마카오", "Macau", "MO", "Asia/Macau", "마카오"),
        preset("상하이", "Shanghai", "CN", "Asia/Shanghai", "중국"),
        preset("베이징", "Beijing", "CN", "Asia/Shanghai", "중국"),
        preset("괌", "Guam", "GU", "Pacific/Guam", "괌"),
        preset("사이판", "Saipan", "MP", "Pacific/Saipan", "사이판"),
        preset("시드니", "Sydney", "AU", "Australia/Sydney", "호주"),
        preset("멜버른", "Melbourne", "AU", "Australia/Melbourne", "호주"),
        preset("오클랜드", "Auckland", "NZ", "Pacific/Auckland", "뉴질랜드"),
        // 유럽
        preset("파리", "Paris", "FR", "Europe/Paris", "프랑스"),
        preset("니스", "Nice", "FR", "Europe/Paris", "프랑스"),
        preset("런던", "London", "GB", "Europe/London", "영국"),
        preset("로마", "Rome", "IT", "Europe/Rome", "이탈리아"),
        preset("피렌체", "Florence", "IT", "Europe/Rome", "이탈리아"),
        preset("베네치아", "Venice", "IT", "Europe/Rome", "이탈리아"),
        preset("바르셀로나", "Barcelona", "ES", "Europe/Madrid", "스페인"),
        preset("마드리드", "Madrid", "ES", "Europe/Madrid", "스페인"),
        preset("리스본", "Lisbon", "PT", "Europe/Lisbon", "포르투갈"),
        preset("프라하", "Prague", "CZ", "Europe/Prague", "체코"),
        preset("빈", "Vienna", "AT", "Europe/Vienna", "오스트리아"),
        preset("부다페스트", "Budapest", "HU", "Europe/Budapest", "헝가리"),
        preset("취리히", "Zurich", "CH", "Europe/Zurich", "스위스"),
        preset("인터라켄", "Interlaken", "CH", "Europe/Zurich", "스위스"),
        preset("뮌헨", "Munich", "DE", "Europe/Berlin", "독일"),
        preset("베를린", "Berlin", "DE", "Europe/Berlin", "독일"),
        preset("암스테르담", "Amsterdam", "NL", "Europe/Amsterdam", "네덜란드"),
        preset("이스탄불", "Istanbul", "TR", "Europe/Istanbul", "튀르키예"),
        preset("아테네", "Athens", "GR", "Europe/Athens", "그리스"),
        preset("산토리니", "Santorini", "GR", "Europe/Athens", "그리스"),
        preset("헬싱키", "Helsinki", "FI", "Europe/Helsinki", "핀란드"),
        preset("레이캬비크", "Reykjavik", "IS", "Atlantic/Reykjavik", "아이슬란드"),
        // 미주
        preset("뉴욕", "New York", "US", "America/New_York", "미국"),
        preset("로스앤젤레스", "Los Angeles", "US", "America/Los_Angeles", "미국"),
        preset("샌프란시스코", "San Francisco", "US", "America/Los_Angeles", "미국"),
        preset("라스베이거스", "Las Vegas", "US", "America/Los_Angeles", "미국"),
        preset("호놀룰루", "Honolulu", "US", "Pacific/Honolulu", "미국 하와이"),
        preset("밴쿠버", "Vancouver", "CA", "America/Vancouver", "캐나다"),
        preset("토론토", "Toronto", "CA", "America/Toronto", "캐나다"),
        preset("칸쿤", "Cancun", "MX", "America/Cancun", "멕시코"),
        // 기타
        preset("두바이", "Dubai", "AE", "Asia/Dubai", "아랍에미리트"),
        preset("서울", "Seoul", "KR", "Asia/Seoul", "대한민국"),
        preset("제주", "Jeju", "KR", "Asia/Seoul", "대한민국"),
        preset("부산", "Busan", "KR", "Asia/Seoul", "대한민국"),
    )

    /**
     * 도시명·별칭·국가명으로 찾는다. 대소문자와 공백은 무시한다 — "hochiminh"으로도
     * "Ho Chi Minh City"가 나와야 한다.
     */
    fun search(query: String, limit: Int = 8): List<CityPreset> {
        val q = query.trim().lowercase().replace(" ", "")
        if (q.isEmpty()) return emptyList()
        return all.filter { preset ->
            preset.displayName.lowercase().replace(" ", "").contains(q) ||
                preset.countryName.lowercase().replace(" ", "").contains(q) ||
                preset.aliases.any { it.lowercase().replace(" ", "").contains(q) }
        }.take(limit)
    }

    private fun preset(
        displayName: String,
        englishName: String,
        countryCode: String,
        timeZoneId: String,
        countryName: String,
    ) = CityPreset(
        displayName = displayName,
        aliases = listOf(englishName),
        countryCode = countryCode,
        timeZoneId = timeZoneId,
        countryName = countryName,
    )
}
