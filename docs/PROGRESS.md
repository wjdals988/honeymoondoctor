# 진행 상황 (다른 세션에서 이어받을 때 먼저 읽을 문서)

원본 요구사항 전문은 [SPEC.md](SPEC.md). 이 문서는 "지금까지 뭘 했고, 왜 그렇게 했고, 다음에 뭘 해야 하는지"만 담는다.

마지막 갱신: 2026-08-06, Phase 8(전체 완료)까지 반영. 스펙 11장 전체 Phase가 끝났다.

## 저장소 위치 / 실행 환경

- 프로젝트 루트: `/Users/user/my-travelapp` (git repo, branch `main`)
- 원격: `origin = https://github.com/wjdals988/honeymoondoctor.git`, **push 완료**(2026-08-06).
  - 이 레포 전용 로컬 설정(전역 `~/.gitconfig`는 그대로 사내 계정 `jeongmin2@navercorp.com` +
    `oss.navercorp.com` 유지): `git config --local user.name/user.email` = `JMLee` /
    `wjdals988@gmail.com`, `credential.https://github.com.helper = !gh auth git-credential`
    (`gh auth login --hostname github.com`으로 `wjdals988` 계정 인증됨).
  - GitHub가 저장소 생성 시 만든 `README.md` 1줄 커밋과 로컬 히스토리가 서로 무관해
    `git merge origin/main --allow-unrelated-histories`로 합친 뒤 push했다(강제 push 아님).
- `/Users/user/series-qa-pipeline-claude`는 **완전히 다른 프로젝트**(TestRail QA 파이프라인)다. 절대 헷갈리지 말 것.
- Android SDK: `~/Library/Android/sdk` (platform 36.1 / 37 설치됨)
- JDK: Temurin 21 — `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home` 하고 `./gradlew` 실행
- Gradle: wrapper 9.6.1 (`./gradlew` 사용, 전역 `gradle` 명령은 wrapper 최초 생성에만 썼음)
- **에뮬레이터 주의**: `emulator-5554`(AVD `Series_QA_Perf_API_36_1`)는 **다른 세션이 실제 QA 자동화(Appium+pytest)에 쓰는 공유 장비**다. 절대 설치/실행하지 말 것. 이 프로젝트 전용으로 `HoneymoonDoctor_Dev`(API 36.1, `~/.android/avd/HoneymoonDoctor_Dev.avd`) AVD를 새로 만들어뒀으니 그것만 쓴다.
  ```bash
  export ANDROID_HOME="$HOME/Library/Android/sdk"
  export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"
  emulator -avd HoneymoonDoctor_Dev -no-window -no-boot-anim -no-snapshot -gpu swiftshader_indirect &
  ```
  `adb devices`로 나온 시리얼(대개 `emulator-5556`)에 `-s`로 지정해서 설치/실행할 것 — 시리얼을 지정하지 않으면 다른 에뮬레이터가 여러 개 떠 있을 때 잘못된 기기로 갈 수 있다.

## Phase 진행 상황 (원본 스펙 11장 순서 기준)

| Phase | 내용 | 상태 | 커밋 |
|---|---|---|---|
| 0 | git init, 루트 구조 | ✅ 완료 | `52171aa` |
| 1 | Gradle/Compose/Hilt/Navigation 스캐폴딩, 데모모드, 테마 | ✅ 완료 | `52171aa` |
| 2 | 시드 JSON, 41개 장소 TSV/JSON 템플릿, Room/DataStore | ✅ 완료 | `6f65ff8` |
| 3 | 인증(Google 로그인), 2인 공유, `firestore.rules`, Emulator 테스트 | ✅ 완료 | `575fd24` |
| 4 | 일정 CRUD, 홈 "다음 일정" 계산 + 타임존 유닛테스트 | ✅ 완료 | `6058bfa` |
| 5 | 준비물, 예약 메타데이터, 기기 내 바우처, 경비·예산·결정함 | ✅ 완료 | `cff6edc` |
| 6 | 주변 추천, 위치 권한, Haversine, 추천 점수, Maps Intent | ✅ 완료 | `14fcfc9` |
| 7 | 오프라인 상태, 동기화 대기 추적, 로컬 알림 | ✅ 완료 | `8eb366f` |
| 8 | 테스트 전수 실행, README, QA 체크리스트, APK 빌드 | ✅ 완료 | (다음 커밋) |

## 검증 명령 (매 Phase 끝날 때마다 이걸로 확인)

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
cd /Users/user/my-travelapp
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain

