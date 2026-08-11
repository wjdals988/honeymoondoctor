package com.jeongmin.honeymoondoctor.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
fun AboutScreen(onNavigateBack: () -> Unit) {
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
