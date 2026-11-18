package com.nubiaphone.dialer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nubiaphone.dialer.telecom.IncomingPreview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nubiaphone.dialer.ui.navigation.NubiaPhoneNav
import com.nubiaphone.dialer.ui.theme.NubiaPhoneTheme
import com.nubiaphone.dialer.ui.update.UpdatePromptHost

class MainActivity : ComponentActivity() {
    /** Number handed over by a DIAL / tel: intent; the home screen opens the keypad with it. */
    private var dialRequest by mutableStateOf<String?>(null)

    /** Bumped from Settings “Güncellemeleri kontrol et”. */
    var updateCheckTick by mutableIntStateOf(0)
        private set

    fun requestUpdateCheck() {
        updateCheckTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // Same as nubiaSMS: black window straight into the list, no logo flash.
        splash.setKeepOnScreenCondition { false }
        splash.setOnExitAnimationListener { provider -> provider.remove() }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighestRefreshRate()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            NubiaPhoneTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    NubiaPhoneNav(
                        dialRequest = dialRequest,
                        onDialConsumed = { dialRequest = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                    UpdatePromptHost(manualCheckTick = updateCheckTick)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        // Developer check without a real call (incoming respects lock → UI / unlock → notification):
        // adb shell am start -n com.nubiaphone.dialer/.MainActivity --es incall_preview incoming|active|dialing|two|conference
        intent.getStringExtra("incall_preview")?.let { kind ->
            IncomingPreview.launch(this, kind)
            if (kind == "incoming" && !com.nubiaphone.dialer.telecom.CallNotifier.shouldShowIncomingUi(this)) {
                moveTaskToBack(true)
            }
            return
        }
        intent.getStringExtra("snackbar_preview")?.let { DevHooks.snackbar.value = it }
        if (intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW) {
            dialRequest = intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart.orEmpty()
        }
    }

    /** MyOS keeps apps at 60Hz unless asked; pick the fastest mode at the current resolution. */
    private fun requestHighestRefreshRate() {
        @Suppress("DEPRECATION")
        val display = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay)
            ?: return
        val current = display.mode
        val best =
            display.supportedModes
                .filter {
                    it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
                }.maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
    }
}
