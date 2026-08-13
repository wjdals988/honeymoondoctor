package com.jeongmin.honeymoondoctor.feature.publictrip

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.core.time.koreanZoneLabel
import com.jeongmin.honeymoondoctor.core.ui.AppCard
import com.jeongmin.honeymoondoctor.core.ui.CardTone
import com.jeongmin.honeymoondoctor.core.ui.EmptyState
import com.jeongmin.honeymoondoctor.core.ui.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicTripDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PublicTripDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val summary = state.summary

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(summary?.name ?: "여행 둘러보기") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxWidth()) {
            item {
                if (summary != null) {
                    Text(
                        "${summary.startDate} ~ ${summary.endDate}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                if (state.cities.isNotEmpty()) {
                    SectionHeader(
                        title = "도시",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Text(
                        state.cities.joinToString(", ") { it.displayName },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                HorizontalDivider()
                SectionHeader(
                    title = "일정",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (state.itinerary.isEmpty() && !state.loading) {
                item {
                    EmptyState(title = "공개된 일정이 없습니다.")
                }
            }
            // 벤치마킹: 일정 탭은 이미 날짜별로 묶여 있는데(백로그 3-1) 여행 둘러보기 상세는
            // 평면 목록이라 같은 앱 안에서 패턴이 갈렸다. 같은 방식으로 날짜 헤더를 끼운다.
            val grouped = state.itinerary.groupBy { LocalTimes.toLocalDate(it.startAt, it.timeZone) }
            grouped.forEach { (date, dayItems) ->
                item(key = "day-$date") {
                    SectionHeader(
                        title = "${date.monthValue}월 ${date.dayOfMonth}일",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(dayItems, key = { it.id }) { item ->
                    val cityName = state.cities.firstOrNull { it.id == item.cityId }?.displayName
                    AppCard(
                        tone = CardTone.Neutral,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${LocalTimes.formatTime(item.startAt, item.timeZone)} " +
                                "(${koreanZoneLabel(item.timeZone)}) · ${item.type.labelKo}" +
                                (cityName?.let { " · $it" }.orEmpty()) +
                                (item.location?.let { " · $it" }.orEmpty()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
