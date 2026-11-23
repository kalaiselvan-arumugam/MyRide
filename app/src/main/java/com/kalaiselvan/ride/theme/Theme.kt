package com.kalaiselvan.ride.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kalaiselvan.ride.preferences.AppPreferences
import com.kalaiselvan.ride.preferences.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = RpmGreen,
    secondary = BluetoothBlue,
    tertiary = RpmOrange,
    background = DarkBgDark,
    surface = DarkBgCard,
    onPrimary = DarkBgDark,
    onSecondary = DarkTextMain,
    onBackground = DarkTextMain,
    onSurface = DarkTextMain
)

private val LightColorScheme = lightColorScheme(
    primary = RpmGreen,
    secondary = BluetoothBlue,
    tertiary = RpmOrange,
    background = LightBgDark,
    surface = LightBgCard,
    onPrimary = LightBgDark,
    onSecondary = LightTextMain,
    onBackground = LightTextMain,
    onSurface = LightTextMain
)

@Composable
fun MyRideTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val appPrefs = remember { AppPreferences.getInstance(context) }
    val userPrefs by appPrefs.userPreferences.collectAsState()

    val isLight = userPrefs.themeMode == ThemeMode.LIGHT
    val themeColors = if (isLight) LightThemeColors else DarkThemeColors
    val colorScheme = if (isLight) LightColorScheme else DarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = themeColors.bgDark.toArgb()
                window.navigationBarColor = themeColors.bgDark.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLight
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = isLight
            }
        }
    }

    CompositionLocalProvider(LocalThemeColors provides themeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
