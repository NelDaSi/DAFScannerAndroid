package com.neldasi.jetpackcompose

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neldasi.jetpackcompose.Screens.CameraScanScreen
import com.neldasi.jetpackcompose.Screens.DetailScreen
import com.neldasi.jetpackcompose.Screens.MainScreen
import com.neldasi.jetpackcompose.Screens.SettingsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = AppDestinations.MAIN_SCREEN) {
        composable(AppDestinations.MAIN_SCREEN) {
            MainScreen(navController)
        }
        composable(AppDestinations.CAMERA_SCREEN) {
            CameraScanScreen(navController)
        }
        composable(AppDestinations.SETTINGS_SCREEN) {
            SettingsScreen(navController)
        }
        composable(
            route = "${AppDestinations.DETAIL_SCREEN}/{fullCode}/{timestamp}",
            arguments = listOf(
                navArgument("fullCode") { type = NavType.StringType },
                navArgument("timestamp") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val fullCode = backStackEntry.arguments?.getString("fullCode") ?: return@composable
            val timestamp = backStackEntry.arguments?.getLong("timestamp") ?: return@composable
            DetailScreen(navController, fullCode, timestamp)
        }
    }
}