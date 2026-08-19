package com.jeongmin.honeymoondoctor.domain.model

/**
 * 나라별 긴급 전화번호(백로그: 긴급상황 화면).
 *
 * 안전과 직결되는 정보라 두 가지 원칙을 둔다.
 * 1. **확신하는 번호만 담는다.** 애매하면 비워 두고 [unified]만 채운다 — 틀린 번호를
 *    자신 있게 보여주는 쪽이 비어 있는 것보다 위험하다.
 * 2. **이 표를 최종 근거로 내세우지 않는다.** 화면에서 외교부 해외안전여행을 함께
 *    안내하고, 번호는 바뀔 수 있다고 명시한다.
 *
 * 목록은 [CityPresets]에 있는 나라만 담는다(없는 나라는 화면에서 영사콜센터만 보여준다).
 */
data class EmergencyNumbers(
    val countryCode: String,
    val countryName: String,
    /** 통합 긴급번호(유럽 112처럼 경찰·구급을 함께 받는 번호). */
    val unified: String? = null,
    val police: String? = null,
    /** 구급차. 나라에 따라 소방과 같은 번호다. */
    val ambulance: String? = null,
)

object EmergencyContacts {

    /**
     * 대한민국 외교부 영사콜센터(24시간). 해외에서 사건·사고·여권 분실을 겪었을 때
     * 한국어로 안내받을 수 있는 창구라, 나라와 무관하게 항상 맨 위에 보여준다.
     */
    const val CONSULAR_CALL_CENTER = "+82-2-3210-0404"

    /** 외교부 해외안전여행 — 나라별 최신 정보·경보의 공식 출처. */
    const val SAFE_TRAVEL_URL = "https://www.0404.go.kr"

    private val all: List<EmergencyNumbers> = listOf(
        EmergencyNumbers("KR", "대한민국", police = "112", ambulance = "119"),
        EmergencyNumbers("JP", "일본", police = "110", ambulance = "119"),
        EmergencyNumbers("CN", "중국", police = "110", ambulance = "120"),
        EmergencyNumbers("TW", "대만", police = "110", ambulance = "119"),
        EmergencyNumbers("HK", "홍콩", unified = "999"),
        EmergencyNumbers("MO", "마카오", unified = "999"),
        EmergencyNumbers("SG", "싱가포르", police = "999", ambulance = "995"),
        EmergencyNumbers("TH", "태국", unified = "191", ambulance = "1669"),
        EmergencyNumbers("VN", "베트남", police = "113", ambulance = "115"),
        EmergencyNumbers("MY", "말레이시아", unified = "999"),
        EmergencyNumbers("PH", "필리핀", unified = "911"),
        EmergencyNumbers("ID", "인도네시아", unified = "112"),
        EmergencyNumbers("AE", "아랍에미리트", police = "999", ambulance = "998"),
        EmergencyNumbers("TR", "튀르키예", unified = "112"),
        EmergencyNumbers("US", "미국", unified = "911"),
        EmergencyNumbers("CA", "캐나다", unified = "911"),
        EmergencyNumbers("MX", "멕시코", unified = "911"),
        EmergencyNumbers("GU", "괌", unified = "911"),
        EmergencyNumbers("MP", "북마리아나제도", unified = "911"),
        EmergencyNumbers("AU", "호주", unified = "000"),
        EmergencyNumbers("NZ", "뉴질랜드", unified = "111"),
        // 유럽연합·유럽 대부분은 112가 경찰·구급·소방을 함께 받는다.
        EmergencyNumbers("GB", "영국", unified = "999", police = "112"),
        EmergencyNumbers("FR", "프랑스", unified = "112", police = "17", ambulance = "15"),
        EmergencyNumbers("DE", "독일", unified = "112", police = "110"),
        EmergencyNumbers("ES", "스페인", unified = "112"),
        EmergencyNumbers("IT", "이탈리아", unified = "112"),
        EmergencyNumbers("CZ", "체코", unified = "112", police = "158", ambulance = "155"),
        EmergencyNumbers("AT", "오스트리아", unified = "112", police = "133", ambulance = "144"),
        EmergencyNumbers("CH", "스위스", unified = "112", police = "117", ambulance = "144"),
        EmergencyNumbers("NL", "네덜란드", unified = "112"),
        EmergencyNumbers("PT", "포르투갈", unified = "112"),
        EmergencyNumbers("GR", "그리스", unified = "112"),
        EmergencyNumbers("HU", "헝가리", unified = "112"),
        EmergencyNumbers("FI", "핀란드", unified = "112"),
        EmergencyNumbers("IS", "아이슬란드", unified = "112"),
    )

    private val byCode = all.associateBy { it.countryCode }

    fun forCountry(countryCode: String?): EmergencyNumbers? =
        countryCode?.trim()?.uppercase()?.let(byCode::get)
}
