# 동행일기

> 둘이 함께 쓰는 여행 일기 — 여행 일정 과밀, 예약 누락, 준비물 미비, 예산 초과를
> 한 화면에서 확인하고 대응하는 2인용 여행 운영 앱. (구 "허니문닥터", 2026-08 리브랜딩)

Android 네이티브 앱(Kotlin + Jetpack Compose)이며, APK를 직접 설치해 사용합니다.
자세한 요구사항 원문은 [docs/SPEC.md](docs/SPEC.md), 개발 경과와 설계 결정은
[docs/PROGRESS.md](docs/PROGRESS.md)를 참고하세요.

## 기술 스택

- Kotlin, Jetpack Compose, Material 3, Navigation Compose
- Hilt(의존성 주입), Coroutines/Flow
- Firebase Authentication(Google 로그인), Cloud Firestore(오프라인 캐시 포함)
- Room(기기 전용 바우처 메타데이터), DataStore(앱 설정·데모 모드 데이터)
- WorkManager + AlarmManager(중요 일정 로컬 알림)
- Fused Location Provider(화면 진입/새로고침 시에만 위치 취득, 백그라운드 추적 없음)

## 데모 모드란

`app/google-services.json`이 없으면(또는 Firebase 초기화에 실패하면) 앱은 **자동으로
데모 모드**로 전환됩니다. 로그인 없이 즉시 사용 가능하며, 모든 데이터는 이 기기의
Room/DataStore에만 저장되고 어디로도 동기화되지 않습니다. 화면 상단에 항상
"데모 모드 · 이 기기에만 저장됩니다" 배너가 표시됩니다.

- 데모 모드에서는 전체 탭 하단에 **데모 데이터 초기화** 메뉴가 나타나며, 저장된 모든
  데이터(여행·일정·예약·바우처 파일 포함)를 지웁니다.
- 실제 Firestore 여행 데이터에는 이런 자동 초기화 기능이 없습니다(의도된 동작).

## Firebase 연결 절차 (선택 사항 — 2인 실시간 동기화가 필요할 때만)

데모 모드만으로도 앱의 모든 기능을 검증할 수 있습니다. 부부가 각자 기기에서 같은
여행을 실시간으로 공유하려면 아래 5단계를 진행하세요.

