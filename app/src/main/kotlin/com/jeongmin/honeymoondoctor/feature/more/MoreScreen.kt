package com.jeongmin.honeymoondoctor.feature.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jeongmin.honeymoondoctor.BuildConfig
import com.jeongmin.honeymoondoctor.core.auth.requestGoogleIdToken
import com.jeongmin.honeymoondoctor.core.ui.SectionHeader
import com.jeongmin.honeymoondoctor.core.ui.TabHeader
import kotlinx.coroutines.launch

/** 메뉴를 묶는 4개 구역. 나열 순서가 곧 화면 표시 순서다. */
private enum class MoreSection(val title: String) {
    TRIP("여행"),
    TOGETHER("함께"),
    DATA("데이터"),
    APP("앱"),
}

/**
 * 전체 탭 메뉴(스펙 6장). 예약함·준비물·결정함·여행 정보는 실제 화면으로 연결됐고,
 * 긴급상황은 아직 자리표시자다.
 *
 * 백로그(벤치마킹): 13개 항목을 아이콘·그룹 없이 한 줄씩 나열하면 스캔성이 떨어져
 * [section]·[icon]을 추가해 구역별로 묶었다.
 */
private data class MoreMenu(val label: String, val onClickKey: String?, val section: MoreSection, val icon: ImageVector)

