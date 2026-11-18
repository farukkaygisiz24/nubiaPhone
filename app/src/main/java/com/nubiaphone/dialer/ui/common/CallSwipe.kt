package com.nubiaphone.dialer.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.nubiaphone.dialer.ui.components.SwipeAction
import com.nubiaphone.dialer.ui.components.myOsAccentFill

/** WhatsApp's own green, so the action is recognisable at a glance. */
private val WhatsAppGreen = Color(0xFF1DA851)

/**
 * Row swipes in Son aramalar / Kişiler / Favoriler: right (start → end) opens the WhatsApp chat,
 * left (end → start) calls. Same animation as nubiaSMS, but the row stays in the list.
 */
@Composable
fun callSwipeActions(
    onWhatsApp: () -> Unit,
    onCall: () -> Unit,
): Pair<SwipeAction, SwipeAction> {
    val accent = myOsAccentFill()
    val logo = rememberWhatsAppLogo()
    return SwipeAction("WhatsApp", Icons.AutoMirrored.Filled.Chat, WhatsAppGreen, image = logo, leaves = true, collapse = false) {
        onWhatsApp()
        true
    } to
        SwipeAction("Ara", Icons.Filled.Call, accent, leaves = true, collapse = false) {
            onCall()
            true
        }
}

/**
 * The installed WhatsApp's own logo, read from the phone at run time (nothing is copied into
 * this app). Android 13+: the app's monochrome "themed icon" layer, cropped to its safe zone and
 * painted white like the other swipe icons. Otherwise the regular app icon. null when WhatsApp
 * is not installed (the chat bubble is drawn instead).
 */
@Composable
private fun rememberWhatsAppLogo(): ImageBitmap? {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { 24.dp.roundToPx() }
    return remember(px) { WhatsAppLogo.get(context, px) }
}

private object WhatsAppLogo {
    private var cached: ImageBitmap? = null
    private var cachedPx = 0
    private var looked = false

    fun get(context: Context, px: Int): ImageBitmap? {
        if (looked && cachedPx == px) return cached
        looked = true
        cachedPx = px
        cached = load(context, px)
        return cached
    }

    private fun load(context: Context, px: Int): ImageBitmap? {
        val pm = context.packageManager
        val icon =
            listOf("com.whatsapp", "com.whatsapp.w4b").firstNotNullOfOrNull { pkg ->
                runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
            } ?: return null
        val mono =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) (icon as? AdaptiveIconDrawable)?.monochrome else null
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (mono != null) {
            // Adaptive layers are 108dp with the logo in the middle 66dp: draw the layer larger
            // so that safe zone fills the bitmap, then tint it white.
            val extra = (px * (108f / 66f - 1f) / 2f).toInt()
            mono.mutate().apply {
                setBounds(-extra, -extra, px + extra, px + extra)
                colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
                draw(canvas)
            }
        } else {
            icon.setBounds(0, 0, px, px)
            icon.draw(canvas)
        }
        return bitmap.asImageBitmap()
    }
}
