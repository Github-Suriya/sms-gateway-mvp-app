package com.example.ui.theme

import android.os.Build
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
    primary = CyanPrimary,
    onPrimary = Slate950,
    primaryContainer = CyanPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = AmberWarning,
    onSecondary = Slate950,
    secondaryContainer = AmberWarningContainer,
    onSecondaryContainer = AmberWarning,
    tertiary = EmeraldSuccess,
    onTertiary = Slate950,
    background = Color(0xFF0B0F19), // Slate 950
    onBackground = Color(0xFFF3F4F6), // Slate 100
    surface = Color(0xFF111827), // Slate 900
    onSurface = Color(0xFFF9FAFB), // Slate 50
    surfaceVariant = Color(0xFF1F2937), // Slate 800
    onSurfaceVariant = Color(0xFF9CA3AF), // Slate 400
    outline = Color(0xFF374151), // Slate 700
    error = RoseError,
    onError = Color.White,
    errorContainer = RoseErrorContainer,
    onErrorContainer = RoseError
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0891B2), // Cyan 600
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFFAFE), // Cyan 100
    onPrimaryContainer = Color(0xFF164E63), // Cyan 900
    secondary = Color(0xFFD97706), // Amber 600
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF92400E),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC), // Slate 50
    onBackground = Color(0xFF0F172A), // Slate 900
    surface = Color(0xFFFFFFFF), // Pure White cards
    onSurface = Color(0xFF0F172A), // Slate 900 High Contrast Text
    surfaceVariant = Color(0xFFF1F5F9), // Slate 100
    onSurfaceVariant = Color(0xFF475569), // Slate 600 Secondary Text
    outline = Color(0xFFCBD5E1), // Slate 300 Card Borders
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF9F1239)
)

@Composable
fun RelayGatewayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
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
