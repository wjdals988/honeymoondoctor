package com.jeongmin.honeymoondoctor.feature.publictrip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicTripListScreen(
    onNavigateBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PublicTripListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("여행 둘러보기") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            if (state.isDemoMode) {
                item {
                    Text(
                        "데모 모드 · 다른 사용자의 여행은 볼 수 없습니다. 내가 공개한 여행만 표시됩니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (state.trips.isEmpty()) {
                item {
                    Text(
                        "아직 공개된 여행이 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(state.trips) { trip ->
                ListItem(
                    headlineContent = { Text(trip.name) },
                    supportingContent = {
                        Text(
                            "${trip.startDate} ~ ${trip.endDate}" +
                                trip.cityNames.takeIf { it.isNotEmpty() }?.let { " · ${it.joinToString(", ")}" }.orEmpty() +
                                " · 일정 ${trip.itineraryCount}건",
                        )
                    },
                    modifier = Modifier.clickable { onOpenDetail(trip.tripId) },
                )
                HorizontalDivider()
            }
        }
    }
}
