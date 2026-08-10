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
            items(state.itinerary) { item ->
                val cityName = state.cities.firstOrNull { it.id == item.cityId }?.displayName
                AppCard(
                    tone = CardTone.Neutral,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${LocalTimes.formatDate(item.startAt, item.timeZone)} " +
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
