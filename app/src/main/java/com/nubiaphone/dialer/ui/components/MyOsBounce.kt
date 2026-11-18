package com.nubiaphone.dialer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.E
import kotlin.math.sign
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * MyOS list-end bounce, measured on NX741J (Ayarlar › Tüm uygulamalar, screenrecord @30fps):
 *   fling into the end → content overshoots ≈ 66dp, peaks after ≈ 130ms, settles in ≈ 600ms
 *     (critically damped: ω ≈ 7.7 rad/s → stiffness ≈ 60)
 *   dragging past the end → content follows the finger with growing resistance; a full-screen
 *     swipe moves it ≈ 70dp, like Ayarlar
 *   release → springs back
 * Android's stretch overscroll is turned off app-wide (Theme.kt), so this replaces it.
 */
private const val FLING_STIFFNESS = 60f
private const val RELEASE_STIFFNESS = 180f
private val MaxOvershoot = 66.dp
private val MaxDrag = 110.dp
private const val DRAG_RESISTANCE = 0.28f

/** Shared bounce for a scroll container; [offset] is >0 when pulled down at the top. */
class MyOsBounceState internal constructor(
    internal val connection: BounceConnection,
    val maxDragPx: Float,
) {
    /** Snapshot-backed; reading in composition recomposes while the spring runs. */
    val offset: Float
        get() = connection.offset.value
}

@Composable
fun rememberMyOsBounce(): MyOsBounceState {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    return remember(density) {
        with(density) {
            MyOsBounceState(
                BounceConnection(scope, MaxOvershoot.toPx(), MaxDrag.toPx()),
                maxDragPx = MaxDrag.toPx(),
            )
        }
    }
}

/** Put on a vertical scroll container (LazyColumn / verticalScroll) to get the MyOS bounce. */
fun Modifier.myOsBounce(): Modifier =
    composed {
        val state = rememberMyOsBounce()
        myOsBounce(state)
    }

/**
 * Same bounce, with a shared [state] so callers can react to overscroll (e.g. grow a header).
 * When [translateTop] is false, a pull-down at the top does not slide the list (the host can
 * grow a header instead); bottom overscroll still bounces.
 */
fun Modifier.myOsBounce(
    state: MyOsBounceState,
    translateTop: Boolean = true,
): Modifier =
    this
        .clipToBounds()
        .nestedScroll(state.connection)
        .graphicsLayer {
            val y = state.connection.offset.value
            translationY = if (translateTop) y else y.coerceAtMost(0f)
        }

internal class BounceConnection(
    private val scope: CoroutineScope,
    private val maxOvershootPx: Float,
    private val maxDragPx: Float,
) : NestedScrollConnection {
    /** >0: pulled down at the top; <0: pushed up at the bottom. */
    val offset = Animatable(0f)

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val current = offset.value
        if (current == 0f || source != NestedScrollSource.UserInput) return Offset.Zero
        // Finger moving back toward the content: undo the overscroll before the list scrolls.
        if (sign(available.y) == sign(current)) return Offset.Zero
        val next = if (current > 0) (current + available.y).coerceAtLeast(0f) else (current + available.y).coerceAtMost(0f)
        val used = next - current
        scope.launch { offset.snapTo(next) }
        return Offset(0f, used)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
        val current = offset.value
        // Rubber band: a quarter of the finger at first, stiffer the further it goes.
        val resistance = DRAG_RESISTANCE * (1f - (abs(current) / maxDragPx)).coerceIn(0.05f, 1f)
        val next = (current + available.y * resistance).coerceIn(-maxDragPx, maxDragPx)
        scope.launch { offset.snapTo(next) }
        return available
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (offset.value == 0f) return Velocity.Zero
        // Let go while stretched: spring home; the list does not fling.
        offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = RELEASE_STIFFNESS))
        return available
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (available.y == 0f) return Velocity.Zero
        // The list hit its end still moving: overshoot by that speed, then settle back.
        // Critically damped peak = v / (e·ω), so cap v to keep the peak at MaxOvershoot.
        val omega = sqrt(FLING_STIFFNESS)
        val cap = maxOvershootPx * E.toFloat() * omega
        val v = available.y.coerceIn(-cap, cap)
        offset.animateTo(
            0f,
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = FLING_STIFFNESS),
            initialVelocity = v,
        )
        return available
    }
}
