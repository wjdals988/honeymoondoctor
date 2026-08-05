package com.jeongmin.honeymoondoctor.feature.reservation

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.ui.copyToClipboard
import com.jeongmin.honeymoondoctor.data.local.db.VoucherMetadataEntity
import com.jeongmin.honeymoondoctor.domain.model.maskSecret
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationDetailScreen(
    onNavigateBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: ReservationDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showVoucherDeleteChoice by remember { mutableStateOf(false) }

    val voucherPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::attachVoucher) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("예약 상세") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    uiState.reservation?.let { reservation ->
                        IconButton(onClick = { onEdit(reservation.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "예약 수정")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "예약 삭제")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val reservation = uiState.reservation
        if (uiState.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (reservation == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { Text("예약을 찾을 수 없습니다.") }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(reservation.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = listOfNotNull(
                    reservation.type.labelKo,
                    reservation.status.labelKo,
                    reservation.vendor.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            reservationScheduleLabel(reservation)?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            uiState.linkedItinerary?.let { linked ->
                Text(
                    text = "연결 일정: ${linked.title}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            reservation.estimatedKrw?.let {
                Text(
                    text = "예상 비용 ${NumberFormat.getNumberInstance(Locale.KOREA).format(it)}원",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            HorizontalDivider()

            SecretRow(label = "예약번호", secret = reservation.confirmationCode)
            SecretRow(label = "PIN", secret = reservation.pin)

            reservation.notes?.let { notes ->
                HorizontalDivider()
                Text("메모", style = MaterialTheme.typography.titleSmall)
                Text(notes, style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()

            Text("기기 내 바우처", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "바우처 파일은 이 기기에만 저장되며 동기화되지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            uiState.voucherError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (uiState.vouchers.isEmpty()) {
                Text(
                    text = "이 기기에 저장된 바우처 없음",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                uiState.vouchers.forEach { voucher ->
                    VoucherRow(
                        voucher = voucher,
                        onOpen = {
                            try {
                                context.startActivity(viewModel.buildOpenIntent(voucher))
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, "이 형식을 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = { viewModel.removeVoucher(voucher) },
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    voucherPicker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("바우처 추가 (PDF/JPG/PNG/WEBP, 최대 15MB)") }
        }
    }

    if (showDeleteDialog) {
        val hasVouchers = uiState.vouchers.isNotEmpty()
        val linkedCount = if (uiState.linkedItinerary != null) 1 else 0
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("예약 삭제") },
            text = {
                Text(
                    buildString {
                        if (linkedCount > 0) {
                            appendLine("연결된 일정 ${linkedCount}건의 참조가 해제됩니다(일정 자체는 유지).")
                        }
                        append("이 예약을 삭제할까요?")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    if (hasVouchers) {
                        showVoucherDeleteChoice = true
                    } else {
                        viewModel.delete(deleteVouchers = false, onDeleted = onNavigateBack)
                    }
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("취소") } },
        )
    }

    // 스펙 4장: 예약 삭제 시 연결된 기기 바우처도 삭제할지 별도로 확인한다.
    if (showVoucherDeleteChoice) {
        AlertDialog(
            onDismissRequest = { showVoucherDeleteChoice = false },
            title = { Text("바우처도 삭제할까요?") },
            text = { Text("이 예약에 저장된 기기 바우처 ${uiState.vouchers.size}개를 함께 삭제할지 선택해 주세요.") },
            confirmButton = {
                TextButton(onClick = {
                    showVoucherDeleteChoice = false
                    viewModel.delete(deleteVouchers = true, onDeleted = onNavigateBack)
                }) { Text("바우처도 삭제") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showVoucherDeleteChoice = false
                    viewModel.delete(deleteVouchers = false, onDeleted = onNavigateBack)
                }) { Text("바우처는 남기기") }
            },
        )
    }
}

/** 예약번호·PIN: 기본은 마스킹, "보기"로 원문 확인, "복사"는 원문을 클립보드로. */
@Composable
private fun SecretRow(label: String, secret: String?) {
    val context = LocalContext.current
    var revealed by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = when {
                    secret.isNullOrEmpty() -> "미입력"
                    revealed -> secret
                    else -> maskSecret(secret).orEmpty()
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (!secret.isNullOrEmpty()) {
            TextButton(onClick = { revealed = !revealed }) { Text(if (revealed) "가리기" else "보기") }
            TextButton(onClick = { copyToClipboard(context, label, secret) }) { Text("복사") }
        }
    }
}

@Composable
private fun VoucherRow(
    voucher: VoucherMetadataEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(voucher.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(
                    text = "%.1f MB".format(voucher.sizeBytes / 1024f / 1024f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "${voucher.fileName} 삭제")
            }
        }
    }
}
