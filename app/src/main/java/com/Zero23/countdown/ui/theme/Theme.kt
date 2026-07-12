package com.Zero23.countdown.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

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
            val onCustomColor = if (customColor.luminance() > 0.5f) Color.Black else Color.White
            if (darkTheme) {
                val containerColor = customColor.copy(alpha = 0.45f).compositeOver(Color(0xFF121212))
                val secondaryContainerColor = customColor.copy(alpha = 0.35f).compositeOver(Color(0xFF121212))
                darkColorScheme(
                    primary = customColor,
                    onPrimary = onCustomColor,
                    secondary = customColor,
                    onSecondary = onCustomColor,
                    tertiary = customColor,
                    onTertiary = onCustomColor,
                    primaryContainer = containerColor,
                    onPrimaryContainer = Color.White,
                    secondaryContainer = secondaryContainerColor,
                    onSecondaryContainer = Color.White,
                    surface = Color(0xFF1E1E1E),
                    onSurface = Color.White,
                    background = Color(0xFF121212),
                    onBackground = Color.White
                )
            } else {
                val containerColor = customColor.copy(alpha = 0.25f).compositeOver(Color.White)
                val secondaryContainerColor = customColor.copy(alpha = 0.2f).compositeOver(Color.White)
                lightColorScheme(
                    primary = customColor,
                    onPrimary = onCustomColor,
                    secondary = customColor,
                    onSecondary = onCustomColor,
                    tertiary = customColor,
                    onTertiary = onCustomColor,
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
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkColorScheme(
            primary = Purple80,
            secondary = PurpleGrey80,
            tertiary = Pink80,
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E)
        )
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
