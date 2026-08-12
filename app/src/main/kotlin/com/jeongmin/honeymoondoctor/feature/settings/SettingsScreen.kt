package com.jeongmin.honeymoondoctor.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.jeongmin.honeymoondoctor.core.ui.AppCard
import com.jeongmin.honeymoondoctor.core.ui.ChipSelector
import com.jeongmin.honeymoondoctor.core.ui.SectionHeader
import com.jeongmin.honeymoondoctor.data.local.prefs.ThemeMode

/** 출발 여유 선택지. 공항 3시간 전 도착 같은 큰 여유까지 흔한 범위만 늘어놓는다. */
private val leadMinuteOptions = listOf(15, 30, 60, 90, 120, 180)

private val ThemeMode.labelKo: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "시스템 설정"
        ThemeMode.LIGHT -> "라이트"
        ThemeMode.DARK -> "다크"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(title = "화면")
                ChipSelector(
                    label = "테마",
                    options = ThemeMode.entries,
                    selected = uiState.themeMode,
                    optionLabel = { it.labelKo },
                    onSelect = viewModel::setThemeMode,
                )
                Text(
                    text = "이 설정은 이 기기에만 적용됩니다. 함께 여행하는 사람의 화면은 바뀌지 않습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AppCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(title = "이동 일정")
                ChipSelector(
                    label = "출발 여유",
                    options = leadMinuteOptions,
                    selected = uiState.transportLeadMinutes,
                    optionLabel = { minutes ->
                        if (minutes >= 60) {
                            val h = minutes / 60
                            val m = minutes % 60
                            if (m == 0) "${h}시간" else "${h}시간 ${m}분"
                        } else {
                            "${minutes}분"
                        }
                    },
                    onSelect = viewModel::setTransportLeadMinutes,
                )
                Text(
                    text = "홈의 이동 일정에 \"출발 권장 시각\"이 표시됩니다. 이동 시작 시각에서 이 여유를 뺀 " +
                        "시각입니다. 숙소에서 공항·역까지 걸리는 시간을 감안해 정하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
