package com.neldasi.jetpackcompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.neldasi.jetpackcompose.ui.theme.JetpackComposeTheme

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = AppDestinations.MAIN_SCREEN) {
        composable(AppDestinations.MAIN_SCREEN) {
            MainScreen(navController) // MainScreen will get the result from SavedStateHandle
        }
        composable(AppDestinations.CAMERA_SCREEN) {
            CameraScanScreen(
                // navController is passed for the back button in CameraScanScreen
                navController = navController
            )
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge display for a more immersive UI
        enableEdgeToEdge()
        setContent {
            JetpackComposeTheme { // Apply your theme
                AppNavigation()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppNavigatorPreview() {
    JetpackComposeTheme {
        AppNavigation()
    }
}