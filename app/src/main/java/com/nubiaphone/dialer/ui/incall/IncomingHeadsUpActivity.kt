package com.nubiaphone.dialer.ui.incall

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nubiaphone.dialer.telecom.CallNotifier
import com.nubiaphone.dialer.telecom.CallRegistry
import com.nubiaphone.dialer.telecom.IncomingPreview
import com.nubiaphone.dialer.ui.components.ContactAvatar
import com.nubiaphone.dialer.ui.components.myOsAccentFill
import com.nubiaphone.dialer.ui.components.myOsDanger
import com.nubiaphone.dialer.ui.theme.NubiaPhoneTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Unlocked incoming: a top banner the user can swipe **up** to silence (system HUN swipe-up
 * does not cancel the notification, so deleteIntent never fires). Tap opens the full screen.
 */
class IncomingHeadsUpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFormat(PixelFormat.TRANSLUCENT)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.attributes =
            window.attributes.apply {
                gravity = Gravity.TOP
                flags = flags or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            }
        if (intent.getBooleanExtra(EXTRA_FINISH, false)) {
            finish()
            return
        }
        val preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Gelen arama"
        val subtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: "Gelen arama"
        val photo = intent.getStringExtra(EXTRA_PHOTO)
        val callId = intent.getIntExtra(EXTRA_CALL_ID, -1)

        setContent {
            NubiaPhoneTheme {
                HeadsUpBanner(
                    title = title,
                    subtitle = subtitle,
                    photoUri = photo,
                    onSwipeUpSilence = {
                        if (preview) IncomingPreview.silence(this) else CallNotifier.silence(this)
                        finish()
                    },
                    onAnswer = {
                        if (preview) {
                            IncomingPreview.silence(this)
                            startActivity(
                                InCallActivity.intent(this)
                                    .putExtra(InCallActivity.EXTRA_PREVIEW, "active")
                                    .putExtra(InCallActivity.EXTRA_PREVIEW_SILENT, true),
                            )
                        } else if (callId >= 0) {
                            CallRegistry.answer(callId)
                            startActivity(InCallActivity.intent(this))
                        }
                        finish()
                    },
                    onDecline = {
                        if (preview) IncomingPreview.dismiss(this) else if (callId >= 0) CallRegistry.reject(callId)
                        finish()
                    },
                    onOpen = {
                        if (preview) {
                            startActivity(
                                InCallActivity.intent(this)
                                    .putExtra(InCallActivity.EXTRA_PREVIEW, "incoming")
                                    .putExtra(InCallActivity.EXTRA_PREVIEW_SILENT, true),
                            )
                        } else {
                            startActivity(InCallActivity.intent(this))
                        }
                        finish()
                    },
                )
            }
        }

        // Auto-close when the ring was silenced elsewhere (volume key).
        // Preview: IncomingPreview.silenced; live: CallNotifier.ringerSilenced — polled lightly.
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_FINISH, false)) finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
            if (preview) IncomingPreview.silence(this) else CallNotifier.silence(this)
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_PREVIEW = "preview"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SUBTITLE = "subtitle"
        const val EXTRA_PHOTO = "photo"
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_FINISH = "finish"

        fun intentPreview(context: Context): Intent =
            Intent(context, IncomingHeadsUpActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_PREVIEW, true)
                .putExtra(EXTRA_TITLE, "Önizleme Kişi")
                .putExtra(EXTRA_SUBTITLE, "Gelen arama · yukarı kaydır: sessiz")

        fun intentLive(context: Context, title: String, subtitle: String, photo: String?, callId: Int): Intent =
            Intent(context, IncomingHeadsUpActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_PREVIEW, false)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_SUBTITLE, subtitle)
                .putExtra(EXTRA_PHOTO, photo)
                .putExtra(EXTRA_CALL_ID, callId)

        fun finishExisting(context: Context) {
            context.startActivity(
                Intent(context, IncomingHeadsUpActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_FINISH, true),
            )
        }
    }
}

@Composable
private fun HeadsUpBanner(
    title: String,
    subtitle: String,
    photoUri: String?,
    onSwipeUpSilence: () -> Unit,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onOpen: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val density = LocalDensity.current
    val dismissPx = with(density) { (-56).dp.toPx() }

    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetY.value <= dismissPx) {
                                offsetY.animateTo(-400f, tween(120))
                                onSwipeUpSilence()
                            } else {
                                offsetY.animateTo(0f, tween(180))
                            }
                        }
                    },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        // Only allow dragging upward (negative).
                        scope.launch {
                            val next = (offsetY.value + amount).coerceAtMost(0f)
                            offsetY.snapTo(next)
                        }
                    },
                )
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactAvatar(photoUri = photoUri, name = title, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            RoundMini(Icons.Filled.CallEnd, myOsDanger(), onDecline)
            Spacer(Modifier.width(8.dp))
            RoundMini(Icons.Filled.Call, myOsAccentFill(), onAnswer)
        }
    }
}

@Composable
private fun RoundMini(icon: ImageVector, fill: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}