# Firestore 보안규칙 (firestore.rules 건드렸을 때만)
cd firebase && npm test
```
마지막 실행 결과(2026-08-06, Phase 8/전체 완료 후): `./gradlew clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 성공, 유닛테스트 62/62 통과, lint 차단오류 0건(경미한 경고 7건), `firebase && npm test` 12/12 통과. APK: `app/build/outputs/apk/debug/app-debug.apk`(27.9MB).

에뮬레이터 스모크(`HoneymoonDoctor_Dev`=emulator-5556, 데모 모드):
- Phase 4: 여행 생성 → 시드 항공편 3건 일정 탭 표시(시간대 라벨), 일정 추가/완료/삭제, KST·프라하 교차 시간대 겹침 과로 경고·완료 시 해제, 연결 예약 삭제 경고, 홈 D-35·다음 일정 카드
- Phase 5: 준비물 8건 시드·체크 토글(완료율 1/8)·필터 칩, 예약 5건 시드·상세 마스킹(`KE•••••••`)·보기/복사, 결정함 옵션 선택→결정 완료 정렬, EUR 12.34×환율 1532.5 → 18,911원 실시간 HALF_UP 환산·저장, 경비 요약(1/2 정산 9,455원·예약 예상비 합계 1,922,152원), 홈 준비 현황 카드(필수 준비물 8·확인 필요 예약 0·지출/예산)
- Phase 6: `pm grant` + `emu geo fix`(프라하 구시가지)로 "현재 위치 기준" 전환, 장소 추가 → 714m 거리(수기 계산과 일치), 점수 근거 문자열 일치, 테스트 TSV(유효2·오류1행) SAF 선택 → 미리보기 오류 사유 → 2건 가져오기 → 추천 랭킹 53/50/49점 수기 계산과 전부 일치, `pm revoke` 후 권한 거절 배너·거리 숨김·거리 0점 확인
- **Phase 7(2026-08-06)**: `svc wifi/data disable` → 동기화 상태 화면 "오프라인" 실시간 전환(상태바 신호 아이콘과 함께), `svc ... enable`로 복구 확인. 알림 권한 `pm revoke` 후 "거절됨" 표시·"권한 요청"/"설정으로 이동" 버튼 노출 확인, `pm grant` 후 화면 재진입(ON_RESUME)으로 "허용됨" 갱신 확인. **AlarmManager 실제 등록 검증**: `appops set ... SCHEDULE_EXACT_ALARM allow` 후 일정 탭에서 항목 추가(도시=프라하, 10:00 현지 시각) → `dumpsys alarm`으로 `ItineraryAlarmReceiver` 대상 알람 3건이 정확히 서울 표시 기준 09-08 17:00(H24)·09-09 14:00(H3)·09-09 16:00(H1)로 등록됐음을 확인(프라하→서울 UTC+7 환산이 정확히 반영됨). 시드 항공편(KE969, 11:05 KST)에 대해서도 동일한 방식으로 3건이 24h/3h/1h 오프셋에 맞춰 이미 등록돼 있음을 함께 확인 — `ItineraryReminderSyncCoordinator`가 앱 시작 시 자동으로 전체 일정을 스캔해 예약함을 실증
- **미검증**: 바우처 실제 파일 첨부 플로우(Phase 5, 수동 확인 필요), Pull-to-Refresh 제스처(스와이프 자동화 미실행 — 동일 코드 경로인 화면 재진입 갱신은 확인됨), 알림이 실제로 발사되는 순간(수 시간~수십 일 후라 대기 불가 — 대신 AlarmManager 등록 자체를 직접 검증함), WorkManager 폴백 경로(정확 알람 미허용 시)의 실제 발사(코드 경로는 존재하나 실측 미완료), 재부팅 시 BootCompletedReceiver 재예약(에뮬레이터 재부팅 미실행)

## 핵심 아키텍처 결정 (Phase 4 이후에도 그대로 따를 것)

