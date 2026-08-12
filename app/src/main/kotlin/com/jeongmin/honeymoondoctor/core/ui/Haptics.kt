package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * 체크·저장·삭제처럼 "이 동작이 실제로 먹혔다"를 확인해 주는 순간의 촉각 피드백
 * (백로그 3-3o). LongPress 상수를 쓰는 이유는 뜻이 아니라 세기다 — 플랫폼이 노출한
 * 몇 안 되는 표준 강도 중 짧은 확인 탭에 가장 가까운 강도라 Compose 예제에서도
 * 범용 확인 피드백으로 흔히 쓰인다.
 */
fun HapticFeedback.confirm() = performHapticFeedback(HapticFeedbackType.LongPress)
