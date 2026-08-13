package com.jeongmin.honeymoondoctor.feature.nearby

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.ui.AppCard
import com.jeongmin.honeymoondoctor.core.ui.CardTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: PlaceImportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val exportCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/tab-separated-values"),
    ) { uri -> uri?.let(viewModel::exportTo) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("장소 가져오기·내보내기") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { innerPadding ->
        PlaceImportFormContent(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding),
            header = {
                OutlinedButton(
                    onClick = { exportCreator.launch("honeymoon_places.tsv") },
                    enabled = uiState.placeCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("현재 장소 ${uiState.placeCount}개 TSV로 내보내기") }
            },
        )
    }
}

/**
 * [PlaceImportScreen]의 본문만 떼어낸 것 — "장소 추가" 진입 탭(저장목록 불러오기)에서도 쓴다.
 * 내보내기 버튼은 "추가" 흐름과 무관해 [header] 슬롯으로 분리했다(기존 화면에서만 채움).
 */
@Composable
fun PlaceImportFormContent(
    viewModel: PlaceImportViewModel,
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.loadFile(it, uiState.cities) } }

    if (uiState.loading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Google Maps 저장 목록은 자동으로 읽지 않습니다.\n" +
                        "docs/templates 의 TSV/JSON 템플릿에 채워 가져오세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        filePicker.launch(
                            arrayOf("text/tab-separated-values", "text/plain", "application/json", "text/*"),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("TSV/JSON 파일 선택") }
                header()
            }
        }

        uiState.error?.let { error ->
                item {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
            uiState.importedCount?.let { imported ->
                item {
                    AppCard(
                        tone = CardTone.Highlight,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "가져오기 완료: ${imported}건이 추가됐습니다.")
                    }
                }
            }

            uiState.preview?.let { preview ->
                item {
                    HorizontalDivider()
                    Text(
                        text = "미리보기 — ${uiState.fileName.orEmpty()}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "가져올 행 ${preview.validRows.size}건 · 오류 ${preview.errorRows.size}건 · " +
                            "중복 제외 ${preview.duplicateLineNumbers.size}건",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                items(preview.rows, key = { it.lineNumber }) { row ->
                    val isDuplicate = row.lineNumber in preview.duplicateLineNumbers
                    AppCard(
                        tone = when {
                            row.errors.isNotEmpty() -> CardTone.Warn
                            isDuplicate -> CardTone.Done
                            else -> CardTone.Neutral
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "행 ${row.lineNumber}: ${row.place?.name ?: "(파싱 실패)"}" +
                                if (isDuplicate) " — 중복(제외됨)" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        row.errors.forEach { message ->
                            Text(
                                text = "• $message",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
                item {
                    Button(
                        onClick = viewModel::confirmImport,
                        enabled = preview.validRows.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("유효한 ${preview.validRows.size}건 가져오기") }
                    TextButton(onClick = viewModel::clearPreview, modifier = Modifier.fillMaxWidth()) {
                        Text("미리보기 취소")
                    }
                }
            }
        }
    }

