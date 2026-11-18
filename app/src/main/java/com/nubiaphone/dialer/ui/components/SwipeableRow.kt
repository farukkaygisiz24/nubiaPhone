package com.nubiaphone.dialer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/*
 * Copied from nubiaSMS (ui/conversations/SwipeableRow.kt) with one addition: [SwipeAction.collapse].
 */

/** Panel fills the row, a progress sweep runs, then the row folds away. ~0.8s in total. */
private const val FILL_MS = 160
private const val HOLD_MS = 420
private const val COLLAPSE_MS = 220

/** Shorter sweep for actions that keep the row (call, WhatsApp). */
private const val KEEP_HOLD_MS = 260

/** If the list has not dropped the row this long after the action, bring it back. */
private const val STUCK_MS = 1500L

/** What a swipe in one direction does. */
data class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    /** nubiaPhone: a picture drawn instead of [icon] (e.g. the installed WhatsApp app's logo). */
    val image: ImageBitmap? = null,
    /**
     * true: the row leaves the list (archive, trash). It fills, holds briefly, folds away and
     * only then [onTrigger] runs, so the rows below slide up into the gap.
     * false: [onTrigger] runs at once and the row snaps back (e.g. a confirm dialog decides).
     */
    val leaves: Boolean = true,
    /**
     * nubiaPhone: with [leaves] true but [collapse] false the row keeps the fill + progress
     * sweep, then runs [onTrigger] and slides back into place instead of folding away
     * (call / WhatsApp: the row stays in the list).
     */
    val collapse: Boolean = true,
    /** Return value is ignored for [leaves] actions; kept for the snap-back case. */
    val onTrigger: () -> Boolean,
)

/**
 * Wraps a list row so it can be swiped right ([start]) or left ([end]), revealing a
 * coloured panel with the action's icon and label. The panel is clipped to [shape] so the
 * first/last rows keep the MyOS card corners while swiping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableRow(
    shape: Shape,
    enabled: Boolean,
    start: SwipeAction?,
    end: SwipeAction?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // Set only by a real swipe in this composition. The swipe state is saveable, so a row
    // that comes back through "Geri al" could otherwise restore as dismissed and re-run.
    var swipedNow by remember { mutableStateOf(false) }
    val currentStart by rememberUpdatedState(start)
    val currentEnd by rememberUpdatedState(end)
    val state =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                val action =
                    when (value) {
                        SwipeToDismissBoxValue.StartToEnd -> currentStart
                        SwipeToDismissBoxValue.EndToStart -> currentEnd
                        SwipeToDismissBoxValue.Settled -> return@rememberSwipeToDismissBoxState true
                    } ?: return@rememberSwipeToDismissBoxState false
                if (action.leaves) {
                    swipedNow = true
                    true // stay swiped out; the effect below finishes the job
                } else {
                    action.onTrigger()
                    false
                }
            },
            // A deliberate swipe: a bit over a third of the row.
            positionalThreshold = { width -> width * 0.35f },
        )

    val progress = remember { Animatable(0f) }
    val heightFraction = remember { Animatable(1f) }
    var committed by remember { mutableStateOf(false) }

    // Light tick when the swipe crosses the threshold (and when it drops back).
    LaunchedEffect(state.targetValue) {
        if (state.dismissDirection != SwipeToDismissBoxValue.Settled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Dismissed: fill → hold with a progress sweep → fold the row → run the action.
    LaunchedEffect(state.currentValue) {
        val action =
            when (state.currentValue) {
                SwipeToDismissBoxValue.StartToEnd -> currentStart
                SwipeToDismissBoxValue.EndToStart -> currentEnd
                SwipeToDismissBoxValue.Settled -> null
            }
        if (action == null || !action.leaves || committed) return@LaunchedEffect
        if (!swipedNow) {
            state.snapTo(SwipeToDismissBoxValue.Settled)
            return@LaunchedEffect
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(FILL_MS.toLong())
        if (!action.collapse) {
            // Stay in the list: short sweep, run, slide back.
            progress.animateTo(1f, tween(KEEP_HOLD_MS, easing = LinearEasing))
            action.onTrigger()
            swipedNow = false
            state.reset()
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
        heightFraction.animateTo(0f, tween(COLLAPSE_MS, easing = FastOutSlowInEasing))
        committed = true
        action.onTrigger()
        // Normally the list removes this row now. If it doesn't (move failed), restore it.
        delay(STUCK_MS)
        committed = false
        swipedNow = false
        progress.snapTo(0f)
        heightFraction.snapTo(1f)
        state.reset()
    }

    Box(
        modifier =
            modifier
                .clipToBounds()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val h = (placeable.height * heightFraction.value).roundToInt()
                    layout(placeable.width, h) { placeable.place(0, 0) }
                },
    ) {
        SwipeToDismissBox(
            state = state,
            modifier = Modifier.clip(shape),
            enableDismissFromStartToEnd = enabled && start != null && !committed,
            enableDismissFromEndToStart = enabled && end != null && !committed,
            backgroundContent = {
                val direction = state.dismissDirection
                val action =
                    when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> start
                        SwipeToDismissBoxValue.EndToStart -> end
                        SwipeToDismissBoxValue.Settled -> null
                    }
                val armed = state.targetValue != SwipeToDismissBoxValue.Settled
                val gone = state.currentValue != SwipeToDismissBoxValue.Settled
                val bg by animateColorAsState(
                    targetValue =
                        when {
                            action == null -> Color.Transparent
                            armed || gone -> action.color
                            else -> action.color.copy(alpha = 0.55f)
                        },
                    animationSpec = tween(120),
                    label = "swipeBg",
                )
                val iconScale by animateFloatAsState(
                    if (armed || gone) 1.1f else 0.9f,
                    tween(120),
                    label = "swipeIcon",
                )
                Box(Modifier.fillMaxSize().background(bg)) {
                    // Fill sweep while the row waits to leave.
                    if (gone && action != null) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.value)
                                .align(
                                    if (direction == SwipeToDismissBoxValue.EndToStart) {
                                        Alignment.CenterEnd
                                    } else {
                                        Alignment.CenterStart
                                    },
                                ).background(Color.White.copy(alpha = 0.18f)),
                        )
                    }
                    if (action != null) {
                        // The label rides with the swipe: it stays centred in the revealed
                        // part of the panel (never closer than 24dp to the edge), so a full
                        // swipe carries it smoothly to the middle instead of jumping there.
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier =
                                Modifier.layout { measurable, constraints ->
                                    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                                    val w = constraints.maxWidth
                                    val h = constraints.maxHeight
                                    val offset = runCatching { state.requireOffset() }.getOrDefault(0f)
                                    val revealed = abs(offset).coerceIn(0f, w.toFloat())
                                    val minCenter = 24.dp.toPx() + p.width / 2f
                                    val center = max(revealed / 2f, minCenter).coerceAtMost(w / 2f)
                                    val x =
                                        if (direction == SwipeToDismissBoxValue.EndToStart) {
                                            w - center - p.width / 2f
                                        } else {
                                            center - p.width / 2f
                                        }
                                    layout(w, h) { p.place(x.roundToInt(), (h - p.height) / 2) }
                                },
                        ) {
                            if (action.image != null) {
                                Image(
                                    bitmap = action.image,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp).scale(iconScale),
                                )
                            } else {
                                Icon(
                                    action.icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp).scale(iconScale),
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                action.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                            )
                        }
                    }
                }
            },
        ) {
            content()
        }
    }
}