- **Demo/Firebase 이중 구현 + 런타임 라우팅**: `domain/repository/*Repository` 인터페이스 하나당 `data/*/Demo*Repository`(DataStore/Room 기반, 실기기 계정 없이도 동작)와 `data/*/Firebase*Repository`(Firestore) 두 구현을 만들고, `core/di/RepositoryModule`에서 `DemoModeManager.isDemoMode`를 보고 `Provider<T>`로 지연 선택한다. **이 패턴을 Phase 4~7의 모든 신규 리포지토리(Itinerary, Reservation, Checklist, Expense, Budget, Place, Decision)에 그대로 반복 적용**하면 된다.
- **DemoModeManager**(`core/demo/DemoModeManager.kt`): `BuildConfig.HAS_FIREBASE_CONFIG`(google-services.json 존재 여부, 빌드타임) + `FirebaseApp.getInstance()` 성공 여부(런타임)를 함께 봐서 데모 모드를 판단한다. Firebase 관련 클래스(FirebaseAuth/FirebaseFirestore)는 데모 모드일 때 **절대 인스턴스화되면 안 됨** — `Provider<T>` 간접 주입으로 이미 보장돼 있다.
- **시드 삽입 경계**: `TripRepository.createTripWithSeed()`가 **여행 문서 + 소유자 멤버 + 도시 4 + 일정 3 + 예약 5 + 준비물 8 + 결정함 5**를 전부 삽입한다(Demo는 각 Demo 리포지토리의 `seedForNewTrip()`, Firebase는 여행 생성 트랜잭션 하나에 포함 — 원자성 보장). 여행 생성이 시드 삽입의 유일한 진입점이므로 재삽입은 구조적으로 불가능하다. 시드 삽입은 이것으로 완결.
- **일정 시간 모델(Phase 4)**: 도메인 `ItineraryItem`은 UTC `Instant`(startAt/endAt) + 표시용 `timeZone`/`endTimeZone`(IANA)을 갖는다. `endTimeZone`은 출발·도착 시간대가 다른 항공 일정용으로 **스펙 8장 스키마에 추가한 필드**(null이면 timeZone과 동일). 로컬시각↔UTC 변환은 `core/time/LocalTimes`가 유일한 경계다. 다음 일정/충돌 판정은 전부 Instant 비교라 기기 시간대와 무관하다.
- **참조 해제(스펙 4장) 구현 위치**: 일정 삭제 → 그 일정을 가리키는 예약의 `linkedItineraryId` 해제(`ItineraryViewModel.delete`). 예약 삭제 → 그 예약을 가리키는 일정의 `reservationId` 해제 + 기기 바우처 삭제 여부 **별도 확인** 다이얼로그(`ReservationDetailViewModel.delete`). 자동 연쇄 삭제는 어디에도 없다. 단, **경비의 linkedItineraryId/linkedReservationId 해제는 아직 안 걸려 있음** — Phase 6~8에서 삭제 경고 카운트에 경비·장소를 추가할 때 함께 처리할 것.
- **기기 전용 바우처(`data/voucher/VoucherStore.kt`)**: 확장자+MIME 이중 화이트리스트, 파일당 15MB(선언 크기가 아닌 실제 복사 바이트로 재검증), 총 100MB, `files/vouchers` + FileProvider. Firestore에는 존재 여부조차 저장하지 않는다(스펙 7-4). 데모 초기화가 Room·바우처 파일까지 지운다.
- **금액 규칙(`domain/usecase/KrwConverter.kt`)**: 모든 금액은 최소 단위 정수(amountMinor). KRW 0자리, EUR/CZK 2자리. KRW 환산은 BigDecimal HALF_UP이며 입력 시점 환율(fxRateToKrw)과 환산액(amountKrw)을 지출 문서에 스냅샷으로 보존한다.
- **동기화 상태(Phase 7, `data/sync/`)**: Firestore SDK가 "전체 대기 쓰기 개수"를 직접 주지 않는 문제를, Phase 2에 미리 만들어뒀던 Room `PendingSyncChangeEntity`/`Dao`(끝내 어디서도 쓰이지 않음) 대신 **Firestore 스냅샷 메타데이터 집계 방식으로 교체 구현**했다 — 현재 여행의 문서+모든 서브컬렉션을 `MetadataChanges.INCLUDE`로 구독해 `hasPendingWrites` true인 문서 수를 합산한다(수동으로 큐를 관리할 필요가 없어 더 견고함). 미사용 Room 엔티티는 삭제했다(`AppDatabase`/`DatabaseModule`에서도 제거). "마지막 동기화 시각"은 `AppPreferences`에 영속화한다. 오프라인 여부는 Firestore 상태가 아니라 `ConnectivityManager.NetworkCallback` 기반 실제 기기 네트워크 상태(`core/network/ConnectivityObserver`)를 그대로 쓴다 — 데모 모드에서도 의미 있게 표시하기 위함.
- **중요 일정 알림(Phase 7, `core/notification/`)**: "중요한 일정" = 시각이 있는(allDay 아님) 미완료(PLANNED) 일정으로 정의(`domain/usecase/ItineraryReminderPlanner` — 순수 함수, 유닛테스트로 오프셋 경계 전부 커버). 정확한 알람 권한(`SCHEDULE_EXACT_ALARM`, Android 12+ 특수 권한)이 있으면 `AlarmManager.setExactAndAllowWhileIdle` + `ItineraryAlarmReceiver`(비노출, Hilt 불필요)로, 없으면 `WorkManager` 지연 작업(`ItineraryReminderWorker`, 지연될 수 있음이 스펙 요구사항)으로 폴백한다. 어느 경로로 예약됐는지는 `ItineraryReminderScheduler`가 두 경로 취소를 모두 시도하므로 알 필요 없다. 예약 상태는 `AppPreferences`의 키 집합(`scheduledReminderKeys`)으로 추적해 재계획 시 사라진 일정의 알림만 정확히 취소한다. `ItineraryReminderSyncCoordinator`가 `HoneymoonDoctorApp.onCreate()`에서 프로세스 전역으로 시작돼 현재 여행의 일정 변경을 항상 관찰한다. `BootCompletedReceiver`(`@AndroidEntryPoint`)가 재부팅 후 정확 알람 재예약을 전담한다(WorkManager는 자체적으로 재부팅 후 복원되므로 손댈 필요 없음). 알림 권한(POST_NOTIFICATIONS) 거절 시 `ItineraryNotifier.show()`가 조용히 아무 것도 하지 않는다(크래시 방지, 핵심 기능 영향 없음).
- **firestore.rules** (`/firestore.rules`): `trips/{tripId}` 하위의 `cities, itinerary, reservations, checklistItems, expenses, budgets, places, decisions`는 이미 "구성원이면 전부 CRUD 허용"으로 열어뒀다. Phase 4~7에서 새 서브컬렉션을 추가하지 않는 한 규칙은 안 건드려도 된다.
- **시간/시간대 데이터**: 시드 JSON(`app/src/main/assets/seed/honeymoon_trip_seed.json`)의 모든 일정·예약은 `startAtLocal`/`startTimeZone`/`endAtLocal`/`endTimeZone` 쌍으로 저장돼 있다(둘 다 있어야 시간대 변환이 가능). Phase 4의 "다음 일정 계산"과 UTC 저장 로직을 만들 때 이 필드 구조를 그대로 Firestore 스키마(`startAt`/`endAt`은 UTC Timestamp, `timeZone`은 표시용 문자열)로 변환하면 된다.

