package com.example.youtubedownloader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.youtubedownloader.MainViewModel

enum class Screen(val route: String, val label: String) {
    HOME("home", "Home"),
    QUEUE("queue", "Queue"),
    SEARCH("search", "Search"),
    HISTORY("history", "History"),
    SETTINGS("settings", "Settings")
}

@Composable
fun MainApp(vm: MainViewModel) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: Screen.HOME.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.HOME.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                when (screen) {
                                    Screen.HOME -> if (currentRoute == screen.route) Icons.Filled.Home else Icons.Outlined.Home
                                    Screen.QUEUE -> if (currentRoute == screen.route) Icons.Filled.Queue else Icons.Outlined.Queue
                                    Screen.SEARCH -> if (currentRoute == screen.route) Icons.Filled.Search else Icons.Outlined.Search
                                    Screen.HISTORY -> if (currentRoute == screen.route) Icons.Filled.History else Icons.Outlined.History
                                    Screen.SETTINGS -> if (currentRoute == screen.route) Icons.Filled.Settings else Icons.Outlined.Settings
                                },
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.HOME.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.HOME.route) { DownloadScreen(vm) }
            composable(Screen.QUEUE.route) { QueueScreen(vm) }
            composable(Screen.SEARCH.route) { SearchScreen(vm) }
            composable(Screen.HISTORY.route) { HistoryScreen(vm) }
            composable(Screen.SETTINGS.route) { SettingsScreen(vm) }
        }
    }
}