package com.jeongmin.honeymoondoctor.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class OnboardingPage(val emoji: String, val title: String, val body: String)

/**
 * 로그인하면 곧바로 여행 생성 폼이라 이 앱이 뭘 해주는지 설명이 없다는 백로그 3-3l.
 * 커스텀 일러스트를 만들 디자이너가 없어(백로그 3-3m), 이모지를 큰 아이콘처럼 써서
 * "동행일기"의 따뜻한 톤을 유지하면서도 비용 없이 각 페이지를 구분한다.
 *
 * 한 번만 보여준다 — [AuthGate]가 [AuthGateViewModel.hasSeenOnboarding]으로 게이팅하고,
 * [onDone]이 그 값을 true로 저장한다. 로그인 전 단계이므로 데모 모드·실계정 여부와
 * 무관하게 항상 같은 순서로 뜬다.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val pages = listOf(
        OnboardingPage(
            emoji = "🧳💕",
            title = "동행일기",
            body = "둘이 함께 쓰는 여행 일기.\n일정, 예약, 준비물, 경비, 주변 장소를 한 곳에서 관리해요.",
        ),
        OnboardingPage(
            emoji = "📅💸",
            title = "따로 적지 않아도 돼요",
            body = "일정이 겹치면 바로 경고하고, 공동 지출은 1/2 정산까지 자동으로 계산해요.",
        ),
        OnboardingPage(
            emoji = "📍📖",
            title = "위치를 공유하고, 기록으로 남겨요",
            body = "급할 때 서로 위치를 확인할 수 있고, 다녀온 여행은 원하면 다른 커플에게 공개할 수 있어요.",
        ),
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone, enabled = !isLastPage) {
                Text(if (isLastPage) "" else "건너뛰기")
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            val item = pages[page]
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(item.emoji, style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(32.dp))
                Text(
                    item.title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    item.body,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pages.indices.forEach { index ->
                val active = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .size(if (active) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (isLastPage) {
                    onDone()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (isLastPage) "시작하기" else "다음") }
    }
}
