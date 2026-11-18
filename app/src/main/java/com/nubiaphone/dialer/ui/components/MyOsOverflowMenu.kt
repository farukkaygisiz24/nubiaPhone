package com.nubiaphone.dialer.ui.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/*
 * MyOS overflow menu. Measured on NX741J from Ses Kaydedici's ⋮ menu
 * (1216×2688 @ 520dpi → 3.25 px/dp), cross-checked with decoded tokens:
 *   panel        min 196dp wide, 16dp corners, no inner top/bottom padding
 *   row          52dp tall (mfvc_1line_list_height), text inset 16dp
 *   text         16sp regular, #1A1A1A by day (90 % foreground), 90 % white at night
 *   panel fill   #FFFFFF day / #232323 night (mfvc_popup_bg_color)
 *   placement    right edge 16dp from the screen edge, top ≈ 2dp above the ⋮ button's bottom
 *   shadow       soft, wide, low-contrast (app_bar_menu_bg nine-patch); none at night
 * Product choice (user request): a 20dp line icon before each label, and destructive rows in red.
 */
private val DangerLight = Color(0xFFE5393B)
private val DangerDark = Color(0xFFFF6B6B)
private val IconSize = 20.dp
private val IconGap = 14.dp
private val PopupBgDark = Color(0xFF232323)
private val PopupBgLight = Color(0xFFFFFFFF)
private val PressDark = Color(0x14FFFFFF)
private val PressLight = Color(0x0F000000)
private val PanelShape = RoundedCornerShape(16.dp)
private val RowHeight = 52.dp
private val PanelMinWidth = 196.dp
private val TextInset = 16.dp

/** Room around the panel inside the popup window so the shadow is not clipped. */
private val ShadowRoom = 16.dp

/** The top bar puts the ⋮ button 4dp from the edge; MyOS panels sit 16dp in. */
private val EdgeShift = 12.dp

data class MyOsMenuItem(
    val label: String,
    /** Line icon drawn before the label. */
    val icon: ImageVector? = null,
    /** Trash / block style rows: icon and label in red. */
    val destructive: Boolean = false,
    /** Kept for callers; MyOS menus have no group separators, so it is not drawn. */
    val startsGroup: Boolean = false,
    val onClick: () -> Unit,
)

/** Three-dot top bar button that opens a MyOS-styled popup menu. */
@Composable
fun MyOsOverflowMenu(
    items: List<MyOsMenuItem>,
    dark: Boolean,
    contentDescription: String = "Diğer seçenekler",
) {
    val visible = remember { MutableTransitionState(false) }
    val density = LocalDensity.current
    val textColor = if (dark) Color.White.copy(alpha = 0.9f) else Color(0xFF1A1A1A)

    Box {
        IconButton(onClick = { visible.targetState = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (visible.currentState || visible.targetState) {
            val offset =
                with(density) {
                    IntOffset(
                        x = (ShadowRoom - EdgeShift).roundToPx(),
                        y = (48.dp - 2.dp - ShadowRoom).roundToPx(),
                    )
                }
            Popup(
                alignment = Alignment.TopEnd,
                offset = offset,
                onDismissRequest = { visible.targetState = false },
                properties = PopupProperties(focusable = true),
            ) {
                AnimatedVisibility(
                    visibleState = visible,
                    enter =
                        fadeIn(tween(150)) +
                            scaleIn(tween(180), initialScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f)),
                    exit =
                        fadeOut(tween(120)) +
                            scaleOut(tween(120), targetScale = 0.95f, transformOrigin = TransformOrigin(1f, 0f)),
                ) {
                    Box(Modifier.padding(ShadowRoom)) {
                        Column(
                            modifier =
                                Modifier
                                    .then(
                                        if (dark) {
                                            Modifier
                                        } else {
                                            Modifier.shadow(
                                                elevation = 14.dp,
                                                shape = PanelShape,
                                                ambientColor = Color.Black.copy(alpha = 0.10f),
                                                spotColor = Color.Black.copy(alpha = 0.14f),
                                            )
                                        },
                                    )
                                    .clip(PanelShape)
                                    .background(if (dark) PopupBgDark else PopupBgLight)
                                    .width(IntrinsicSize.Max)
                                    .widthIn(min = PanelMinWidth)
                                    // Landscape / small windows: scroll instead of cutting rows off.
                                    .verticalScroll(rememberScrollState()),
                        ) {
                            items.forEach { item ->
                                MenuRow(
                                    label = item.label,
                                    icon = item.icon,
                                    color = if (item.destructive) (if (dark) DangerDark else DangerLight) else textColor,
                                    iconColor =
                                        if (item.destructive) {
                                            if (dark) DangerDark else DangerLight
                                        } else {
                                            textColor.copy(alpha = if (dark) 0.75f else 0.7f)
                                        },
                                    pressColor = if (dark) PressDark else PressLight,
                                    onClick = {
                                        visible.targetState = false
                                        item.onClick()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    icon: ImageVector?,
    color: Color,
    iconColor: Color,
    pressColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(RowHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = pressColor.copy(alpha = 1f)),
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = TextInset),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(IconSize),
                )
                Spacer(Modifier.width(IconGap))
            }
            Text(
            text = label,
            color = color,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        }
    }
}
