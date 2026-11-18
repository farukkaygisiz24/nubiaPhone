package com.nubiaphone.dialer.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nubiaphone.dialer.ui.theme.MyOsCardShape

/**
 * Shared durations (same in nubiaSMS): a bar with "Geri al" stays SnackbarDuration.Long (10s)
 * so there is time to undo; a plain notice stays SnackbarDuration.Short (4s).
 */
val UndoDuration = androidx.compose.material3.SnackbarDuration.Long

/** MyOS popup card: white by day, #2A2A2A at night (one step above the #1B1B1B cards). */
private val SnackLight = Color(0xFFFFFFFF)
private val SnackDark = Color(0xFF2A2A2A)

/**
 * Snackbar in the MyOS card style instead of Material's always-dark bar: follows the
 * light/dark theme, 16dp corners, flat (no shadow — Material's Snackbar has a fixed
 * elevation, so this draws its own Surface), accent-coloured action. Sits 16dp above whatever
 * is below it (the navigation bar, or the tab bar on the home screen), same as nubiaSMS.
 */
@Composable
fun MyOsSnackbarHost(
    state: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    SnackbarHost(state, modifier) { data ->
        Surface(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp).fillMaxWidth(),
            shape = MyOsCardShape,
            color = if (dark) SnackDark else SnackLight,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.heightIn(min = 52.dp).padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = data.visuals.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                data.visuals.actionLabel?.let { label ->
                    TextButton(onClick = { data.performAction() }) {
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
