package com.jeongmin.honeymoondoctor.core.version

/**
 * 버전별 변경 내역. 앱에 그대로 담아 두는 이유: 이 앱은 스토어를 거치지 않고 APK를 직접
 * 설치하는 방식이라 "무엇이 바뀌었는지"를 볼 수 있는 스토어 페이지가 없다. 네트워크 없이
 * 기내·해외 로밍 상태에서도 확인할 수 있어야 하므로 원격 조회가 아니라 상수로 둔다.
 *
 * 새 버전을 낼 때 [entries] 맨 앞에 추가한다(최신이 위). versionName은
 * `app/build.gradle.kts`의 값과 일치시킨다.
 */
data class ReleaseEntry(
    val versionName: String,
    val versionCode: Int,
    /** 배포일(ISO-8601). 기기 시간대와 무관하게 그대로 보여준다. */
    val date: String,
    val changes: List<String>,
)

object ReleaseHistory {

    val entries: List<ReleaseEntry> = listOf(
        ReleaseEntry(
            versionName = "0.1.3",
            versionCode = 4,
            date = "2026-08-11",
            changes = listOf(
                "도시에 체류 시작일·종료일을 넣을 수 있습니다. 넣으면 그 기간 동안 홈 화면의 현지 시각이 그 도시 시간대로 표시됩니다.",
                "제목 글꼴을 손글씨체에서 시스템 기본 글꼴로 되돌렸습니다.",
                "이 화면(버전 정보·변경 내역)을 추가했습니다.",
            ),
        ),
        ReleaseEntry(
            versionName = "0.1.2",
            versionCode = 3,
            date = "2026-08-11",
            changes = listOf(
                "완료 처리한 여행에서 일정·준비물 등을 건드리면 앱이 종료되던 문제를 고쳤습니다.",
                "완료된 여행에서 수정·삭제 버튼이 그대로 보이던 문제를 고쳤습니다. 길찾기·예약번호 보기 같은 읽기 동작은 그대로 씁니다.",
                "저장이 실패하면 화면을 닫지 않고 이유를 표시합니다.",
                "준비물·예약함처럼 제목줄이 있는 화면의 상단 여백이 두 배로 벌어져 있던 문제를 고쳤습니다.",
            ),
        ),
        ReleaseEntry(
            versionName = "0.1.1",
            versionCode = 2,
            date = "2026-08-11",
            changes = listOf(
                "일부 기기에서 Google 로그인 버튼을 눌러도 아무 반응이 없던 문제를 고쳤습니다.",
                "로그인이 실패한 이유가 화면에 표시되지 않던 문제를 고쳤습니다.",
                "로그인 버튼을 연달아 누르면 로그인이 영구히 멈추던 문제를 고쳤습니다.",
                "로그인·여행 만들기 화면이 상단 상태표시줄과 겹치던 문제를 고쳤습니다.",
            ),
        ),
        ReleaseEntry(
            versionName = "0.1.0",
            versionCode = 1,
            date = "2026-08-11",
            changes = listOf(
                "첫 배포. 이름을 \"동행일기\"로 정하고 화면 전체를 새 색·글꼴·모서리로 정리했습니다.",
                "Google 로그인, 초대코드로 둘이 같은 여행 공유, 로그아웃, 회원 탈퇴.",
                "홈·일정·주변·경비·전체 5개 탭. 예약함·준비물·결정함·예산·공개 여행 둘러보기.",
                "오프라인에서도 열람·기록 가능하며 연결되면 자동으로 동기화됩니다.",
            ),
        ),
    )
}
