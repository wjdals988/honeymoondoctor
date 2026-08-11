package com.jeongmin.honeymoondoctor.feature.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.BuildConfig
import com.jeongmin.honeymoondoctor.R
import com.jeongmin.honeymoondoctor.core.ui.AppCard
import com.jeongmin.honeymoondoctor.core.ui.CardTone
import com.jeongmin.honeymoondoctor.core.ui.SectionHeader
import com.jeongmin.honeymoondoctor.core.version.ReleaseEntry
import com.jeongmin.honeymoondoctor.core.version.ReleaseHistory

/**
 * 버전 정보와 변경 내역. 스토어를 거치지 않고 APK를 직접 설치하는 배포 방식이라
 * "지금 깔린 게 몇 버전인지 / 무엇이 바뀌었는지"를 앱 안에서 볼 수 있어야 한다.
 *
 * 설치된 버전은 [BuildConfig]에서 읽고(실제로 깔린 APK의 값), 변경 내역은
 * [ReleaseHistory] 상수에서 읽는다. 둘이 어긋나면 목록 맨 위 항목에 "현재 설치된 버전"
 * 배지가 붙지 않으므로, 그것만 봐도 히스토리 갱신을 빠뜨린 걸 알 수 있다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateBack: () -> Unit, viewModel: AboutViewModel = hiltViewModel()) {
    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("버전 정보") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth(), tone = CardTone.Highlight) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "버전 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = if (BuildConfig.HAS_FIREBASE_CONFIG) {
                            "동기화 모드 — 두 사람이 같은 여행을 실시간으로 공유합니다."
                        } else {
                            "데모 모드 — 이 기기에만 저장되며 어디로도 동기화되지 않습니다."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                UpdateCard(
                    state = updateState,
                    onRetry = viewModel::checkForUpdate,
                    onOpenRelease = { url ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                )
            }

            item { SectionHeader(title = "변경 내역", modifier = Modifier.padding(top = 8.dp)) }

            items(ReleaseHistory.entries.size) { index ->
                val entry = ReleaseHistory.entries[index]
                ReleaseCard(entry = entry, isInstalled = entry.versionCode == BuildConfig.VERSION_CODE)
            }
        }
    }
}

@Composable
private fun ReleaseCard(entry: ReleaseEntry, isInstalled: Boolean) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        tone = if (isInstalled) CardTone.Highlight else CardTone.Neutral,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "v${entry.versionName} (${entry.versionCode})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = entry.date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isInstalled) {
            Text(
                text = "현재 설치된 버전",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            entry.changes.forEach { change ->
                Row {
                    Text("· ", style = MaterialTheme.typography.bodyMedium)
                    Text(change, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * 업데이트 확인 카드. 스토어를 거치지 않는 배포라 앱이 직접 최신 버전을 알려줘야 한다
 * (이게 없으면 크래시를 고쳐도 옛 버전 사용자에게 도달하지 않는다).
 */
@Composable
private fun UpdateCard(
    state: UpdateCheckState,
    onRetry: () -> Unit,
    onOpenRelease: (String) -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        tone = if (state is UpdateCheckState.UpdateAvailable) CardTone.Warn else CardTone.Neutral,
    ) {
        when (state) {
            UpdateCheckState.Idle, UpdateCheckState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = "업데이트 확인 중…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            UpdateCheckState.UpToDate -> Text(
                text = "최신 버전을 쓰고 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
            )

            is UpdateCheckState.UpdateAvailable -> {
                Text(
                    text = "새 버전 ${state.versionName}이(가) 있습니다.",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "이 앱은 스토어를 거치지 않으므로 새 APK를 직접 내려받아 설치해야 합니다. " +
                        "같은 서명이라 기존 데이터는 그대로 유지됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { onOpenRelease(state.releaseUrl) },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("다운로드 페이지 열기") }
            }

            is UpdateCheckState.Failed -> {
                Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry, modifier = Modifier.padding(top = 4.dp)) { Text("다시 확인") }
            }
        }
    }
}
