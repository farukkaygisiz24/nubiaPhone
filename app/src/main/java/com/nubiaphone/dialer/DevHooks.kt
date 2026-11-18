package com.nubiaphone.dialer

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Developer-only checks that need no real data change, triggered over adb:
 *   adb shell am start -n com.nubiaphone.dialer/.MainActivity --es snackbar_preview "Metin"
 */
object DevHooks {
    val snackbar = MutableStateFlow<String?>(null)
}