## 이번 세션에서 겪은 실수 (재발 방지용 기록)

- **AGP 9.0부터 Kotlin이 AGP에 내장**돼 `org.jetbrains.kotlin.android` 플러그인을 적용하면 빌드가 실패한다. Compose/Serialization 서브플러그인(`org.jetbrains.kotlin.plugin.compose` 등)은 여전히 별도 적용해야 한다.
- **Firebase BOM 34.x부터 `-ktx` 아티팩트가 제거**됐다 (`firebase-auth`, `firebase-firestore`를 직접 쓴다. `-ktx` 접미사 버전은 존재하지 않음).
- **Android API 37이 이미 출시**돼 있어(제 학습 시점 이후) 최신 AndroidX 라이브러리들이 `compileSdk 37` 이상을 요구한다. `compileSdk`/`targetSdk`를 36으로 두면 AAR 메타데이터 체크에서 실패한다.
- 의존성 버전은 절대 기억에 의존하지 말고 `curl -s https://dl.google.com/dl/android/maven2/.../maven-metadata.xml` (Google Maven) / `https://repo1.maven.org/maven2/.../maven-metadata.xml` (Maven Central)로 실제 최신 안정 버전을 확인한 뒤 `gradle/libs.versions.toml`에 반영했다. Phase 4 이후 새 라이브러리를 추가할 때도 이 방식을 유지할 것.
- 에뮬레이터 관련 사고: 이미 떠 있던 `emulator-5554`가 다른 세션의 실제 QA 자동화 배치가 쓰던 공유 장비인 줄 모르고 잠깐 앱을 설치·실행했다가 그 배치를 방해할 뻔했다. 반드시 `HoneymoonDoctor_Dev` 전용 에뮬레이터만 사용할 것.

