package com.jeongmin.honeymoondoctor.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.ui.graphics.vector.ImageVector
import com.jeongmin.honeymoondoctor.R

/** 하단 탭은 정확히 5개다: 홈, 일정, 주변, 경비, 전체 */
enum class BottomTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(route = "home", labelRes = R.string.tab_home, icon = Icons.Filled.Home),
    ITINERARY(route = "itinerary", labelRes = R.string.tab_itinerary, icon = Icons.AutoMirrored.Filled.List),
    NEARBY(route = "nearby", labelRes = R.string.tab_nearby, icon = Icons.Filled.Place),
    EXPENSE(route = "expense", labelRes = R.string.tab_expense, icon = Icons.Filled.AttachMoney),
    MORE(route = "more", labelRes = R.string.tab_more, icon = Icons.Filled.Menu),
}
