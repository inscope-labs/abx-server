package com.inscopelabs.abx.server.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

// Calm, Heroku-console inspired scheme: white/gray surfaces, blue reserved
// for active state and primary actions. No dynamic color — brand identity
// should not be overridden by the user's wallpaper.

private val LightColorScheme = lightColorScheme(
    primary = Blue50,
    onPrimary = Color_White,
    primaryContainer = Blue10,
    onPrimaryContainer = Blue50,
    secondary = Gray70,
    onSecondary = Color_White,
    secondaryContainer = Gray10,
    onSecondaryContainer = Gray85,
    background = Color_White,
    onBackground = Gray85,
    surface = Color_White,
    onSurface = Gray85,
    surfaceVariant = Gray05,
    onSurfaceVariant = Gray70,
    outline = Gray30,
    outlineVariant = Gray20,
    error = ErrorRed40,
    onError = Color_White,
    errorContainer = ErrorRed90,
    onErrorContainer = ErrorRed40,
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Gray95,
    primaryContainer = Blue50,
    onPrimaryContainer = Blue90,
    secondary = Gray30,
    onSecondary = Gray95,
    secondaryContainer = Gray85,
    onSecondaryContainer = Gray10,
    background = Gray95,
    onBackground = Gray10,
    surface = Gray95,
    onSurface = Gray10,
    surfaceVariant = Gray85,
    onSurfaceVariant = Gray30,
    outline = Gray50,
    outlineVariant = Gray70,
    error = ErrorRed40,
    onError = Gray95,
    errorContainer = ErrorRed40,
    onErrorContainer = ErrorRed90,
)

// Extra semantic roles Material3's base ColorScheme doesn't cover
// (success / warning) so components never need to hardcode hex again.
@Immutable
data class AbxStatusColors(
    val success: androidx.compose.ui.graphics.Color,
    val onSuccess: androidx.compose.ui.graphics.Color,
    val successContainer: androidx.compose.ui.graphics.Color,
    val warning: androidx.compose.ui.graphics.Color,
    val onWarning: androidx.compose.ui.graphics.Color,
    val warningContainer: androidx.compose.ui.graphics.Color,
)

private val LightStatusColors = AbxStatusColors(
    success = SuccessGreen40,
    onSuccess = Color_White,
    successContainer = SuccessGreen90,
    warning = WarningAmber40,
    onWarning = Color_White,
    warningContainer = WarningAmber90,
)

private val DarkStatusColors = AbxStatusColors(
    success = SuccessGreen90,
    onSuccess = SuccessGreen40,
    successContainer = SuccessGreen40,
    warning = WarningAmber90,
    onWarning = WarningAmber40,
    warningContainer = WarningAmber40,
)

val LocalAbxStatusColors = staticCompositionLocalOf { LightStatusColors }

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    androidx.compose.runtime.CompositionLocalProvider(
        LocalAbxStatusColors provides statusColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Small helper accessor so callers can write MaterialTheme.abxStatusColors.success
val MaterialTheme.abxStatusColors: AbxStatusColors
    @Composable
    get() = LocalAbxStatusColors.current