## Firebase 콘솔 연동 진행 중 (2026-08-07, 사용자가 직접 진행)

- 사용자가 Firebase 콘솔에서 프로젝트(`honeymoon-doctor`)와 Android 앱(`com.jeongmin.honeymoondoctor`)을
  등록하고 `app/google-services.json`을 받아 배치함.
- **디버그 빌드의 `applicationIdSuffix = ".debug"`를 제거함**(app/build.gradle.kts). 이유: 이 앱은
  2인 개인용이라 release와 debug를 같은 기기에 동시 설치할 필요가 없고, 접미사가 있으면
  Firebase에 앱을 2개(정식+debug) 등록해야 해서 불필요하게 복잡해짐. 제거 후 디버그 빌드의
  실제 패키지명도 `com.jeongmin.honeymoondoctor`로 통일됨(이전 세션 기록에 `.debug`로 adb
  설치했던 예시들은 이제 접미사 없이 그대로 쓰면 됨).
- 아직 남은 것: Authentication에서 Google 로그인 활성화 → SHA-1 등록 → google-services.json
  재다운로드(웹 클라이언트 ID 포함) → 실제 Firebase 모드 빌드 검증.

## Firebase 설정 — 사용자가 직접 해야 하는 것 (아직 아무것도 안 받음)

1. Firebase 콘솔에서 프로젝트 생성 → Android 앱 등록(패키지명 `com.jeongmin.honeymoondoctor`) → `google-services.json`을 `app/google-services.json`에 저장(자동으로 `.gitignore`에 걸려 커밋되지 않음).
   - 파일이 생기는 순간 `app/build.gradle.kts`가 자동으로 google-services 플러그인을 적용하고, 그 안의 웹 클라이언트 ID(`client_type == 3`)를 뽑아 `BuildConfig.GOOGLE_WEB_CLIENT_ID`로 노출한다 — **추가 설정 불필요**.
2. 디버그 키스토어 SHA-1 지문을 Firebase 콘솔의 Android 앱 설정에 등록해야 Google 로그인이 동작한다. (`./gradlew signingReport`로 확인 가능)
3. Firebase Authentication에서 Google 로그인 제공업체를 활성화하고, OAuth 동의 화면을 구성해야 한다.
4. Firestore를 프로덕션 모드로 생성한 뒤 `firestore.rules`를 배포한다: `firebase deploy --only firestore:rules --project <실제 프로젝트 ID>` (지금은 로컬 Emulator 테스트만 돼 있고 실제 프로젝트에 배포된 적 없음).

이 중 하나도 아직 받지 못했으므로, 지금 상태의 앱은 **데모 모드로만 실행·검증**됐다.
(2026-08-06 기준) 사용자가 Firebase 연결을 별도로 직접 진행 중 — 완료되면 README의
"Firebase 연결 절차"대로 됐는지, 그리고 QA_CHECKLIST.md §3-7(2인 공유 실동기화)과
§4에 미검증으로 남겨둔 항목들을 재확인해야 한다.

## Phase 0~8 전체 완료 — 남은 일이 있다면

스펙 11장의 9단계(Phase 0~8)가 모두 끝났다. 앞으로 다시 이어갈 만한 작업은:

1. **Firebase 연결 후 실동기화 검증**: `docs/QA_CHECKLIST.md` §3-7, §4의 미검증 항목들
   (2인 실시간 동기화, WorkManager 알림 폴백 실발사, 재부팅 재예약)을 실제로 확인.
2. **`docs/FEATURE_STATUS.md`에 정리된 명확한 미구현 4가지**: 여행 기본정보 수정/삭제,
   도시(City) CRUD UI, 긴급상황 화면, 로그아웃 버튼. 필요해지면 우선순위를 정해 추가.
3. **릴리스 서명·배포**: 지금은 `keystore.properties`가 없어 debug APK만 빌드된다.
   실제 배포하려면 release 키스토어를 만들고 `keystore.properties.example`을 참고해
   설정해야 한다(아직 이 파일 자체도 없음 — 필요해지면 새로 만들 것).

## 다음 세션에서 이어갈 때 프롬프트 예시

```
/Users/user/my-travelapp 에서 작업을 이어간다.
docs/PROGRESS.md 를 먼저 읽고, "Phase 0~8 전체 완료 — 남은 일이 있다면" 항목 중
<구체적으로 하고 싶은 것>부터 같은 방식(실제 빌드·테스트·에뮬레이터 검증, 끝나면 커밋)으로
진행해줘.
```
