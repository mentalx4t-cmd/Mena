package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = BrandPink,
    secondary = BrandCyan,
    tertiary = BrandGlowGreen,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceContainer,
    onPrimary = BrandWhite,
    onSecondary = DarkBackground,
    onTertiary = DarkBackground,
    onBackground = BrandWhite,
    onSurface = BrandWhite
  )

private val LightColorScheme =
  lightColorScheme(
    primary = BrandPink,
    secondary = BrandCyan,
    tertiary = BrandGlowGreen,
    background = DarkBackground, // Dark background forced for high reliability and visual contrast
    surface = DarkSurface,
    onPrimary = BrandWhite,
    onSecondary = DarkBackground,
    onBackground = BrandWhite,
    onSurface = BrandWhite
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
