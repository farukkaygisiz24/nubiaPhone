package com.nubiaphone.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * Opens under the anchor (a list row) when it fits, otherwise above it, so it never covers the
 * row it belongs to. The panel's own 12dp shadow margin keeps it off the row's edge.
 */
private class BelowOrAbove(private val alignEnd: Boolean) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val fitsBelow = anchorBounds.bottom + popupContentSize.height <= windowSize.height
        val y = if (fitsBelow) anchorBounds.bottom else anchorBounds.top - popupContentSize.height
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceAtLeast(0),
        )
    }
}

/**
 * A MyOS popup list anchored to whatever it is placed next to (e.g. a message bubble).
 * Same look as [MyOsOverflowMenu]: 16dp panel, 52dp rows, 20dp line icons, white by day /
 * #232323 at night, destructive rows red.
 */
@Composable
fun MyOsPopupMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<MyOsMenuItem>,
    dark: Boolean,
    alignEnd: Boolean,
) {
    if (!expanded) return
    val bg = if (dark) Color(0xFF232323) else Color.White
    val text = if (dark) Color.White.copy(alpha = 0.9f) else Color(0xFF1A1A1A)
    val danger = if (dark) Color(0xFFFF6B6B) else Color(0xFFE5393B)
    val shape = RoundedCornerShape(16.dp)
    Popup(
        popupPositionProvider = remember(alignEnd) { BelowOrAbove(alignEnd) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .padding(12.dp)
                .widthIn(min = 196.dp)
                .then(if (dark) Modifier else Modifier.shadow(12.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000)))
                .clip(shape)
                .background(bg)
                .padding(vertical = 4.dp),
        ) {
            items.forEach { item ->
                val tint = if (item.destructive) danger else text
                Row(
                    modifier =
                        Modifier
                            .widthIn(min = 196.dp)
                            .clickable {
                                onDismiss()
                                item.onClick()
                            }
                            .height(52.dp)
                            .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.icon != null) {
                        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                    }
                    Text(item.label, style = MaterialTheme.typography.bodyLarge, color = tint)
                }
            }
        }
    }
}
