/** Navigation and bottom bar. Uses the Navigation Compose library. */
package com.zK.financialguide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("dashboard", "Home", Icons.Default.Home),
    Tab("expenses", "Expenses", Icons.AutoMirrored.Filled.List),
    Tab("goals", "Goals", Icons.Default.Star),
    Tab("afford", "Afford?", Icons.Default.ShoppingCart),
    Tab("learn", "Learn", Icons.Default.Info)
)

@Composable
fun AppRoot(vm: FinanceViewModel = viewModel()) {
    val state by vm.finances.collectAsStateWithLifecycle()

    if (!state.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    // First-time users go to Setup; returning users go straight to the Dashboard
    val start = remember { if (state.profile == null) "setup" else "dashboard" }

    Scaffold(
        bottomBar = {
            if (route in tabs.map { it.route }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo("dashboard")
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = {
                                Text(tab.label, maxLines = 1,
                                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 1.sp))
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("setup") {
                SetupScreen(initial = state.profile, onSave = { profile ->
                    vm.saveProfile(profile)
                    nav.navigate("dashboard") { popUpTo("setup") { inclusive = true } }
                })
            }
            composable("profile") {
                SetupScreen(initial = state.profile, onBack = { nav.popBackStack() }, onSave = { profile ->
                    vm.saveProfile(profile)
                    nav.popBackStack()
                })
            }
            composable("dashboard") {
                DashboardScreen(
                    vm,
                    onOpenPlan = { nav.navigate("plan") },
                    onOpenProfile = { nav.navigate("profile") }
                )
            }
            composable("plan") { PlanScreen(vm, onBack = { nav.popBackStack() }) }
            composable("expenses") { ExpensesScreen(vm) }
            composable("goals") { GoalsScreen(vm) }
            composable("afford") { AffordScreen(vm, onOpenPlan = { nav.navigate("plan") }) }
            composable("learn") { LearnScreen() }
        }
    }
}
