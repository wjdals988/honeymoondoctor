package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.jeongmin.honeymoondoctor.domain.model.City
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
 * 도시 추가·수정 다이얼로그. 인라인 추가는 이름·국가코드·시간대만 받는다(날짜·좌표는
 * 나중에 여행 정보 화면에서 채울 수 있게 비워둔다).
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "새 도시 추가" else "도시 수정") },
        text = {
            Column {
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
                            startDate = initial?.startDate,
                            endDate = initial?.endDate,
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
