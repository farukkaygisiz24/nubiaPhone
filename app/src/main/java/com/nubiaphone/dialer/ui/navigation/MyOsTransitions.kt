package com.nubiaphone.dialer.ui.navigation

import android.graphics.Path
import android.view.animation.PathInterpolator
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * Page transitions matching the system activity animations on the NX741J
 * (framework `activity_open_*` / `activity_close_*`): a short 96dp horizontal slide over
 * 450ms with the "fast out extra slow in" curve. The outgoing page never fades; only the
 * incoming page (open) or the closing page (back) does a brief 83ms alpha step.
 * Navigation Compose's default full cross-fade does not exist anywhere in MyOS.
 */
private const val DURATION = 450
private const val ALPHA_DURATION = 83
private const val OPEN_ALPHA_DELAY = 50
private const val CLOSE_ALPHA_DELAY = 35

/** Material "emphasized" / fast_out_extra_slow_in: two cubic segments, so a Path is needed. */
private val FastOutExtraSlowIn: Easing =
    run {
        val path =
            Path().apply {
                moveTo(0f, 0f)
                cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
                cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
            }
        val interpolator = PathInterpolator(path)
        Easing { fraction -> interpolator.getInterpolation(fraction) }
    }

private fun <T> slideSpec() = tween<T>(durationMillis = DURATION, easing = FastOutExtraSlowIn)

typealias NavScope = AnimatedContentTransitionScope<NavBackStackEntry>

/** The four NavHost transitions, for a slide distance of [slidePx] (96dp). */
class MyOsTransitions(private val slidePx: Int) {
    /** New page coming in from the right. */
    val enter: NavScope.() -> EnterTransition = {
        slideInHorizontally(slideSpec()) { slidePx } +
            fadeIn(tween(ALPHA_DURATION, delayMillis = OPEN_ALPHA_DELAY, easing = LinearEasing))
    }

    /** Current page pushed slightly left, stays opaque underneath. */
    val exit: NavScope.() -> ExitTransition = {
        slideOutHorizontally(slideSpec()) { -slidePx }
    }

    /** Previous page returning from the left on back. */
    val popEnter: NavScope.() -> EnterTransition = {
        slideInHorizontally(slideSpec()) { -slidePx }
    }

    /** Closing page sliding right and dropping out quickly on back. */
    val popExit: NavScope.() -> ExitTransition = {
        slideOutHorizontally(slideSpec()) { slidePx } +
            fadeOut(tween(ALPHA_DURATION, delayMillis = CLOSE_ALPHA_DELAY, easing = LinearEasing))
    }
}
