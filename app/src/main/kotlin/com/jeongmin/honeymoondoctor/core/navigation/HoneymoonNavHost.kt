package com.jeongmin.honeymoondoctor.core.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
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
import com.jeongmin.honeymoondoctor.core.ui.LocalTripReadOnly
import com.jeongmin.honeymoondoctor.core.ui.ReadOnlyBanner
import com.jeongmin.honeymoondoctor.core.ui.ReadOnlyEditorPanel
import com.jeongmin.honeymoondoctor.feature.about.AboutScreen
import com.jeongmin.honeymoondoctor.feature.checklist.ChecklistScreen
import com.jeongmin.honeymoondoctor.feature.decision.DecisionScreen
import com.jeongmin.honeymoondoctor.feature.expense.BudgetScreen
import com.jeongmin.honeymoondoctor.feature.expense.ExpenseEditScreen
import com.jeongmin.honeymoondoctor.feature.expense.ExpenseScreen
import com.jeongmin.honeymoondoctor.feature.home.HomeScreen
import com.jeongmin.honeymoondoctor.feature.itinerary.ItineraryEditScreen
import com.jeongmin.honeymoondoctor.feature.itinerary.ItineraryScreen
import com.jeongmin.honeymoondoctor.feature.more.MoreScreen
import com.jeongmin.honeymoondoctor.feature.more.MoreViewModel
import com.jeongmin.honeymoondoctor.feature.nearby.NearbyScreen
import com.jeongmin.honeymoondoctor.feature.nearby.PlaceEditScreen
import com.jeongmin.honeymoondoctor.feature.nearby.PlaceImportScreen
import com.jeongmin.honeymoondoctor.feature.publictrip.PublicTripDetailScreen
import com.jeongmin.honeymoondoctor.feature.publictrip.PublicTripListScreen
import com.jeongmin.honeymoondoctor.feature.reservation.ReservationDetailScreen
import com.jeongmin.honeymoondoctor.feature.reservation.ReservationEditScreen
import com.jeongmin.honeymoondoctor.feature.reservation.ReservationListScreen
import com.jeongmin.honeymoondoctor.feature.sync.SyncStatusScreen
import com.jeongmin.honeymoondoctor.feature.tripinfo.TripInfoScreen

private const val ROUTE_TRIP_INFO = "trip_info"
private const val ROUTE_ITINERARY_EDIT = "itinerary_edit"
private const val ROUTE_CHECKLIST = "checklist"
private const val ROUTE_DECISIONS = "decisions"
private const val ROUTE_RESERVATIONS = "reservations"
private const val ROUTE_RESERVATION_DETAIL = "reservation_detail"
private const val ROUTE_RESERVATION_EDIT = "reservation_edit"
private const val ROUTE_EXPENSE_EDIT = "expense_edit"
private const val ROUTE_BUDGETS = "budgets"
private const val ROUTE_PLACE_EDIT = "place_edit"
private const val ROUTE_PLACE_IMPORT = "place_import"
private const val ROUTE_SYNC_STATUS = "sync_status"
private const val ROUTE_PUBLIC_TRIPS = "public_trips"
private const val ROUTE_PUBLIC_TRIP_DETAIL = "public_trip_detail"
private const val ROUTE_ABOUT = "about"

private fun itineraryEditRoute(itemId: String?): String =
    if (itemId == null) ROUTE_ITINERARY_EDIT else "$ROUTE_ITINERARY_EDIT?itemId=$itemId"

private fun reservationEditRoute(reservationId: String?): String =
    if (reservationId == null) ROUTE_RESERVATION_EDIT else "$ROUTE_RESERVATION_EDIT?reservationId=$reservationId"

private fun expenseEditRoute(expenseId: String?): String =
    if (expenseId == null) ROUTE_EXPENSE_EDIT else "$ROUTE_EXPENSE_EDIT?expenseId=$expenseId"

private fun placeEditRoute(placeId: String?): String =
    if (placeId == null) ROUTE_PLACE_EDIT else "$ROUTE_PLACE_EDIT?placeId=$placeId"

