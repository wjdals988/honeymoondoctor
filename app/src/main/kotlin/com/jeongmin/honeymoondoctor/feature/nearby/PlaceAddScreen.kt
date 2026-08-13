package com.jeongmin.honeymoondoctor.feature.nearby

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * 장소를 넣는 두 가지 방법(직접 입력 / TSV·JSON 저장목록 불러오기)을 탭 하나로 묶은 진입 화면.
 * 이전에는 "불러오기"가 전체 메뉴 깊숙이 있어 있는지도 모르기 쉬웠다 — 주변 탭 "+"를
 * 누르는 순간 두 방법을 나란히 보여준다. 기존 화면(수정, 전체 메뉴의 가져오기·내보내기)은
 * 그대로 두고 본문만 [PlaceEditFormContent]/[PlaceImportFormContent]로 재사용한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceAddScreen(
    onNavigateBack: () -> Unit,
    placeEditViewModel: PlaceEditViewModel = hiltViewModel(),
    placeImportViewModel: PlaceImportViewModel = hiltViewModel(),
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("장소 추가") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("직접 추가") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("저장목록 불러오기") },
                )
            }
            when (selectedTab) {
                0 -> PlaceEditFormContent(
                    viewModel = placeEditViewModel,
                    onSaved = onNavigateBack,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> PlaceImportFormContent(
                    viewModel = placeImportViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
