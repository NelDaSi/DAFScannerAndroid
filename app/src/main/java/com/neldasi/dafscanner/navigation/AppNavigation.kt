package com.neldasi.dafscanner.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.neldasi.dafscanner.screens.CameraScanScreen
import com.neldasi.dafscanner.screens.DetailScreen
import com.neldasi.dafscanner.screens.MainScreen
import com.neldasi.dafscanner.screens.SearchListScreen
import com.neldasi.dafscanner.screens.SettingsScreen
import com.neldasi.dafscanner.viewmodels.SearchListViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = MainRoute) {
        composable<MainRoute> {
            MainScreen(navController)
        }
        composable<CameraRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<CameraRoute>()
            
            val searchBackStackEntry = remember(backStackEntry) {
                try {
                    navController.getBackStackEntry<SearchListRoute>()
                } catch (e: Exception) {
                    null
                }
            }
            
            val searchViewModel: SearchListViewModel? = searchBackStackEntry?.let { viewModel(it) }
            
            CameraScanScreen(navController, route.isVerifyMode, searchViewModel)
        }
        composable<SettingsRoute> {
            SettingsScreen(navController)
        }
        composable<SearchListRoute> { backStackEntry ->
            val searchViewModel: SearchListViewModel = viewModel(backStackEntry)
            SearchListScreen(navController, searchViewModel)
        }
        composable<DetailRoute> { backStackEntry ->
            val route: DetailRoute = backStackEntry.toRoute()
            DetailScreen(navController, route.fullCode, route.timestamp)
        }
    }
}
