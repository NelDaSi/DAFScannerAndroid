package com.neldasi.jetpackcompose

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

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
    }
}