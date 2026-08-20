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
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import com.jeongmin.honeymoondoctor.core.time.koreanZoneLabel
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.CityPreset
import com.jeongmin.honeymoondoctor.domain.model.CityPresets
import java.util.UUID
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
    /** 초대 딥링크로 들어왔을 때 초대코드 입력창을 미리 채운다. 사용자는 여전히 "참여 요청
     * 보내기"를 직접 눌러야 한다 — 링크만으로 자동 참여시키면 잘못 탭한 링크로도 요청이
     * 나가버릴 수 있다. */
    prefillInviteCode: String? = null,
    onCreateTrip: (NewTripDraft) -> Unit,
    onRequestToJoin: (String, (Result<Unit>) -> Unit) -> Unit,
    onCancelPendingJoin: () -> Unit,
    modifier: Modifier = Modifier,
    /** 여행 목록에서 들어왔을 때만 준다. 첫 여행을 만들 때는 돌아갈 곳이 없어 null이다. */
    onNavigateBack: (() -> Unit)? = null,
) {
    Column(modifier = modifier.padding(24.dp)) {
        if (onNavigateBack != null) {
            TextButton(onClick = onNavigateBack, modifier = Modifier.padding(bottom = 4.dp)) {
                Text("← 여행 목록")
            }
        }
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
        var destinationQuery by remember { mutableStateOf("") }
        var pickedDestination by remember { mutableStateOf<CityPreset?>(null) }
        val destinationSuggestions = remember(destinationQuery, pickedDestination) {
            if (pickedDestination?.displayName == destinationQuery) emptyList()
            else CityPresets.search(destinationQuery)
        }

        SectionHeader(title = "새 여행 만들기")
        // 목적지를 맨 위에 둔다 — 종전에는 아예 묻지 않아 도시 없이 여행이 시작됐고,
        // 그러면 홈 현지 시각·주변 거리 계산·일정 기본 시간대가 전부 서울로 굳었다.
        OutlinedTextField(
            value = destinationQuery,
            onValueChange = {
                destinationQuery = it
                pickedDestination = null
            },
            label = { Text("어디로 가시나요?") },
            supportingText = {
                Text(
                    pickedDestination?.let { "${it.countryName} · ${koreanZoneLabel(it.timeZoneId)} 기준으로 표시됩니다" }
                        ?: "도시를 고르면 현지 시각과 주변 장소 거리가 그 도시 기준이 됩니다",
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        destinationSuggestions.forEach { preset ->
            ListItem(
                headlineContent = { Text(preset.displayName) },
                supportingContent = { Text("${preset.countryName} · ${koreanZoneLabel(preset.timeZoneId)}") },
                modifier = Modifier.clickable {
                    pickedDestination = preset
                    destinationQuery = preset.displayName
                    // 이름을 아직 안 적었으면 "도쿄 여행"처럼 채워 준다(그대로 고칠 수 있다).
                    if (tripName.isBlank()) tripName = "${preset.displayName} 여행"
                },
            )
        }
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
                        // 목록에서 고른 도시만 저장한다. 직접 타이핑한 문자열은 시간대를
                        // 알 수 없어(오타면 ZoneId.of가 실패) 도시로 만들지 않는다.
                        firstCity = pickedDestination?.let { preset ->
                            City(
                                id = "city-${UUID.randomUUID()}",
                                displayName = preset.displayName,
                                countryCode = preset.countryCode,
                                timeZoneId = preset.timeZoneId,
                                startDate = null,
                                endDate = null,
                                referenceLatitude = preset.latitude,
                                referenceLongitude = preset.longitude,
                            )
                        },
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

        var inviteCode by remember { mutableStateOf(prefillInviteCode.orEmpty()) }
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
