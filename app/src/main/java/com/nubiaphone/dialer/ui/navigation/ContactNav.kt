package com.nubiaphone.dialer.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/** Lets any screen open our own contact pages without threading callbacks through every layer. */
class ContactNav(
    val open: (contactId: Long) -> Unit,
    val add: (number: String?) -> Unit,
)

val LocalContactNav = staticCompositionLocalOf { ContactNav(open = {}, add = {}) }
