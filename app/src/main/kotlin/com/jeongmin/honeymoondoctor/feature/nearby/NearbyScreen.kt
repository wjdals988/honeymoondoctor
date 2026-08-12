package com.jeongmin.honeymoondoctor.feature.nearby

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.ui.AppCard
import com.jeongmin.honeymoondoctor.core.ui.CardTone
import com.jeongmin.honeymoondoctor.core.ui.EmptyState
import com.jeongmin.honeymoondoctor.core.ui.FabSpacing
import com.jeongmin.honeymoondoctor.core.ui.LocalTripReadOnly
import com.jeongmin.honeymoondoctor.core.ui.SectionHeader
import com.jeongmin.honeymoondoctor.core.ui.openGoogleMapsDirections
import com.jeongmin.honeymoondoctor.core.ui.openUrl
import com.jeongmin.honeymoondoctor.core.ui.UndoDeleteSnackbarEffect
import com.jeongmin.honeymoondoctor.core.ui.rememberActionErrorSnackbar
import com.jeongmin.honeymoondoctor.domain.model.Place
import com.jeongmin.honeymoondoctor.domain.model.PlaceCategory
import com.jeongmin.honeymoondoctor.domain.usecase.PlaceScore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(
    onOpenEditor: (placeId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NearbyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<Place?>(null) }
    val snackbarHostState = rememberActionErrorSnackbar(uiState.actionError, viewModel::clearActionError)
    val pendingUndo by viewModel.undoDelete.pending.collectAsState()
    UndoDeleteSnackbarEffect(
        hostState = snackbarHostState,
        pending = pendingUndo,
        onUndo = viewModel::restoreDeleted,
        onDismissed = viewModel.undoDelete::dismiss,
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.onPermissionResult() }

    // 화면 진입 시 1회 위치 갱신(권한 있으면). 백그라운드 추적은 없다(스펙 2장).
    LaunchedEffect(Unit) { viewModel.refreshLocation() }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!LocalTripReadOnly.current) {
                FloatingActionButton(onClick = { onOpenEditor(null) }) {
                    Icon(Icons.Filled.Add, contentDescription = "장소 추가")
                }
            }
        },
    ) { innerPadding ->
        if (uiState.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = uiState.refreshing,
            onRefresh = viewModel::refreshLocation,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = FabSpacing.ContentBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    StatusHeader(
                        uiState = uiState,
                        onRequestPermission = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        },
                        onSelectCity = viewModel::selectCity,
                    )
                }
                item {
                    FilterRow(uiState = uiState, viewModel = viewModel)
                }

                if (uiState.totalPlaceCount == 0) {
                    item {
                        EmptyState(
                            title = "저장된 장소가 없습니다",
                            modifier = Modifier.padding(top = 48.dp),
                            description = "가고 싶은 곳을 담아 두면 현재 위치 기준으로 추천해 줍니다.",
                            action = if (LocalTripReadOnly.current) {
                                null
                            } else {
                                { FilledTonalButton(onClick = { onOpenEditor(null) }) { Text("첫 장소 추가") } }
                            },
                        )
                    }
                } else {
                    if (uiState.top3.isNotEmpty()) {
                        item {
                            SectionHeader(title = "지금 가기 좋은 처방")
                        }
                        items(uiState.top3, key = { "top-${it.place.id}" }) { scored ->
                            TopPlaceCard(
                                scored = scored,
                                onDirections = { openDirections(context, scored.place) },
                                onEdit = { onOpenEditor(scored.place.id) },
                                onToggleVisited = { viewModel.toggleVisited(scored.place) },
                                onDelete = { deleteTarget = scored.place },
                            )
                        }
                    }
                    if (uiState.others.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "전체 목록 (${uiState.sort.labelKo})",
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(uiState.others, key = { it.place.id }) { scored ->
                            PlaceRow(
                                scored = scored,
                                onDirections = { openDirections(context, scored.place) },
                                onEdit = { onOpenEditor(scored.place.id) },
                                onToggleVisited = { viewModel.toggleVisited(scored.place) },
                                onDelete = { deleteTarget = scored.place },
                            )
                        }
                    }
                    if (uiState.noCoordinates.isNotEmpty()) {
                        item {
                            Column {
                                SectionHeader(
                                    title = "위치 미확인",
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                Text(
                                    text = "좌표가 없어 거리·추천 계산에서 제외된 장소입니다. 수정에서 좌표를 입력해 주세요.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(uiState.noCoordinates, key = { "nocoord-${it.id}" }) { place ->
                            PlaceRow(
                                scored = ScoredPlace(place, null, emptyScore),
                                showScore = false,
                                onDirections = { openDirections(context, place) },
                                onEdit = { onOpenEditor(place.id) },
                                onToggleVisited = { viewModel.toggleVisited(place) },
                                onDelete = { deleteTarget = place },
                            )
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("장소 삭제") },
            text = { Text("\"${target.name}\" 장소를 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target)
                    deleteTarget = null
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("취소") } },
        )
    }
}

/** 좌표가 있으면 좌표로, 없으면 mapsUrl → 장소명 순으로 길찾기를 연다. */
private fun openDirections(context: android.content.Context, place: Place) {
    when {
        place.hasCoordinates -> openGoogleMapsDirections(context, "${place.latitude},${place.longitude}")
        !place.mapsUrl.isNullOrBlank() -> openUrl(context, place.mapsUrl)
        else -> openGoogleMapsDirections(context, place.name)
    }
}

private val emptyScore = PlaceScore(
    distancePoints = 0, cityPoints = 0, priorityPoints = 0, timePoints = 0, ratingPoints = 0, lowConfidence = true,
)

@Composable
private fun StatusHeader(
    uiState: NearbyUiState,
    onRequestPermission: () -> Unit,
    onSelectCity: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!uiState.hasLocationPermission) {
            AppCard(
                tone = CardTone.Warn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "위치 권한이 없어 선택 도시 기준으로 표시합니다.\n현재 위치와의 거리 계산은 불가능합니다.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onRequestPermission) { Text("위치 권한 허용") }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = uiState.referenceLabel ?: "거리 계산 불가 (위치·기준 좌표 없음)",
                style = MaterialTheme.typography.bodySmall,
                color = if (uiState.referenceLabel != null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.weight(1f),
            )
            CitySelector(uiState = uiState, onSelectCity = onSelectCity)
        }
    }
}

@Composable
private fun CitySelector(uiState: NearbyUiState, onSelectCity: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = uiState.cities.firstOrNull { it.id == uiState.selectedCityId }?.displayName
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("도시: ${selectedName ?: "자동"}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            uiState.cities.forEach { city ->
                DropdownMenuItem(
                    text = { Text(city.displayName) },
                    onClick = {
                        expanded = false
                        onSelectCity(city.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterRow(uiState: NearbyUiState, viewModel: NearbyViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            FilterChip(
                selected = uiState.categoryFilter == null,
                onClick = { viewModel.setCategoryFilter(null) },
                label = { Text("전체") },
            )
            PlaceCategory.entries.forEach { category ->
                FilterChip(
                    selected = uiState.categoryFilter == category,
                    onClick = { viewModel.setCategoryFilter(category) },
                    label = { Text(category.labelKo) },
                )
            }
            FilterChip(
                selected = uiState.unvisitedOnly,
                onClick = { viewModel.setUnvisitedOnly(!uiState.unvisitedOnly) },
                label = { Text("미방문만") },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NearbySort.entries.forEach { sortOption ->
                FilterChip(
                    selected = uiState.sort == sortOption,
                    onClick = { viewModel.setSort(sortOption) },
                    label = { Text(sortOption.labelKo) },
                )
            }
        }
    }
}

@Composable
private fun TopPlaceCard(
    scored: ScoredPlace,
    onDirections: () -> Unit,
    onEdit: () -> Unit,
    onToggleVisited: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard(
        onClick = onEdit,
        tone = CardTone.Highlight,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scored.place.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        scored.distanceMeters?.let { formatDistance(it) },
                        scored.place.category.labelKo,
                        scored.place.priority.labelKo,
                        if (scored.place.visited) "방문함" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            PlaceMenu(
                place = scored.place,
                onEdit = onEdit,
                onToggleVisited = onToggleVisited,
                onDelete = onDelete,
            )
        }
        Text(
            text = scoreBreakdownText(scored),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
            modifier = Modifier.padding(top = 4.dp),
        )
        scored.place.notes?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onDirections) { Text("Google Maps 길찾기") }
    }
}

@Composable
private fun PlaceRow(
    scored: ScoredPlace,
    onDirections: () -> Unit,
    onEdit: () -> Unit,
    onToggleVisited: () -> Unit,
    onDelete: () -> Unit,
    showScore: Boolean = true,
) {
    val place = scored.place
    AppCard(onClick = onEdit, tone = CardTone.Neutral, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        scored.distanceMeters?.let { formatDistance(it) },
                        place.category.labelKo,
                        place.priority.labelKo,
                        if (place.visited) "방문함" else "미방문",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (showScore) {
                    Text(
                        text = scoreBreakdownText(scored),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
                place.notes?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDirections) { Text("길찾기") }
                PlaceMenu(
                    place = place,
                    onEdit = onEdit,
                    onToggleVisited = onToggleVisited,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun PlaceMenu(
    place: Place,
    onEdit: () -> Unit,
    onToggleVisited: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // 완료된 여행에서는 쓰기 항목을 내린다. 메뉴가 비면 점 세 개 버튼 자체를 감춘다.
    val readOnly = LocalTripReadOnly.current
    if (readOnly) return
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "${place.name} 메뉴")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(if (place.visited) "미방문으로 되돌리기" else "방문함으로 표시") },
                onClick = {
                    expanded = false
                    onToggleVisited()
                },
            )
            DropdownMenuItem(text = { Text("수정") }, onClick = {
                expanded = false
                onEdit()
            })
            DropdownMenuItem(text = { Text("삭제") }, onClick = {
                expanded = false
                onDelete()
            })
        }
    }
}

private fun formatDistance(meters: Double): String =
    if (meters < 1_000) "${meters.toInt()}m" else "%.1fkm".format(meters / 1_000)

/** 점수 근거를 그대로 보여준다(스펙 7-7). */
private fun scoreBreakdownText(scored: ScoredPlace): String {
    val score = scored.score
    val base = "추천 ${score.total}점 = 거리 ${score.distancePoints} + 도시 ${score.cityPoints} + " +
        "우선순위 ${score.priorityPoints} + 시간대 ${score.timePoints} + 평점 ${score.ratingPoints}"
    return if (score.lowConfidence) "$base · 평점 정보 없음(신뢰도 낮음)" else base
}
