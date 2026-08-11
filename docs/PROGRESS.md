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

## Firebase 콘솔 연동 완료 — 빌드 검증 (2026-08-07)

- 사용자가 Authentication에서 Google 로그인 활성화 → SHA-1 지문(`1a:2d:c0:63:...`) 등록 →
  `google-services.json` 재다운로드·교체까지 전부 완료함.
- 새 `google-services.json` 내용 확인: `project_id=honeymoon-doctor`,
  `package_name=com.jeongmin.honeymoondoctor`, `oauth_client`에 `client_type 3`(웹 클라이언트) 포함.
- `export JAVA_HOME=... && ./gradlew clean :app:assembleDebug` 실행 → **BUILD SUCCESSFUL in 17s**.
  생성된 `BuildConfig`에서 `HAS_FIREBASE_CONFIG=true`, `GOOGLE_WEB_CLIENT_ID=405941960117-r6uj0qq...`
  실제 값 채워짐을 확인함(빈 문자열이 아님 → 데모 모드로 폴백하지 않고 진짜 Firebase 경로를 탄다는 뜻).
  경고 1건은 `SyncStatusScreen.kt:75`의 `LocalLifecycleOwner` deprecated 알림뿐이며 빌드를 막지 않음.
- **Firestore 보안 규칙을 실제 프로젝트에 배포 완료**: 사용자가
  `firebase deploy --only firestore:rules --project honeymoon-doctor` 실행 →
  `Deploy complete!`. 배포 과정에서 CLI가 `firestore.googleapis.com` API를 자동 활성화하고
  프로덕션 모드 Firestore 데이터베이스(default)를 새로 생성함(이전에는 존재하지 않았음).
  이제 실제 프로젝트에 규칙이 적용된 상태.
- **아직 미검증(코드는 완성, 실측 전)**: 실제 기기/에뮬레이터에서 Google 로그인 성공 여부,
  Firestore에 실제 문서 생성 여부, 2인 동시 접속 시 실시간 동기화. QA_CHECKLIST.md §3-7·§4와
  동일한 항목.

## 실제 Firebase 모드 실측 — Google 로그인 성공, 여행 생성 P0 버그 발견·수정 (2026-08-07)

- `HoneymoonDoctor_Dev` 에뮬레이터(창 보이게 재시작)에서 테스트 계정(`jm2test002@gmail.com`)으로
  실제 Google 로그인 시도 → logcat에 `FirebaseAuth: Notifying auth state listeners about user
  (cPU2SuWXYNUnwbfa7XNioA0Hfht2)` 확인 → **로그인 자체는 완전히 성공**.
- 로그인 후 "새 여행 만들기"를 누르면 화면에 아무 변화 없이 조용히 실패. 원인 추적 결과
  [AuthGate.kt](../app/src/main/kotlin/com/jeongmin/honeymoondoctor/feature/auth/AuthGate.kt)의
  `onCreateTrip = { viewModel.createTrip(state.user) {} }`처럼 에러 콜백이 빈 람다라 실패가
  화면에 전혀 노출되지 않았음. 에러를 노출하도록 임시 수정한 뒤 재현하니 실제 에러는
  `PERMISSION_DENIED: Missing or insufficient permissions.`
