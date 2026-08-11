# 서드파티 라이선스

현재 앱에 번들된 서드파티 **에셋**은 없습니다. 폰트는 전 구간 안드로이드 시스템 기본
폰트를 사용합니다.

## 제거된 항목

### Gaegu (손글씨체) — 2026-08-11 제거

`app/src/main/res/font/gaegu_regular.ttf` · `gaegu_bold.ttf`로 번들해 제목 계열
(`display*`/`headline*`)에 쓰다가, 실기기에서 완성도가 떨어져 시스템 기본 폰트로
되돌리고 파일도 지웠습니다(SIL Open Font License 1.1, Copyright 2018 The Gaegu
Project Authors, https://fonts.google.com/specimen/Gaegu).

## 라이브러리

Kotlin·AndroidX·Jetpack Compose·Firebase·Hilt 등 Gradle 의존성은 각자의 라이선스
(대부분 Apache License 2.0)를 따르며, `gradle/libs.versions.toml`에 버전이 명시되어
있습니다.