@Composable
fun MoreScreen(
    isDemoMode: Boolean,
    onNavigateToTripInfo: () -> Unit,
    onNavigateToReservations: () -> Unit,
    onNavigateToChecklist: () -> Unit,
    onNavigateToDecisions: () -> Unit,
    onNavigateToPlaceImport: () -> Unit,
    onNavigateToSyncStatus: () -> Unit,
    onNavigateToPublicTrips: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTogether: () -> Unit,
    onSwitchTrip: () -> Unit,
    onExportBackup: (android.net.Uri) -> Unit,
    backupMessage: String? = null,
    onBackupMessageShown: () -> Unit = {},
    onResetDemoData: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    onRetryDeleteAfterReauth: (String) -> Unit,
    onDismissDeleteAccountError: () -> Unit,
    deleteAccountState: DeleteAccountUiState = DeleteAccountUiState.Idle,
    pendingJoinRequestCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val backupCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(onExportBackup) }
    // 목록 화면이라 스낵바 호스트가 없어 백업 결과는 토스트로 알린다.
    LaunchedEffect(backupMessage) {
        if (backupMessage != null) {
            android.widget.Toast.makeText(context, backupMessage, android.widget.Toast.LENGTH_SHORT).show()
            onBackupMessageShown()
        }
    }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val menuItems = listOf(
        MoreMenu("여행 바꾸기", "switch_trip", MoreSection.TRIP, Icons.Filled.SwapHoriz),
        MoreMenu("여행 정보 및 구성원", "trip_info", MoreSection.TRIP, Icons.Filled.Group),
        MoreMenu("여행 둘러보기", "public_trips", MoreSection.TRIP, Icons.Filled.Public),
        MoreMenu("우리 위치", "together", MoreSection.TOGETHER, Icons.Filled.LocationOn),
        MoreMenu("예약함", "reservations", MoreSection.TOGETHER, Icons.Filled.ConfirmationNumber),
        MoreMenu("준비물", "checklist", MoreSection.TOGETHER, Icons.Filled.Checklist),
        MoreMenu("결정함", "decisions", MoreSection.TOGETHER, Icons.Filled.HowToVote),
        MoreMenu("긴급상황", null, MoreSection.TOGETHER, Icons.Filled.WarningAmber),
        MoreMenu("장소 가져오기·내보내기", "place_import", MoreSection.DATA, Icons.Filled.ImportExport),
        MoreMenu("전체 백업 내보내기", "backup", MoreSection.DATA, Icons.Filled.CloudUpload),
        MoreMenu("동기화 상태", "sync_status", MoreSection.DATA, Icons.Filled.Sync),
        MoreMenu("버전 정보", "about", MoreSection.APP, Icons.Filled.Info),
        MoreMenu("설정", "settings", MoreSection.APP, Icons.Filled.Settings),
    )
    val menusBySection = menuItems.groupBy { it.section }
    LazyColumn(modifier = modifier.padding(vertical = 8.dp)) {
        item {
            TabHeader(Icons.Filled.Menu, "전체", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        MoreSection.entries.forEach { section ->
            val menusInSection = menusBySection[section].orEmpty()
            if (menusInSection.isEmpty()) return@forEach
            item {
                SectionHeader(
                    title = section.title,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(menusInSection) { menu ->
                val onClick: (() -> Unit)? = when (menu.onClickKey) {
                    "backup" -> {
                        {
                            backupCreator.launch(
                                "donghaeng-backup-${java.time.LocalDate.now()}.json",
                            )
                        }
                    }
                    "reservations" -> onNavigateToReservations
                    "checklist" -> onNavigateToChecklist
                    "decisions" -> onNavigateToDecisions
                    "trip_info" -> onNavigateToTripInfo
                    "place_import" -> onNavigateToPlaceImport
                    "sync_status" -> onNavigateToSyncStatus
                    "public_trips" -> onNavigateToPublicTrips
                    "about" -> onNavigateToAbout
                    "settings" -> onNavigateToSettings
                    "together" -> onNavigateToTogether
                    "switch_trip" -> onSwitchTrip
                    else -> null
                }
                ListItem(
                    headlineContent = { Text(menu.label) },
                    leadingContent = { Icon(menu.icon, contentDescription = null) },
                    supportingContent = when {
                        menu.onClickKey == "switch_trip" -> { { Text("다른 여행을 보거나 새로 만듭니다") } }
                        // 목록에서 바로 버전을 읽을 수 있게 한다(들어가지 않아도 확인 가능).
                        menu.onClickKey == "about" ->
                            { { Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") } }
                        onClick == null -> { { Text("추후 단계에서 제공") } }
                        else -> null
                    },
                    trailingContent = if (menu.onClickKey == "trip_info" && pendingJoinRequestCount > 0) {
                        { Text("대기 중인 참여 요청 ${pendingJoinRequestCount}건", color = MaterialTheme.colorScheme.error) }
                    } else {
                        null
                    },
                    modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
                )
                HorizontalDivider()
            }
        }
        if (isDemoMode) {
            item {
                ListItem(
                    headlineContent = { Text("데모 데이터 초기화") },
                    modifier = Modifier.clickable(onClick = onResetDemoData),
                )
            }
        } else {
            item {
                ListItem(
                    headlineContent = { Text("로그아웃") },
                    modifier = Modifier.clickable { showLogoutConfirm = true },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("회원 탈퇴", color = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { showDeleteAccountConfirm = true },
                )
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("로그아웃") },
            text = { Text("로그아웃하시겠어요?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLogout()
                        showLogoutConfirm = false
                    },
                ) { Text("로그아웃") }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("취소") } },
        )
    }

    if (showDeleteAccountConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirm = false },
            title = { Text("회원 탈퇴") },
            text = {
                Text(
                    "회원 탈퇴하시겠어요? 되돌릴 수 없습니다.\n" +
                        "혼자인 여행은 모든 데이터가 삭제되고, 동반자가 있는 여행은 소유권이 " +
                        "동반자에게 넘어갑니다.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAccount()
                        showDeleteAccountConfirm = false
                    },
                ) { Text("탈퇴", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteAccountConfirm = false }) { Text("취소") } },
        )
    }

    when (deleteAccountState) {
        DeleteAccountUiState.NeedsReauth -> {
            AlertDialog(
                onDismissRequest = onDismissDeleteAccountError,
                title = { Text("다시 로그인해주세요") },
                text = { Text("보안을 위해 회원 탈퇴 전 다시 로그인해야 합니다.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching { requestGoogleIdToken(context) }
                                    .onSuccess { onRetryDeleteAfterReauth(it) }
                                    .onFailure { onDismissDeleteAccountError() }
                            }
                        },
                    ) { Text("다시 로그인") }
                },
                dismissButton = { TextButton(onClick = onDismissDeleteAccountError) { Text("취소") } },
            )
        }
        is DeleteAccountUiState.Failed -> {
            AlertDialog(
                onDismissRequest = onDismissDeleteAccountError,
                title = { Text("회원 탈퇴 실패") },
                text = { Text(deleteAccountState.message) },
                confirmButton = { TextButton(onClick = onDismissDeleteAccountError) { Text("확인") } },
            )
        }
        DeleteAccountUiState.Idle, DeleteAccountUiState.InProgress -> Unit
    }
}