### 1. Firebase 프로젝트 생성
[Firebase 콘솔](https://console.firebase.google.com) → 프로젝트 추가. Google Analytics는
사용하지 않습니다(스펙상 분석 SDK 미포함).

### 2. Android 앱 등록 + google-services.json
- 패키지 이름: `com.jeongmin.honeymoondoctor` (정확히 일치해야 함)
- 다운로드한 `google-services.json`을 `app/google-services.json`에 저장
  (`.gitignore`에 걸려 있어 커밋되지 않습니다)
- 이 파일이 생기는 즉시 `app/build.gradle.kts`가 자동으로 google-services 플러그인을
  적용하고, 그 안의 웹 클라이언트 ID를 `BuildConfig.GOOGLE_WEB_CLIENT_ID`로 노출합니다.
  추가 설정은 필요 없습니다.

### 3. 디버그 키스토어 SHA-1 등록 (Google 로그인 필수)
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
./gradlew signingReport
```
`Variant: debug`의 `SHA1:` 값을 Firebase 콘솔 → 프로젝트 설정 → 해당 Android 앱 →
**SHA 인증서 지문 추가**에 붙여넣습니다. (참고용 예시 형식: `1A:2D:C0:...`처럼
콜론으로 구분된 40자리 16진수 값이며, 키스토어마다 값이 다르므로 반드시 본인 환경에서
직접 확인해야 합니다.)

### 4. Google 로그인 활성화
Firebase 콘솔 → Authentication → Sign-in method → **Google** 제공업체 사용 설정 →
프로젝트 지원 이메일 지정.

### 5. Firestore 생성 + 보안 규칙 배포
Firebase 콘솔 → Firestore Database → 데이터베이스 만들기 → **프로덕션 모드** 선택.
그 다음 로컬에서:
```bash
cd firebase
npx firebase login
npx firebase deploy --only firestore:rules --project <실제 프로젝트 ID>
```

위 5단계를 마치면 앱을 다시 빌드·설치할 때 데모 배너 없이 실제 Firebase 모드로
동작합니다. 두 사람이 각자 Google 계정으로 로그인해 초대코드로 여행을 공유하면 됩니다.

## 빌드·실행

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

- 결과 APK: `app/build/outputs/apk/debug/app-debug.apk`
- `local.properties.example`을 복사해 `local.properties`로 만들고 `sdk.dir`을 본인
  Android SDK 경로로 채우세요(Android Studio로 열면 자동 생성됨).

### Firestore 보안 규칙 테스트 (firestore.rules를 건드렸을 때만)
```bash
cd firebase
npm install   # 최초 1회
npm test
```
Firebase Local Emulator Suite로 소유자/구성원/비구성원/승인 전 참여자의 허용·차단
흐름을 검증합니다(네트워크 접속·실제 프로젝트 불필요).

## 서버 환경변수(.env)가 필요 없는 이유

이 앱은 자체 백엔드 서버가 없습니다. Firebase Authentication과 Cloud Firestore를
클라이언트 SDK로 직접 호출하며, 필요한 설정값(API 키, 프로젝트 ID 등)은 전부
`google-services.json` 안에 들어 있어 별도의 `.env` 파일이 필요하지 않습니다.
Google Maps 길찾기도 API 키 없는 외부 Intent(`geo:`/`google.navigation:` 대신
표준 Maps 딥링크)만 사용합니다.

## 프로젝트 구조 개요

```
app/src/main/kotlin/com/jeongmin/honeymoondoctor/
  core/        # 테마, 네비게이션, DI, 위치/네트워크/알림 유틸, 데모 모드 관리
  data/        # Demo(DataStore/Room)·Firebase(Firestore) 이중 구현 리포지토리
  domain/      # 모델, 리포지토리 인터페이스, 순수 유스케이스(계산 로직)
  feature/     # 화면별 Compose UI + ViewModel (홈/일정/주변/경비/전체 하위 메뉴들)
docs/
  SPEC.md            # 원본 요구사항 전문
  PROGRESS.md        # 개발 경과, 설계 결정, 다음 단계
  QA_CHECKLIST.md    # 수동 테스트 시나리오
  FEATURE_STATUS.md  # 기능별 구현 완료/미완료 현황
  templates/         # 장소 가져오기용 TSV/JSON 템플릿(41개 장소용, 빈 값)
firebase/            # firestore.rules Emulator 테스트(Node, 앱 본체와 별도)
```

## 핵심 아키텍처: Demo/Firebase 이중 구현

`domain/repository/*Repository` 인터페이스마다 `data/*/Demo*Repository`(로컬 저장,
계정 불필요)와 `data/*/Firebase*Repository`(Firestore) 두 구현이 있고,
`core/di/RepositoryModule`이 `DemoModeManager.isDemoMode`를 보고 런타임에 하나만
지연 생성합니다. 데모 모드에서는 Firebase 관련 클래스가 아예 인스턴스화되지 않습니다.

## 안전 원칙

- 예약번호·PIN은 목록에서 항상 마스킹되고 상세 화면에서만 확인·복사할 수 있습니다.
- 초대코드는 원문이 아니라 SHA-256 해시만 Firestore에 저장됩니다.
- 바우처 원본 파일은 각 기기 내부 저장소에만 저장되며 Firestore에는 존재 여부조차
  기록하지 않습니다. 부부 간 바우처 파일 동기화는 이번 버전 범위 밖입니다.
- 위치는 화면 진입 또는 Pull-to-Refresh 시에만 1회 취득하며, 백그라운드 위치 추적은
  하지 않습니다.
