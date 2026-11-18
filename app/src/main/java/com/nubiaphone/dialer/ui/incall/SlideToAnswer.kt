package com.nubiaphone.dialer.ui.incall

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubiaphone.dialer.ui.components.myOsAccentFill
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val TrackHeight = 76.dp
private val Knob = 64.dp
private val Inset = 6.dp

/** Fraction of the track the knob must pass to answer. */
private const val ANSWER_AT = 0.82f

/**
 * iOS-style "slide to answer": a pill track with the accent knob on the left and a shimmering
 * hint. Drag the knob to the right end to answer; let go earlier and it springs back.
 * A light tick when the knob passes the answer point (and when it drops back).
 */
@Composable
fun SlideToAnswer(
    onAnswer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val accent = myOsAccentFill()
    val offset = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    var answered by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(TrackHeight)
            .clip(RoundedCornerShape(TrackHeight / 2))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val maxPx = with(density) { (maxWidth - Knob - Inset * 2).toPx() }.coerceAtLeast(1f)
        val progress = (offset.value / maxPx).coerceIn(0f, 1f)

        // Accent wash that follows the knob.
        Box(
            Modifier
                .fillMaxWidth()
                .height(TrackHeight)
                .alpha(progress * 0.35f)
                .background(accent),
        )
        ShimmerHint(
            "Cevaplamak için kaydır",
            Modifier
                .align(Alignment.Center)
                .padding(start = Knob)
                .alpha((1f - progress * 1.6f).coerceIn(0f, 1f)),
        )
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .padding(Inset)
                .size(Knob)
                .shadow(10.dp, CircleShape, ambientColor = accent, spotColor = accent)
                .clip(CircleShape)
                .background(accent)
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = !answered,
                    state =
                        rememberDraggableState { delta ->
                            scope.launch {
                                offset.snapTo((offset.value + delta).coerceIn(0f, maxPx))
                                val nowArmed = offset.value >= maxPx * ANSWER_AT
                                if (nowArmed != armed) {
                                    armed = nowArmed
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                    onDragStopped = {
                        if (offset.value >= maxPx * ANSWER_AT) {
                            answered = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            offset.animateTo(maxPx, tween(120))
                            onAnswer()
                        } else {
                            armed = false
                            offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Call, contentDescription = "Cevapla", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

/** Hint text with a bright band sweeping left → right, like the iOS lock-screen slide hint. */
@Composable
private fun ShimmerHint(text: String, modifier: Modifier) {
    val base = MaterialTheme.colorScheme.onSurfaceVariant
    val sweep by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "shimmerX",
    )
    Text(
        text,
        modifier =
            modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val band = size.width * 0.42f
                    val x = -band + (size.width + band * 2f) * sweep
                    drawRect(
                        brush =
                            Brush.linearGradient(
                                colorStops =
                                    arrayOf(
                                        0f to Color.Transparent,
                                        0.35f to Color.White.copy(alpha = 0.15f),
                                        0.5f to Color.White.copy(alpha = 0.85f),
                                        0.65f to Color.White.copy(alpha = 0.15f),
                                        1f to Color.Transparent,
                                    ),
                                start = Offset(x, 0f),
                                end = Offset(x + band, 0f),
                            ),
                        blendMode = BlendMode.SrcAtop,
                    )
                },
        style =
            TextStyle(
                fontSize = 17.sp,
                color = base,
            ),
    )
}
