package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 현재 여행이 완료(읽기전용) 상태인지 여부. HoneymoonDoctorAppRoot에서 한 번만 제공하고,
 * FAB·체크박스·삭제 버튼처럼 네비게이션 없이 바로 쓰는 지점들이 이 값을 읽어 스스로를 숨기거나
 * 비활성화한다. 각 화면 ViewModel/파라미터에 매번 꿰어 넣는 것보다 훨씬 적은 변경으로 끝난다.
 */
val LocalTripReadOnly: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadOnlyEditorPanel(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column {
                Text("완료된 여행은 수정할 수 없습니다", style = MaterialTheme.typography.titleMedium)
                Text(
                    "전체 → 여행 정보에서 다시 활성화하면 수정할 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
