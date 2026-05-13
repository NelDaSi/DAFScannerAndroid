package com.neldasi.dafscanner.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.neldasi.dafscanner.screens.CameraScanScreen
import com.neldasi.dafscanner.screens.DetailScreen
import com.neldasi.dafscanner.screens.MainScreen
import com.neldasi.dafscanner.screens.SearchListScreen
import com.neldasi.dafscanner.screens.SettingsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = MainRoute) {
        composable<MainRoute> {
            MainScreen(navController)
        }
        composable<CameraRoute> {
            CameraScanScreen(navController)
        }
        composable<SettingsRoute> {
            SettingsScreen(navController)
        }
        composable<SearchListRoute> {
            SearchListScreen(navController)
        }
        composable<DetailRoute> { backStackEntry ->
            val route: DetailRoute = backStackEntry.toRoute()
            DetailScreen(navController, route.fullCode, route.timestamp)
        }
    }
}
