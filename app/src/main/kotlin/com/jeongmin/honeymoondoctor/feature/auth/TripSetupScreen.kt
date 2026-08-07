package com.jeongmin.honeymoondoctor.feature.auth

import androidx.compose.foundation.layout.Arrangement
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
import com.jeongmin.honeymoondoctor.domain.model.AuthUser

@Composable
fun TripSetupScreen(
    user: AuthUser,
    pendingJoinTripId: String?,
    createError: String?,
    onCreateTrip: () -> Unit,
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
            Text("참여 요청을 보냈습니다. 소유자 승인을 기다리는 중입니다.", style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onCancelPendingJoin) { Text("요청 취소") }
            return@Column
        }

        Button(onClick = onCreateTrip, modifier = Modifier.fillMaxWidth()) {
            Text("새 여행 만들기")
        }
        createError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

        var inviteCode by remember { mutableStateOf("") }
        var joinError by remember { mutableStateOf<String?>(null) }
        Text("초대코드로 참여하기", style = MaterialTheme.typography.titleMedium)
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
                onRequestToJoin(inviteCode) { result ->
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
