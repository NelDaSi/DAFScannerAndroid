package com.neldasi.dafscanner.ui.theme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)

private val DafColorScheme = lightColorScheme(
    primary = DafBlue,
    onPrimary = Color.White,
    primaryContainer = DafBlue.copy(alpha = 0.12f),
    onPrimaryContainer = DafBlue,
    secondary = DafRed,
    onSecondary = Color.White,
    secondaryContainer = DafRed.copy(alpha = 0.12f),
    onSecondaryContainer = DafRed,
    tertiary = DafBlue,
    onTertiary = Color.White,
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1B1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFF1F0F7),
    onSurfaceVariant = Color(0xFF44474E),
    outline = DafBlue.copy(alpha = 0.3f),
    error = DafRed,
    onError = Color.White,
)

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun JetpackComposeTheme(
    theme: String = "DAF",
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (theme) {
        "LIGHT" -> false
        "DARK" -> true
        "DAF" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        theme == "DAF" -> DafColorScheme
        (dynamicColor) -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}