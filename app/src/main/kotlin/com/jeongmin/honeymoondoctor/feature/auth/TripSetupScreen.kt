package com.jeongmin.honeymoondoctor.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeongmin.honeymoondoctor.core.ui.DateField
import com.jeongmin.honeymoondoctor.core.ui.DropdownSelector
import com.jeongmin.honeymoondoctor.core.ui.SectionHeader
import com.jeongmin.honeymoondoctor.domain.model.AuthUser
import com.jeongmin.honeymoondoctor.domain.model.JoinRequestStatus
import com.jeongmin.honeymoondoctor.domain.model.NewTripDraft
import com.jeongmin.honeymoondoctor.domain.model.TravelCurrency
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TripSetupScreen(
    user: AuthUser,
    pendingJoinTripId: String?,
    joinRequestStatus: JoinRequestStatus?,
    createError: String?,
    onCreateTrip: (NewTripDraft) -> Unit,
    onRequestToJoin: (String, (Result<Unit>) -> Unit) -> Unit,
    onCancelPendingJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(24.dp)) {
        Text("${user.displayName}님, 환영합니다", style = MaterialTheme.typography.headlineMedium)
        Text(
            "새 여행을 만들거나, 파트너에게 받은 초대코드로 참여하세요.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        if (pendingJoinTripId != null) {
            when (joinRequestStatus) {
                JoinRequestStatus.REJECTED -> {
                    Text(
                        "참여 요청이 거절되었습니다.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onCancelPendingJoin) { Text("다른 초대코드로 다시 시도") }
                }
                else -> {
                    Text("참여 요청을 보냈습니다. 소유자 승인을 기다리는 중입니다.", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = onCancelPendingJoin) { Text("요청 취소") }
                }
            }
            return@Column
        }

        var tripName by remember { mutableStateOf("") }
        var startDate by remember { mutableStateOf(LocalDate.now()) }
        var endDate by remember { mutableStateOf(LocalDate.now().plusDays(6)) }
        var currency by remember { mutableStateOf(TravelCurrency.KRW) }

        SectionHeader(title = "새 여행 만들기")
        OutlinedTextField(
            value = tripName,
            onValueChange = { tripName = it },
            label = { Text("여행 이름 *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        DateField(
            label = "시작일",
            date = startDate,
            onDateChange = { date ->
                startDate = date
                if (endDate.isBefore(date)) endDate = date
            },
            modifier = Modifier.padding(top = 8.dp),
        )
        DateField(
            label = "종료일",
            date = endDate,
            onDateChange = { date -> endDate = date },
            modifier = Modifier.padding(top = 8.dp),
        )
        DropdownSelector(
            label = "기본 통화",
            selectedLabel = "${currency.code} (${currency.symbol})",
            options = TravelCurrency.entries,
            optionLabel = { "${it.code} (${it.symbol})" },
            onSelect = { currency = it },
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = {
                onCreateTrip(
                    NewTripDraft(
                        name = tripName.trim(),
                        startDate = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        endDate = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        defaultCurrency = currency.code,
                    ),
                )
            },
            enabled = tripName.isNotBlank() && !endDate.isBefore(startDate),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text("여행 만들기")
        }
        createError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

        var inviteCode by remember { mutableStateOf("") }
        var joinError by remember { mutableStateOf<String?>(null) }
        SectionHeader(title = "초대코드로 참여하기")
        OutlinedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it; joinError = null },
            label = { Text("초대코드") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        Button(
            onClick = {
                onRequestToJoin(inviteCode.trim()) { result ->
                    result.onFailure { joinError = it.message ?: "참여 요청에 실패했습니다." }
                }
            },
            enabled = inviteCode.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("참여 요청 보내기")
        }
        joinError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
