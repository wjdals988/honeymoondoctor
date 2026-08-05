package com.jeongmin.honeymoondoctor.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jeongmin.honeymoondoctor.feature.expense.ExpenseScreen
import com.jeongmin.honeymoondoctor.feature.home.HomeScreen
import com.jeongmin.honeymoondoctor.feature.itinerary.ItineraryEditScreen
import com.jeongmin.honeymoondoctor.feature.itinerary.ItineraryScreen
import com.jeongmin.honeymoondoctor.feature.more.MoreScreen
import com.jeongmin.honeymoondoctor.feature.more.MoreViewModel
import com.jeongmin.honeymoondoctor.feature.nearby.NearbyScreen
import com.jeongmin.honeymoondoctor.feature.tripinfo.TripInfoScreen

private const val ROUTE_TRIP_INFO = "trip_info"
private const val ROUTE_ITINERARY_EDIT = "itinerary_edit"

private fun itineraryEditRoute(itemId: String?): String =
    if (itemId == null) ROUTE_ITINERARY_EDIT else "$ROUTE_ITINERARY_EDIT?itemId=$itemId"

/** 데모 모드 배너는 AuthGate가 로그인/여행설정 화면을 포함해 항상 최상단에 그린다. */
@Composable
fun HoneymoonDoctorAppRoot(viewModel: AppRootViewModel = hiltViewModel()) {
    val isDemoMode = viewModel.isDemoMode
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { HoneymoonBottomBar(navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomTab.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(BottomTab.HOME.route) {
                HomeScreen(
                    onAddItinerary = { navController.navigate(itineraryEditRoute(null)) },
                    onOpenItineraryTab = { navController.navigateToTab(BottomTab.ITINERARY) },
                    onOpenNearbyTab = { navController.navigateToTab(BottomTab.NEARBY) },
                )
            }
            composable(BottomTab.ITINERARY.route) {
                ItineraryScreen(onOpenEditor = { itemId -> navController.navigate(itineraryEditRoute(itemId)) })
            }
            composable(
                route = "$ROUTE_ITINERARY_EDIT?itemId={itemId}",
                arguments = listOf(
                    navArgument("itemId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                ItineraryEditScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(BottomTab.NEARBY.route) { NearbyScreen() }
            composable(BottomTab.EXPENSE.route) { ExpenseScreen() }
            composable(BottomTab.MORE.route) {
                val moreViewModel: MoreViewModel = hiltViewModel()
                MoreScreen(
                    isDemoMode = isDemoMode,
                    onNavigateToTripInfo = { navController.navigate(ROUTE_TRIP_INFO) },
                    onResetDemoData = moreViewModel::resetDemoData,
                )
            }
            composable(ROUTE_TRIP_INFO) { TripInfoScreen() }
        }
    }
}

private fun androidx.navigation.NavHostController.navigateToTab(tab: BottomTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun HoneymoonBottomBar(navController: androidx.navigation.NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        BottomTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { navController.navigateToTab(tab) },
                icon = { Icon(imageVector = tab.icon, contentDescription = stringResource(id = tab.labelRes)) },
                label = { Text(text = stringResource(id = tab.labelRes)) },
            )
        }
    }
}
