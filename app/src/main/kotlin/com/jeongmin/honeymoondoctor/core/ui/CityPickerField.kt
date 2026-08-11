package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeongmin.honeymoondoctor.domain.model.City
import java.time.LocalDate
import java.util.UUID

/**
 * 도시 선택 드롭다운 + "새 도시 추가". 시드로 미리 채워둔 도시가 없어졌으므로(범용화),
 * 일정·장소·경비 편집 화면 어디서든 이 자리에서 바로 새 도시를 만들 수 있어야 한다.
 */
@Composable
fun CityPickerField(
    selectedCityId: String?,
    cities: List<City>,
    onSelect: (City?) -> Unit,
    onCreateCity: (City) -> Unit,
    label: String = "도시",
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val selectedLabel = cities.firstOrNull { it.id == selectedCityId }?.displayName ?: "선택 안 함"

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("선택 안 함") },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            cities.forEach { city ->
                DropdownMenuItem(
                    text = { Text(city.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(city)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("+ 새 도시 추가") },
                onClick = {
                    expanded = false
                    showCreateDialog = true
                },
            )
        }
    }

    if (showCreateDialog) {
        CityFormDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { newCity ->
                onCreateCity(newCity)
                onSelect(newCity)
                showCreateDialog = false
            },
        )
    }
}

/**
 * 도시 추가·수정 다이얼로그.
 *
 * 체류 기간(시작일·종료일)은 선택 입력이다. 이걸 채워야 홈 화면이 "지금 어느 도시에
 * 있는지"를 판단해 현지 시각을 그 도시 시간대로 보여준다(채우지 않으면 한국 시각).
 * 두 날짜는 항상 함께 저장한다 — 한쪽만 있으면 기간 판정이 불가능해 어차피 무시된다.
 */
@Composable
fun CityFormDialog(
    onDismiss: () -> Unit,
    onConfirm: (City) -> Unit,
    initial: City? = null,
) {
    var displayName by remember { mutableStateOf(initial?.displayName.orEmpty()) }
    var countryCode by remember { mutableStateOf(initial?.countryCode.orEmpty()) }
    var timeZoneId by remember { mutableStateOf(initial?.timeZoneId ?: "Asia/Seoul") }

    val initialStart = initial?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val initialEnd = initial?.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    var hasStayPeriod by remember { mutableStateOf(initialStart != null && initialEnd != null) }
    var startDate by remember { mutableStateOf(initialStart ?: LocalDate.now()) }
    var endDate by remember { mutableStateOf(initialEnd ?: initialStart ?: LocalDate.now()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "새 도시 추가" else "도시 수정") },
        text = {
            // 체류 기간을 켜면 필드가 늘어나 작은 화면에서 잘리므로 스크롤을 둔다.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("도시명 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = countryCode,
                    onValueChange = { countryCode = it.uppercase().take(2) },
                    label = { Text("국가코드 (예: KR, FR)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = timeZoneId,
                    onValueChange = { timeZoneId = it },
                    label = { Text("시간대 (예: Asia/Seoul)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text("체류 기간 입력", modifier = Modifier.weight(1f))
                    Switch(checked = hasStayPeriod, onCheckedChange = { hasStayPeriod = it })
                }
                Text(
                    text = "체류 기간을 넣으면 그 기간 동안 홈 화면의 현지 시각이 이 도시 시간대로 표시됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasStayPeriod) {
                    DateField(
                        label = "체류 시작일",
                        date = startDate,
                        onDateChange = { picked ->
                            startDate = picked
                            if (endDate.isBefore(picked)) endDate = picked
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    DateField(
                        label = "체류 종료일",
                        date = endDate,
                        onDateChange = { picked ->
                            endDate = picked
                            if (picked.isBefore(startDate)) startDate = picked
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = displayName.isNotBlank(),
                onClick = {
                    onConfirm(
                        City(
                            id = initial?.id ?: "city-${UUID.randomUUID()}",
                            displayName = displayName.trim(),
                            countryCode = countryCode.trim(),
                            timeZoneId = timeZoneId.trim().ifBlank { "Asia/Seoul" },
                            startDate = if (hasStayPeriod) startDate.toString() else null,
                            endDate = if (hasStayPeriod) endDate.toString() else null,
                            referenceLatitude = initial?.referenceLatitude,
                            referenceLongitude = initial?.referenceLongitude,
                            notes = initial?.notes,
                        ),
                    )
                },
            ) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
