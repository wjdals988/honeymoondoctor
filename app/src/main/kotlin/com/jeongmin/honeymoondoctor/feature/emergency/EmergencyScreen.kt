package com.jeongmin.honeymoondoctor.feature.emergency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.ui.AppCard
import com.jeongmin.honeymoondoctor.core.ui.CardTone
import com.jeongmin.honeymoondoctor.core.ui.EmptyState
import com.jeongmin.honeymoondoctor.core.ui.SectionHeader
import com.jeongmin.honeymoondoctor.core.ui.dialNumber
import com.jeongmin.honeymoondoctor.core.ui.openUrl
import com.jeongmin.honeymoondoctor.domain.model.EmergencyContacts
import com.jeongmin.honeymoondoctor.domain.model.EmergencyNumbers

/**
 * 긴급상황(스펙 6장). 종전에는 "추후 단계에서 제공" 자리표시자였다.
 *
 * 급할 때 쓰는 화면이라 세 가지를 지킨다: (1) 한국어로 안내받을 수 있는 영사콜센터를
 * 나라와 무관하게 맨 위에 두고, (2) 번호를 눌러도 바로 걸리지 않고 다이얼러에만 올리며
 * (오탭으로 긴급번호가 걸리는 사고 방지), (3) 번호가 바뀔 수 있음을 명시하고 공식
 * 출처(외교부 해외안전여행)를 함께 연결한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(
    onNavigateBack: () -> Unit,
    viewModel: EmergencyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("긴급상황") },
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard(tone = CardTone.Warn, modifier = Modifier.fillMaxWidth()) {
                    Text("영사콜센터 (24시간·한국어)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "해외에서 사건·사고를 겪거나 여권을 잃어버렸을 때 한국어로 안내받는 창구입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    FilledTonalButton(
                        onClick = { dialNumber(context, EmergencyContacts.CONSULAR_CALL_CENTER) },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = null)
                        Text(
                            text = EmergencyContacts.CONSULAR_CALL_CENTER,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            item { SectionHeader(title = "이 여행에서 갈 나라") }

            if (uiState.countries.isEmpty() && !uiState.loading) {
                item {
                    EmptyState(
                        title = "등록된 도시가 없습니다",
                        description = "전체 → 여행 정보에서 도시를 추가하면 그 나라의 긴급번호가 여기 표시됩니다.",
                    )
                }
            }

            items(uiState.countries, key = { it.countryCode }) { country ->
                CountryCard(country = country, onDial = { dialNumber(context, it) })
            }

            if (uiState.unknownCityNames.isNotEmpty()) {
                item {
                    Text(
                        text = "${uiState.unknownCityNames.joinToString(", ")}는 아직 번호 목록에 없습니다 — " +
                            "아래 해외안전여행에서 확인해 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Text(
                    text = "번호는 바뀔 수 있습니다. 출발 전 외교부 해외안전여행에서 다시 확인하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(onClick = { openUrl(context, EmergencyContacts.SAFE_TRAVEL_URL) }) {
                    Text("외교부 해외안전여행 열기")
                }
            }
        }
    }
}

@Composable
private fun CountryCard(country: EmergencyNumbers, onDial: (String) -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(country.countryName, style = MaterialTheme.typography.titleMedium)
        listOfNotNull(
            country.unified?.let { "통합" to it },
            country.police?.let { "경찰" to it },
            country.ambulance?.let { "구급" to it },
        ).forEach { (label, number) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = number,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(end = 8.dp),
                )
                IconButton(onClick = { onDial(number) }) {
                    Icon(Icons.Filled.Call, contentDescription = "$label ${number} 걸기")
                }
            }
        }
    }
}
