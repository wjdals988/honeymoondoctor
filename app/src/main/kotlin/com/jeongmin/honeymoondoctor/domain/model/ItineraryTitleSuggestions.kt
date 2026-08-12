package com.jeongmin.honeymoondoctor.domain.model

/**
 * 일정 유형별로 자주 쓰는 이름 후보. 일정 이름은 필수 입력이라 없앨 수 없지만,
 * "공항 이동"이나 "점심"처럼 대부분 몇 가지로 정해져 있어 칩으로 대신 채울 수 있다.
 *
 * 후보를 유형별로 나눈 이유: 전부 한 줄에 늘어놓으면 12개가 넘어 스크롤해야 찾는다.
 * 유형을 이미 고른 상태라면 그 유형의 4~6개만 보이는 편이 빠르다.
 *
 * 도메인에 두는 이유: 화면 코드에 문자열 배열을 박아 두면 나중에 유형이 늘 때
 * 어디를 고쳐야 하는지 알기 어렵다. [ItineraryType]과 같은 파일 계층에 둔다.
 */
object ItineraryTitleSuggestions {

    private val byType: Map<ItineraryType, List<String>> = mapOf(
        ItineraryType.TRANSPORT to listOf("공항 이동", "기차 이동", "숙소 체크인", "숙소 체크아웃", "렌터카 픽업"),
        ItineraryType.SIGHTSEEING to listOf("시내 산책", "박물관 관람", "전망대", "야경 보기", "투어"),
        ItineraryType.MEAL to listOf("아침", "점심", "저녁", "카페", "야식"),
        ItineraryType.REST to listOf("숙소 휴식", "스파", "낮잠", "수영장"),
        ItineraryType.SHOPPING to listOf("기념품", "장보기", "면세점", "쇼핑몰"),
        ItineraryType.ETC to listOf("환전", "짐 정리", "세탁", "유심 개통"),
    )

    fun forType(type: ItineraryType): List<String> = byType[type].orEmpty()
}
