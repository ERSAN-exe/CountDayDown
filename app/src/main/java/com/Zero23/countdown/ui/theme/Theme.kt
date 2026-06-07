package com.Zero23.countdown.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun CountDownTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    customColor: Color? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        customColor != null -> {
            if (darkTheme) {
                val containerColor = customColor.copy(alpha = 0.3f).compositeOver(Color(0xFF1C1B1F))
                val secondaryContainerColor = customColor.copy(alpha = 0.2f).compositeOver(Color(0xFF1C1B1F))
                darkColorScheme(
                    primary = customColor,
                    secondary = customColor,
                    tertiary = customColor,
                    primaryContainer = containerColor,
                    onPrimaryContainer = Color.White,
                    secondaryContainer = secondaryContainerColor,
                    onSecondaryContainer = Color.White,
                    surface = Color(0xFF1C1B1F),
                    onSurface = Color.White,
                    background = Color(0xFF1C1B1F),
                    onBackground = Color.White
                )
            } else {
                val containerColor = customColor.copy(alpha = 0.15f).compositeOver(Color.White)
                val secondaryContainerColor = customColor.copy(alpha = 0.1f).compositeOver(Color.White)
                lightColorScheme(
                    primary = customColor,
                    secondary = customColor,
                    tertiary = customColor,
                    primaryContainer = containerColor,
                    onPrimaryContainer = Color.Black,
                    secondaryContainer = secondaryContainerColor,
                    onSecondaryContainer = Color.Black,
                    surface = Color(0xFFFDFDFD),
                    onSurface = Color.Black,
                    background = Color(0xFFFDFDFD),
                    onBackground = Color.Black
                )
            }
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
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
