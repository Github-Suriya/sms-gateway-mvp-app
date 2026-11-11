package com.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.screens.home.GatewayHomeScreen
import com.example.ui.screens.jobs.RecentJobsScreen
import com.example.ui.screens.pair.PairDeviceScreen
import com.example.ui.screens.settings.SettingsTroubleshootingScreen
import com.example.ui.theme.CyanPrimary

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Gateway", Icons.Default.Home)
    object Jobs : Screen("jobs", "Jobs", Icons.Default.History)
    object Pair : Screen("pair", "Pairing", Icons.Default.QrCode)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun RelayNavHost(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val uiState by viewModel.uiState.collectAsState()

    val startDestination = if (uiState.isPaired) Screen.Home.route else Screen.Pair.route

    val screens = listOf(
        Screen.Home,
        Screen.Jobs,
        Screen.Pair,
        Screen.Settings
    )

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.testTag("main_navigation_bar")
            ) {
                screens.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("nav_item_${screen.route}")
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                GatewayHomeScreen(
                    viewModel = viewModel,
                    onNavigateToPair = {
                        navController.navigate(Screen.Pair.route)
                    }
                )
            }

            composable(Screen.Jobs.route) {
                RecentJobsScreen(
                    viewModel = viewModel
                )
            }

            composable(Screen.Pair.route) {
                PairDeviceScreen(
                    viewModel = viewModel,
                    onPairSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Pair.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsTroubleshootingScreen(
                    viewModel = viewModel,
                    onUnpaired = {
                        navController.navigate(Screen.Pair.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