/** 데모 모드 배너는 AuthGate가 로그인/여행설정 화면을 포함해 항상 최상단에 그린다. */
@Composable
fun HoneymoonDoctorAppRoot(viewModel: AppRootViewModel = hiltViewModel()) {
    val isDemoMode = viewModel.isDemoMode
    val navController = rememberNavController()
    val isReadOnly by viewModel.isTripReadOnly.collectAsState()

    CompositionLocalProvider(LocalTripReadOnly provides isReadOnly) {
    Scaffold(
        bottomBar = { HoneymoonBottomBar(navController) },
    ) { innerPadding ->
        // 읽기전용 띠는 NavHost 밖에 한 번만 두어 5개 탭과 모든 하위 화면에 함께 적용한다
        // (화면마다 넣으면 20곳을 손대야 하고 새 화면에서 빠뜨리기 쉽다).
        Column(modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)) {
        if (isReadOnly) {
            ReadOnlyBanner()
        }
        NavHost(
            navController = navController,
            startDestination = BottomTab.HOME.route,
            // 인셋은 위 Column이 처리한다. 여기서 다시 적용하면 하위 화면의 TopAppBar와
            // 이중으로 겹쳐 제목 위 여백이 두 배가 된다(v0.1.2에서 고친 문제).
        ) {
            composable(BottomTab.HOME.route) {
                HomeScreen(
                    onSwitchTrip = viewModel::backToTripList,
                    onAddItinerary = { navController.navigate(itineraryEditRoute(null)) },
                    onOpenItineraryTab = { navController.navigateToTab(BottomTab.ITINERARY) },
                    // 오버뷰의 날짜 줄 → 일정 탭의 그 날짜로. 탭 자체는 그대로 두고
                    // 인자만 실어 보낸다(하단 탭으로 들어올 때는 인자가 없다).
                    onOpenItineraryDate = { date ->
                        navController.navigate("${BottomTab.ITINERARY.route}?date=$date") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenNearbyTab = { navController.navigateToTab(BottomTab.NEARBY) },
                    onAddExpense = { navController.navigate(expenseEditRoute(null)) },
                    onOpenReservations = { navController.navigate(ROUTE_RESERVATIONS) },
                    onOpenChecklist = { navController.navigate(ROUTE_CHECKLIST) },
                    onOpenSyncStatus = { navController.navigate(ROUTE_SYNC_STATUS) },
                )
            }
            composable(
                route = "${BottomTab.ITINERARY.route}?date={date}",
                arguments = listOf(
                    navArgument("date") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                ItineraryScreen(
                    onOpenEditor = { itemId -> navController.navigate(itineraryEditRoute(itemId)) },
                    focusDate = entry.arguments?.getString("date"),
                )
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
                if (LocalTripReadOnly.current) {
                    ReadOnlyEditorPanel(onNavigateBack = { navController.popBackStack() })
                } else {
                    ItineraryEditScreen(onNavigateBack = { navController.popBackStack() })
                }
            }
            composable(BottomTab.NEARBY.route) {
                NearbyScreen(onOpenEditor = { placeId -> navController.navigate(placeEditRoute(placeId)) })
            }
            composable(
                route = "$ROUTE_PLACE_EDIT?placeId={placeId}",
                arguments = listOf(
                    navArgument("placeId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                if (LocalTripReadOnly.current) {
                    ReadOnlyEditorPanel(onNavigateBack = { navController.popBackStack() })
                } else {
                    PlaceEditScreen(onNavigateBack = { navController.popBackStack() })
                }
            }
            composable(ROUTE_PLACE_IMPORT) {
                PlaceImportScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(ROUTE_SYNC_STATUS) {
                SyncStatusScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(BottomTab.EXPENSE.route) {
                ExpenseScreen(
                    onAddExpense = { navController.navigate(expenseEditRoute(null)) },
                    onEditExpense = { expenseId -> navController.navigate(expenseEditRoute(expenseId)) },
                    onOpenBudgets = { navController.navigate(ROUTE_BUDGETS) },
                )
            }
            composable(
                route = "$ROUTE_EXPENSE_EDIT?expenseId={expenseId}",
                arguments = listOf(
                    navArgument("expenseId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                if (LocalTripReadOnly.current) {
                    ReadOnlyEditorPanel(onNavigateBack = { navController.popBackStack() })
                } else {
                    ExpenseEditScreen(onNavigateBack = { navController.popBackStack() })
                }
            }
            composable(ROUTE_BUDGETS) {
                BudgetScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(BottomTab.MORE.route) {
                val moreViewModel: MoreViewModel = hiltViewModel()
                val pendingJoinRequestCount by moreViewModel.pendingJoinRequestCount.collectAsState()
                val deleteAccountState by moreViewModel.deleteAccountState.collectAsState()
                MoreScreen(
                    isDemoMode = isDemoMode,
                    onNavigateToTripInfo = { navController.navigate(ROUTE_TRIP_INFO) },
                    onNavigateToReservations = { navController.navigate(ROUTE_RESERVATIONS) },
                    onNavigateToChecklist = { navController.navigate(ROUTE_CHECKLIST) },
                    onNavigateToDecisions = { navController.navigate(ROUTE_DECISIONS) },
                    onNavigateToPlaceImport = { navController.navigate(ROUTE_PLACE_IMPORT) },
                    onNavigateToSyncStatus = { navController.navigate(ROUTE_SYNC_STATUS) },
                    onNavigateToPublicTrips = { navController.navigate(ROUTE_PUBLIC_TRIPS) },
                    onNavigateToAbout = { navController.navigate(ROUTE_ABOUT) },
                    onSwitchTrip = viewModel::backToTripList,
                    onResetDemoData = moreViewModel::resetDemoData,
                    onLogout = moreViewModel::logout,
                    onDeleteAccount = moreViewModel::deleteAccount,
                    onRetryDeleteAfterReauth = moreViewModel::retryDeleteAfterReauth,
                    onDismissDeleteAccountError = moreViewModel::dismissDeleteAccountError,
                    deleteAccountState = deleteAccountState,
                    pendingJoinRequestCount = pendingJoinRequestCount,
                )
            }
            composable(ROUTE_TRIP_INFO) { TripInfoScreen() }
            composable(ROUTE_ABOUT) {
                AboutScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(ROUTE_PUBLIC_TRIPS) {
                PublicTripListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenDetail = { tripId -> navController.navigate("$ROUTE_PUBLIC_TRIP_DETAIL/$tripId") },
                )
            }
            composable(
                route = "$ROUTE_PUBLIC_TRIP_DETAIL/{tripId}",
                arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
            ) {
                PublicTripDetailScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(ROUTE_CHECKLIST) {
                ChecklistScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(ROUTE_DECISIONS) {
                DecisionScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(ROUTE_RESERVATIONS) {
                ReservationListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenDetail = { id -> navController.navigate("$ROUTE_RESERVATION_DETAIL/$id") },
                    onCreate = { navController.navigate(reservationEditRoute(null)) },
                )
            }
            composable(
                route = "$ROUTE_RESERVATION_DETAIL/{reservationId}",
                arguments = listOf(navArgument("reservationId") { type = NavType.StringType }),
            ) {
                ReservationDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(reservationEditRoute(id)) },
                )
            }
            composable(
                route = "$ROUTE_RESERVATION_EDIT?reservationId={reservationId}",
                arguments = listOf(
                    navArgument("reservationId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                if (LocalTripReadOnly.current) {
                    ReadOnlyEditorPanel(onNavigateBack = { navController.popBackStack() })
                } else {
                    ReservationEditScreen(onNavigateBack = { navController.popBackStack() })
                }
            }
        }
        }
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

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        BottomTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { navController.navigateToTab(tab) },
                icon = { Icon(imageVector = tab.icon, contentDescription = stringResource(id = tab.labelRes)) },
                label = { Text(text = stringResource(id = tab.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
