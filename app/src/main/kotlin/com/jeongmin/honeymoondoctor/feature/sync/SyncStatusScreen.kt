package com.jeongmin.honeymoondoctor.feature.sync

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(
    onNavigateBack: () -> Unit,
    viewModel: SyncStatusViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 알림/정확 알람 권한은 시스템 설정에서 바뀔 수 있어, 화면이 다시 보일 때마다 재확인한다.
    var notificationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    var exactAlarmAllowed by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true,
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                exactAlarmAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationGranted = granted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("동기화 상태") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        val status = uiState.status

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (status?.isOnline == true) "온라인" else "오프라인",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (status?.isOnline == true) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = if (status?.isDemoMode == true) {
                            "데모 모드 — 이 기기에만 저장되며 원격 동기화가 없습니다."
                        } else {
                            "마지막 동기화: " + (status?.lastSyncAt?.let { formatSyncTime(it) } ?: "아직 없음")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (status?.isDemoMode != true) {
                        Text(
                            text = "동기화 대기 변경: ${status?.pendingChangeCount ?: 0}건",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("중요 일정 알림", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "일정 시작 24시간·3시간·1시간 전에 로컬 알림을 보냅니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    PermissionRow(
                        label = "알림 권한",
                        granted = notificationGranted,
                        deniedMessage = "거절 시 앱의 핵심 기능은 그대로 동작하며, 일정 알림만 표시되지 않습니다.",
                        onRequest = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onOpenSettings = { context.startActivity(appNotificationSettingsIntent(context.packageName)) },
                    )

                    PermissionRow(
                        label = "정확한 알람 권한",
                        granted = exactAlarmAllowed,
                        deniedMessage = "거절 시 WorkManager 기반 알림으로 대체됩니다. 정시가 아닌 " +
                            "몇 분~수십 분 정도 지연될 수 있습니다.",
                        onRequest = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }
                        },
                        onOpenSettings = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    deniedMessage: String,
    onRequest: () -> Unit,
    onOpenSettings: (() -> Unit)?,
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                text = if (granted) "허용됨" else "거절됨",
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (!granted) {
            Text(
                text = deniedMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton(onClick = onRequest) { Text("권한 요청") }
                if (onOpenSettings != null) {
                    TextButton(onClick = onOpenSettings) { Text("설정으로 이동") }
                }
            }
        }
    }
}

private fun appNotificationSettingsIntent(packageName: String): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    }

private fun formatSyncTime(instant: Instant): String =
    "${LocalTimes.formatDate(instant, "Asia/Seoul")} ${LocalTimes.formatTime(instant, "Asia/Seoul")} (한국 시각)"
