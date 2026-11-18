package com.nubiaphone.dialer.ui.theme

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** MyOS Settings night card (`mfv_common_card_bg_color`). */
val MyOsCardSurface = Color(0xFF1B1B1B)
val MyOsCardSurfaceLight = Color(0xFFFFFFFF)
/**
 * Settings homepage search fill (screenshot / SurfaceContainerLowest ≈ #1E1E1E).
 * Note: `mfvc_common_search_bg` (#2D2D2D) is for older tinted search fields, not the homepage bar.
 */
val MyOsSearchSurface = Color(0xFF1E1E1E)
val MyOsSearchSurfaceLight = Color(0xFFEBEBEB)
val MyOsSecondaryText = Color(0xFFD0D0D0)
val MyOsSecondaryTextLight = Color(0xFF5C5C5C)

/** Light page canvas measured on Ayarlar (NX741J): #F5F5F5. */
val MyOsCanvasLight = Color(0xFFF5F5F5)

/**
 * Fallback accent for devices without dynamic color (< Android 12). On MyOS the
 * accent comes from the system palette via dynamic color instead.
 * Sampled from the China-ROM Messages thread screen (design-refs/myos-tokens/apps/messages).
 */
val MyOsBlue = Color(0xFF0F87F6)

/** Compose bar behind the input field: light `#F2F2F2` (sampled), dark = MyOS card. */
val MyOsInputBarLight = Color(0xFFF2F2F2)
val MyOsInputBarDark = MyOsCardSurface

/** Incoming bubble: a step above the card in dark, white on the #F5F5F5 light canvas. */
val MyOsIncomingBubbleDark = Color(0xFF262626)
val MyOsIncomingBubbleLight = Color(0xFFFFFFFF)

private val FallbackDark =
    darkColorScheme(
        background = Color.Black,
        surface = MyOsCardSurface,
        surfaceVariant = MyOsSearchSurface,
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = MyOsSecondaryText,
        primary = MyOsBlue,
        onPrimary = Color.White,
        primaryContainer = MyOsBlue,
        onPrimaryContainer = Color.White,
    )

private val FallbackLight =
    lightColorScheme(
        background = MyOsCanvasLight,
        surface = MyOsCardSurfaceLight,
        surfaceVariant = MyOsSearchSurfaceLight,
        onBackground = Color(0xFF111111),
        onSurface = Color(0xFF111111),
        onSurfaceVariant = MyOsSecondaryTextLight,
        primary = MyOsBlue,
        onPrimary = Color.White,
        primaryContainer = MyOsBlue,
        onPrimaryContainer = Color.White,
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NubiaPhoneTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> {
                val dynamic = dynamicDarkColorScheme(context)
                dynamic.copy(
                    background = Color.Black,
                    surface = MyOsCardSurface,
                    surfaceVariant = MyOsSearchSurface,
                    onBackground = Color.White,
                    onSurface = Color.White,
                    onSurfaceVariant = MyOsSecondaryText,
                    // Accent roles stay dynamic: they follow the MyOS system palette
                    // (wallpaper seed / theme style the user picked), not a fixed blue.
                    onSecondary = Color.Black,
                    onSecondaryContainer = Color.White,
                    onTertiary = Color.Black,
                    onTertiaryContainer = Color.White,
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !dark -> {
                val dynamic = dynamicLightColorScheme(context)
                dynamic.copy(
                    background = MyOsCanvasLight,
                    surface = MyOsCardSurfaceLight,
                    surfaceVariant = MyOsSearchSurfaceLight,
                    onBackground = Color(0xFF111111),
                    onSurface = Color(0xFF111111),
                    onSurfaceVariant = MyOsSecondaryTextLight,
                    // primary / primaryContainer left dynamic = system accent.
                )
            }
            dark -> FallbackDark
            else -> FallbackLight
        }

    MaterialTheme(
        colorScheme = scheme,
        typography = NubiaPhoneTypography,
        shapes = NubiaPhoneShapes,
    ) {
        // MyOS lists stop flat at the top/bottom; Android 12's stretch overscroll does not
        // exist there, so turn it off for every scrollable in the app.
        CompositionLocalProvider(LocalOverscrollConfiguration provides null, content = content)
    }
}
