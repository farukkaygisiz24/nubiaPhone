package com.nubiaphone.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * MyOS round floating button, measured in Saat: a 48dp accent circle
 * (mfvc_floating_button_radius 24dp) with an accent-tinted glow instead of a grey shadow
 * (mfvc_fab_bg_shadow_color). [size] 64dp = mfvc_floating_button_size, used for the call key.
 */
@Composable
fun MyOsFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true,
) {
    val accent = myOsAccentFill()
    Box(
        modifier =
            modifier
                .size(size)
                .shadow(10.dp, CircleShape, ambientColor = accent, spotColor = accent)
                .clip(CircleShape)
                .background(accent)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

/**
 * Solid accent fill for MyOS buttons and the selected tab. Material's dark scheme uses a pale
 * primary (accent1_200) with dark content; MyOS keeps a mid tone with white content in both
 * themes (Saat at night: FAB = system_accent1_400, selected tab = system_accent1_500;
 * light theme values are derived: accent1_600, the documented MyOS primary).
 */
@Composable
fun myOsAccentFill(): Color =
    when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> MaterialTheme.colorScheme.primary
        isSystemInDarkTheme() -> colorResource(android.R.color.system_accent1_400)
        else -> colorResource(android.R.color.system_accent1_600)
    }

/** Accent for small glyphs/labels (selected tab): one palette step deeper than the fill at night. */
@Composable
fun myOsAccentInk(): Color =
    when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> MaterialTheme.colorScheme.primary
        isSystemInDarkTheme() -> colorResource(android.R.color.system_accent1_500)
        else -> colorResource(android.R.color.system_accent1_600)
    }
