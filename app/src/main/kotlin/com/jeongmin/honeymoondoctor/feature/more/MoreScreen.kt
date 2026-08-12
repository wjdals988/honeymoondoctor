package com.jeongmin.honeymoondoctor.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jeongmin.honeymoondoctor.BuildConfig
import com.jeongmin.honeymoondoctor.core.auth.requestGoogleIdToken
import kotlinx.coroutines.launch

/**
 * 전체 탭 메뉴(스펙 6장). 예약함·준비물·결정함·여행 정보는 실제 화면으로 연결됐고,
 * 긴급상황·가져오기/내보내기·동기화 상태·설정은 Phase 6~8에서 연결할 자리표시자다.
 */
private data class MoreMenu(val label: String, val onClickKey: String?)

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
    onSwitchTrip: () -> Unit,
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
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val menuItems = listOf(
        MoreMenu("여행 바꾸기", "switch_trip"),
        MoreMenu("예약함", "reservations"),
        MoreMenu("준비물", "checklist"),
        MoreMenu("결정함", "decisions"),
        MoreMenu("긴급상황", null),
        MoreMenu("여행 정보 및 구성원", "trip_info"),
        MoreMenu("장소 가져오기·내보내기", "place_import"),
        MoreMenu("여행 둘러보기", "public_trips"),
        MoreMenu("동기화 상태", "sync_status"),
        MoreMenu("버전 정보", "about"),
        MoreMenu("설정", null),
    )
    LazyColumn(modifier = modifier.padding(vertical = 8.dp)) {
        items(menuItems) { menu ->
            val onClick: (() -> Unit)? = when (menu.onClickKey) {
                "reservations" -> onNavigateToReservations
                "checklist" -> onNavigateToChecklist
                "decisions" -> onNavigateToDecisions
                "trip_info" -> onNavigateToTripInfo
                "place_import" -> onNavigateToPlaceImport
                "sync_status" -> onNavigateToSyncStatus
                "public_trips" -> onNavigateToPublicTrips
                "about" -> onNavigateToAbout
                "switch_trip" -> onSwitchTrip
                else -> null
            }
            ListItem(
                headlineContent = { Text(menu.label) },
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