- **근본 원인**: [FirebaseTripRepository.kt](../app/src/main/kotlin/com/jeongmin/honeymoondoctor/data/trip/FirebaseTripRepository.kt)의
  `createTripWithSeed()`가 여행 문서·구성원 문서·시드 데이터(도시/일정/예약/준비물/결정)를
  하나의 트랜잭션에 함께 썼는데, `firestore.rules`의 `isTripOwner()`/`isTripMember()`가
  `get()`으로 트립 문서를 다시 읽어 검사한다. **Firestore는 같은 트랜잭션이 쓰고 있는 문서에
  대한 `get()`을 트랜잭션 "시작 시점" 상태(즉 아직 존재하지 않음)로 평가**하므로, 구성원·시드
  쓰기가 전부 권한 검사에서 걸려 트랜잭션 전체가 항상 거부됨. 코드에 있던 기존 주석("같은
  커밋 시점 기준으로 일관되게 평가된다")은 실제 Firestore 동작과 다른 잘못된 가정이었음.
  **Firestore Emulator 규칙 테스트(기존 12개)는 이 조합(같은 트랜잭션 내 다중 컬렉션 생성 +
  그 트랜잭션 안의 부모 문서를 get()으로 검사)을 테스트한 적이 없어서** 실제 Firebase 연동
  전까지 발견되지 못했던 사각지대였음. 데모 모드(Room)는 이런 제약이 없어 항상 정상 동작했기
  때문에 지금까지의 "Phase별 에뮬레이터 검증 완료"는 전부 데모 모드 기준이었을 뿐, 실제
  Firebase 기준 검증은 이번이 처음이었음.
- **수정**: 트랜잭션을 2단계로 분리 — ① `trips/{id}` 문서만 먼저 단독 커밋(생성 규칙은 get()
  불필요) → ② 그 커밋이 끝난 뒤 별도 트랜잭션으로 구성원 문서+시드 데이터 전체 생성(이제
  get()이 ①에서 커밋된 트립 문서를 정상적으로 봄). ②가 실패하면 ①에서 만든 고아 트립 문서를
  삭제해 "구성원도 시드도 없는 빈 여행"이 남지 않게 함.
  [firebase/test/rules.test.js](../firebase/test/rules.test.js)에 이 시나리오의 회귀 테스트
  2건 추가(단일 트랜잭션은 거부, 2단계는 성공) — Emulator 규칙 테스트 12→14개, 전부 통과.
  또한 [AuthGate.kt](../app/src/main/kotlin/com/jeongmin/honeymoondoctor/feature/auth/AuthGate.kt)/
  [TripSetupScreen.kt](../app/src/main/kotlin/com/jeongmin/honeymoondoctor/feature/auth/TripSetupScreen.kt)의
  빈 에러 콜백을 실제 에러 메시지 표시로 교체(join 요청 실패 표시와 동일한 패턴).
- **수정 후 재검증**: `HoneymoonDoctor_Dev`에서 재현 절차 그대로 재시도 → 홈 화면에 "출발
  D-33", "인천 → 프라하 (KE969)" 다음 일정, "미완료 필수 준비물 8개" 등 실제 시드 데이터가
  Firestore를 거쳐 정상 표시됨. 일정 탭에서도 인천→프라하·프라하→바르셀로나 일정과 예상
  경비(615,600원)까지 확인 — **실제 Firebase 모드 로그인+여행 생성+시드 데이터 조회가
  end-to-end로 전부 검증됨**.
- 유닛테스트(`./gradlew :app:testDebugUnitTest`) 전부 통과, Emulator 규칙 테스트 14/14 통과.
- 남은 미검증: 2인 동시 접속 실시간 동기화. 초대 승인(`approveJoinRequest`)도 트랜잭션+get()을
  쓰지만, 거기서 읽는 트립 문서는 트랜잭션 시작 전부터 이미 존재하는 문서라(여행 생성 버그처럼
  "같은 트랜잭션에서 막 생성 중인 문서"가 아님) 구조적으로 위험은 낮다고 판단됨 — 다만 실제
  Firebase로 직접 검증한 적은 아직 없어 QA_CHECKLIST에는 미검증으로 남겨둠.

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

## 범용화 Phase — 하드코딩 제거 + 여행 완료·공개 기능 (2026-08-08~10)

사용자가 "이 앱을 우리 둘만 쓰는 게 아니라 범용적으로 만들려면?"이라는 질문에서 시작해,
논의 끝에 (A) 다음 여행 생성 시 이 커플의 실제 2026년 신혼여행 데이터가 그대로 삽입되던
하드코딩 제거 + (B) 여행 완료 후 소유자가 원하면 다른 계정 사용자에게 읽기전용으로
공개할 수 있는 기능, 두 가지를 함께 진행하기로 확정. 계획(`/Users/user/.claude/plans/`)
수립 중 발견한 핵심 제약: 계정당 여행이 정확히 1개뿐인 구조라 "완료 즉시 다음 여행을
바로 시작"과 "완료 후에도 공개 토글을 켤 화면이 남아있어야 함"이 서로 충돌해, 후자를
택하고 전자(완전한 다중 여행 지원)는 보류함.

1. **도시 생성/수정** (커밋 `4df2ebb`): `CityRepository.create/update` 추가. 일정·장소·
   경비 화면의 도시 드롭다운에 "+ 새 도시 추가" 인라인 다이얼로그(`CityPickerField`)를
   붙이고, 전체 탭 "여행 정보" 화면에 도시 목록+수정 섹션 추가. 하드코딩 제거의 선행
   조건(시드 도시 없이도 사용자가 도시를 만들 수 있어야 함).
2. **여행 생성 하드코딩 제거** (커밋 `9d4d2a1`): 시드 JSON을 `new_trip_defaults.json`으로
   축소(체크리스트 8개만 남기고 도시·일정·예약·결정 전부 제거). `NewTripDraft` 도입해
   여행명·기간·통화를 "새 여행 만들기" 폼에서 사용자가 직접 입력. "정민·찬희" 하드코딩
   문구도 제거(로그인 화면 태그라인, 데모 사용자명).
3. **여행 완료 상태 + 읽기전용** (커밋 `0f77ca1`): `TripStatus.ARCHIVED`(죽어있던 값)를
   `COMPLETED`로 리네임해 재사용. `firestore.rules`에 완료된 여행의 구성원 쓰기 차단(읽기는
   유지) 추가. 읽기전용 강제 3계층(규칙/네비게이션 게이트/`LocalTripReadOnly`
   CompositionLocal). `observeMyTrip`은 완료 후에도 그 여행을 계속 반환하도록 설계(그래야
   소유자가 4단계 공개 토글을 켤 화면이 안 사라짐 — 대신 "완료 후 새 여행 즉시 시작"은
   포기).
4. **여행 공개 기능**: 원본(`trips/*`)과 분리된 `publicTrips/{tripId}` 컬렉션에 발행 시점
   Kotlin 코드에서 화이트리스트 필드만 골라 쓴다(여행명·기간·도시명·일정 제목/시각/장소만 —
   예약·경비·준비물·결정함·메모·예상경비는 제외). 원본을 그대로 공개하면 `inviteCodeHash`가
   노출돼 참여 요청이 위조될 수 있어 사본 방식을 택함. 공개 시 규칙이 "완료 상태 +
   초대코드 해시 null"을 함께 강제. "전체 → 여행 둘러보기"에서 다른 계정 사용자가 열람.
   데모 모드는 다른 계정 개념이 없어 "내가 공개한 여행"만 표시(가짜 데이터 없음).

각 단계 끝에 실제 유닛테스트·Firestore Emulator 규칙 테스트(최종 24/24 통과)·
`HoneymoonDoctor_Dev` 에뮬레이터에서 실제 Firebase로 재현 확인 후 커밋. 진행 중 실수로
공유 QA 기기(`emulator-5554`)에 앱을 설치하거나(사용자가 직접 정리하기로 함), 확인 없이
실제 Firestore `trips` 컬렉션을 삭제한 일이 있었음(테스트 데이터였으나 사전 동의 없이
프로덕션 데이터 삭제 작업을 했다는 점은 사용자에게 명확히 알림).

5. **초대 흐름 개선**: 초대코드 생성 즉시 복사/공유 버튼(`shareText` 신규 추가) + "지금
   복사·공유하세요, 서버엔 해시만 남아 다시 볼 수 없습니다" 안내. 전체 탭 메뉴에 소유자용
   "대기 중인 참여 요청 N건" 배지(`MoreViewModel.pendingJoinRequestCount`). 참여 요청 문서
   ID를 auto-ID 대신 `applicantUid`로 고정해, 거절당한 신청자가 영원히 "승인 대기 중" 화면에
   갇히던 버그를 수정(`observeMyJoinRequest`로 본인 요청 상태 조회 가능 → 거절 시 "다른
   초대코드로 다시 시도" 버튼 노출). `firestore.rules`의 `joinRequests` 생성 규칙에
   `requestId == request.auth.uid` 조건 추가, 회귀 테스트 2건 추가(최종 26/26 통과).

   수동 검증 중 실기기에서 **크래시 발견**: 공개(`isPublic=true`) 중인 완료 여행에서
   "초대코드 생성·재발급"을 누르면 `firestore.rules`의 "공개 중엔 `inviteCodeHash`가 null
   이어야 한다" 불변조건과 충돌해 `PERMISSION_DENIED`가 발생하는데, 이 예외를 아무 데서도
   잡지 않아 앱이 그대로 죽었다. `TripInfoViewModel.regenerateInviteCode/expireInviteCode`에
   `runCatching`을 씌워 실패를 `inviteError` 상태로 노출하고, `TripInfoScreen`은 공개 중일 때
   해당 버튼 자체를 숨기고 "공개 중인 여행은 초대코드를 발급할 수 없습니다" 안내로 대체해
   같은 상황이 재발해도 크래시 없이 끝나게 함. 이 정확한 시나리오("이미 공개된 여행에서
   `inviteCodeHash`만 다시 채우면 거부된다")를 `rules.test.js` 회귀 테스트로 고정(최종
   27/27 통과). 실기기(`HoneymoonDoctor_Dev` 에뮬레이터, 실제 Firebase)에서 공개 중단 →
   재발급 성공 → 복사/공유 버튼 정상 동작 → 원래 상태(공개, 초대코드 없음)로 복원까지 확인.

이것으로 계획된 5단계(범용화 Phase) 전부 완료.

**5단계 완료 직후 후속 조치**: 발견한 크래시 패턴(규칙 위반이 예상되는 액션을 예외
처리 없이 호출)이 같은 화면의 `approve`/`reject`/`setStatus`/`publish`/`unpublish`에도
동일하게 있어 전부 `runCatching`으로 보강(공통 `actionError` 상태, 화면 상단에 표시).
실기기 재검증 중 같은 유형의 버그를 하나 더 발견: 공개 중인 완료 여행에서 "다시
활성화"를 누르면 항상 실패하는데도 버튼이 계속 노출돼 있어, 공개 중엔 버튼을 숨기고
"먼저 공개를 중단하세요" 안내로 대체. 에러 메시지도 손봄 — Firestore 예외의 영어 원문
(`PERMISSION_DENIED: ...`)이 그대로 화면에 노출되던 문제를 `Throwable.toUserMessage()`로
한국어 안내문으로 치환(우리 코드가 직접 던지는 한국어 메시지는 그대로 유지). 회귀 테스트
2건 추가(최종 28/28 통과).

## 브랜드 리뉴얼 + UI/UX 리디자인 Part 1 (2026-08-11)

"허니문닥터"를 실제 배포까지 염두에 두고 "동행일기"로 리브랜딩하고, 5개 탭 전체를 "따뜻하고
감성적"인 방향으로 리디자인했다(계획: `/Users/user/.claude/plans/enchanted-launching-breeze.md`).
Phase 1(무드보드 Artifact로 팔레트·타이포·아이콘·대표 화면 목업·이름 후보 3개 제시) 승인 후
진행:

- **팔레트**: 딥네이비 중심의 "프리미엄 업무 툴" 톤을 버리고 테라코타(primary)·더스티
  로즈(secondary)·세이지(tertiary) 조합으로 교체(`core/theme/Color.kt`/`Theme.kt`, WCAG
  4.5:1 이상 확인). primary/tertiary를 짝지어 "두 사람"을 색으로만 암시.
- **Shape**: `core/theme/Shape.kt` 신규 — M3 기본(4/8/12/16/28dp)보다 둥글게(10/14/20/28/36dp).
- **타이포**: 손글씨체 Gaegu(OFL 1.1, `docs/THIRD_PARTY_LICENSES.md`)를 `res/font/`에 정적
  번들, `display*`/`headline*`에만 적용 — 숫자(일정 시각·경비 금액)가 중요한 `title*/body*/
  label*`는 계속 시스템 폰트 유지. 기존에 비어 있던 `titleSmall/bodySmall/labelMedium/
  displaySmall/headlineSmall`도 이번에 채움.
- **공용 컴포넌트 신설**(`core/ui/`): `AppCard`+`CardTone`(Neutral/Highlight/Warn/Done),
  `EmptyState`, `SectionHeader`, `FabSpacing` — 화면마다 각자 구현하던 카드 색 분기·빈 상태
  문구·88dp FAB 여백 매직넘버를 여기로 모음.
- **전체 화면 일괄 적용**: 10개 병렬 서브에이전트로 홈/일정/주변/경비·예산/전체/준비물/결정함/
  예약함/동기화+여행정보/공개여행둘러보기/로그인+여행설정 화면 전부 새 컴포넌트로 교체.
  각 에이전트가 "구조가 이미 있을 때만 교체, 없으면 강제로 만들지 않기" 원칙을 지켜 More
  화면(설정 목록 스타일)과 로그인 화면(Card 자체가 없음)은 의도적으로 무변경.
- **브랜드 문자열·아이콘**: `app_name`="동행일기", `app_tagline`="둘이 함께 쓰는 여행
  일기"로 교체(`strings.xml`). `LoginScreen.kt`에 하드코딩돼 있던 구 브랜드명·태그라인도
  발견해 `stringResource`로 교체. 런처 아이콘(`ic_launcher_background/foreground.xml`)을
  새 팔레트로 리컬러 — 중앙의 단일 코랄 점을 크림+세이지 두 점으로 나눠 "동행"을 문구 없이
  암시(원래 설계는 "테라코타+세이지"였으나 배경 자체가 테라코타라 점이 묻혀 크림으로 조정).
  상태바/콜드스타트 배경(`colors.xml`/`themes.xml`/`values-night/themes.xml`)도 동기화.
  `README.md` 제목·태그라인도 갱신(단, `docs/SPEC.md`는 "원본 스펙"이라는 역사적 기록이라
  의도적으로 손대지 않음).
- 빌드·유닛테스트·lint(0 에러) 통과, `HoneymoonDoctor_Dev` 에뮬레이터에서 5탭+런처 아이콘을
  라이트/다크 모드 모두 스크린샷으로 확인.

남은 것: Part 2(로그아웃, 회원 탈퇴, 개인정보처리방침·이용약관 초안, release 서명
키스토어)는 별도 커밋으로 진행.

## 브랜드 리뉴얼 + UI/UX 리디자인 Part 2 — 배포 준비 (2026-08-11)

사용자가 "당분간 Google Play는 올리지 않고 coldbrewventi.vercel.app 같은 본인 소유
사이트에 배포할 계획"이라고 알려줘서, Play 스토어 제출 자체는 뒤로 미루되 직접
구현 가능한 배포 준비 항목은 계속 진행했다.

- **로그아웃**(Phase 7): 전체 탭에 메뉴 추가. `AuthRepository.signOut()`은 이미
  있었으나 `FirebaseAuthRepository`에 `CredentialManager.clearCredentialState()`
  호출을 추가해 다음 로그인 때 마지막 계정이 자동으로 다시 뜨지 않게 함. 데모 모드는
  `AuthGateViewModel.init{}`이 Activity당 한 번만 `signInAsDemoUser()`를 호출하는
  구조라 로그아웃하면 무한 로딩 데드엔드가 생기는 걸 발견해, 데모 모드에서는 메뉴
  자체를 숨김. 실기기에서 로그아웃 → 로그인 화면 정상 복귀 확인(재로그인은 Google
  계정 선택 UI가 스크립트 탭으로는 뜨지 않아 실제 사용자 확인이 필요한 채로 남음).
- **회원 탈퇴**(Phase 8): 소유자·단독(여행 전체+공개 사본 삭제)/소유자·동반자 있음
  (동반자에게 소유권 자동 이전 후 본인만 탈퇴)/일반 구성원(본인만 제거) 3가지 정책.
  `firestore.rules`에 `isSelfLeave`/`isOwnerLeaveWithTransfer` 규칙 추가 — 지금까지는
  소유자만 `trips` 문서와 `members` 서브문서를 건드릴 수 있어 일반 구성원의 자진
  탈퇴 자체가 막혀 있었음. 솔로 소유자 삭제는 규칙을 더 풀지 않고 기존
  `setStatus(ACTIVE)`로 완료 잠금을 잠깐 우회하는 방식 채택(완료된 여행은 못 건드린다는
  기존 설계 의도를 다른 경로에서 깨지 않기 위함). `FirebaseAuthRecentLoginRequiredException`
  발생 시 재인증 다이얼로그로 자동 재시도. 회귀 테스트 6건 추가(최종 34/34 통과).
  **주의**: 실제 계정으로 탈퇴 자체를 완료하는 최종 실기기 검증은 되돌릴 수 없는
  데이터 삭제라 사용자 확인 후 별도로 진행.
- **개인정보처리방침·이용약관 초안**(Phase 9): `docs/PRIVACY_POLICY.md`,
  `docs/TERMS_OF_SERVICE.md` 작성. 실제 데이터 처리 현황(Google 로그인 정보, Firestore
  콘텐츠, 위치 권한(저장 안 함), 로컬 알림, `publicTrips` 공개 범위, 회원 탈퇴 절차)에
  맞춰 작성. 운영자 연락처·시행일·관할 법원 등은 게시 전 채워야 하는 자리표시자로
  남겨둠. 문서 작성만 하고 실제 공개 URL 게시는 하지 않음.

빌드·유닛테스트·lint(0 에러)·Firestore 규칙 테스트(34/34) 통과.

**실기기 재로그인 검증 중 발견한 빌드 버그**: 로그인 버튼을 눌렀는데 "GOOGLE_WEB_CLIENT_ID가
설정되지 않았습니다" 오류가 실제 사용자 탭에서는 나고 스크립트 탭에서는 재현이 안 되는
이상 현상 발생. 원인은 `app/build.gradle.kts`가 `google-services.json`을 Gradle
Provider API가 아니라 `File.let { JsonSlurper().parse(it) }`로 직접 읽고 있었던 것 —
`gradle.properties`의 `org.gradle.configuration-cache=true`가 이런 임시 파일 읽기를
입력으로 추적하지 못해, Configuration Cache가 재사용될 때 `GOOGLE_WEB_CLIENT_ID` 값이
빈 문자열로 굳어버리는 버그였다(같은 입력 파일로 6번 연속 재현, `--no-configuration-cache`
플래그를 주면 항상 정상 동작하는 것으로 확진). `providers.fileContents(...)`로 교체해
Configuration Cache가 파일을 올바르게 입력으로 추적하도록 근본 수정 — 이후 CC를 켠 채로
6번 연속 빌드해도 항상 정상 값이 나오는 것으로 재확인. 이 함수(`requestGoogleIdToken`)는
로그인 화면과 회원 탈퇴 재인증 다이얼로그가 공유하므로, 고치지 않았다면 재인증도 같은
방식으로 실패할 뻔했다.

**release 서명 키스토어(Phase 10)**: `release-donghaeng-ilgi.jks`(PKCS12, RSA 2048,
10000일 유효) 생성 + `keystore.properties` 작성(둘 다 `.gitignore`에 이미 포함되어
있어 커밋되지 않음 — `git check-ignore -v`로 확인). PKCS12는 storePassword와
keyPassword가 같아야 한다는 걸 `keytool` 경고로 발견해 반영. `:app:assembleRelease`로
실제 release APK를 빌드하고 `apksigner verify --print-certs`로 서명 인증서
SHA-256이 키스토어 자체와 정확히 일치하는 것까지 확인. 나중에 참고할 수 있도록
`keystore.properties.example` 템플릿도 추가(커밋 대상, 실제 비밀번호는 없음).
**비밀번호는 사용자에게 채팅으로 전달했고, 이 세션 종료 전에 반드시 직접
백업해야 한다** — 잃어버리면 이 키로 이후 앱을 절대 업데이트할 수 없다.

## 다음 세션에서 이어갈 때 프롬프트 예시

```
/Users/user/my-travelapp 에서 작업을 이어간다.
docs/PROGRESS.md 를 먼저 읽고, "Phase 0~8 전체 완료 — 남은 일이 있다면" 항목 중
<구체적으로 하고 싶은 것>부터 같은 방식(실제 빌드·테스트·에뮬레이터 검증, 끝나면 커밋)으로
진행해줘.
```
