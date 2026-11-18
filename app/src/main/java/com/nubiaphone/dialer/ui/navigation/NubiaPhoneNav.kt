package com.nubiaphone.dialer.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nubiaphone.dialer.ui.contacts.ContactDetailScreen
import com.nubiaphone.dialer.ui.contacts.ContactEditScreen
import com.nubiaphone.dialer.ui.home.HomeScreen
import com.nubiaphone.dialer.ui.recents.CallDetailScreen
import com.nubiaphone.dialer.ui.search.SearchScreen
import com.nubiaphone.dialer.ui.settings.BlockedNumbersScreen
import com.nubiaphone.dialer.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val CALL = "call/{key}"

    const val SETTINGS = "settings"
    const val BLOCKED = "settings/blocked"
    const val CONTACT = "contact/{id}"
    const val CONTACT_EDIT = "contact/edit/{id}"
    const val CONTACT_NEW = "contact/new?number={number}"

    fun call(key: String) = "call/${Uri.encode(key)}"

    fun contact(id: Long) = "contact/$id"

    fun contactEdit(id: Long) = "contact/edit/$id"

    fun contactNew(number: String?) = "contact/new?number=${Uri.encode(number.orEmpty())}"
}

@Composable
fun NubiaPhoneNav(
    dialRequest: String?,
    onDialConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = rememberNavController()
    // A DIAL / tel: intent always lands on the home keypad.
    LaunchedEffect(dialRequest) {
        if (dialRequest != null) nav.popBackStack(Routes.HOME, inclusive = false)
    }
    val slidePx = with(LocalDensity.current) { 96.dp.roundToPx() }
    val t = remember(slidePx) { MyOsTransitions(slidePx) }

    val contactNav =
        remember(nav) {
            ContactNav(
                open = { nav.navigate(Routes.contact(it)) },
                add = { nav.navigate(Routes.contactNew(it)) },
            )
        }

    CompositionLocalProvider(LocalContactNav provides contactNav) {
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        modifier = modifier,
        enterTransition = t.enter,
        exitTransition = t.exit,
        popEnterTransition = t.popEnter,
        popExitTransition = t.popExit,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                dialRequest = dialRequest,
                onDialConsumed = onDialConsumed,
                onSearch = { nav.navigate(Routes.SEARCH) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenCall = { nav.navigate(Routes.call(it)) },
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { nav.popBackStack() },
                onOpenCall = { nav.navigate(Routes.call(it)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() }, onBlocked = { nav.navigate(Routes.BLOCKED) })
        }
        composable(Routes.BLOCKED) {
            BlockedNumbersScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.CALL) { entry ->
            CallDetailScreen(
                groupKey = entry.arguments?.getString("key").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.CONTACT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            ContactDetailScreen(
                contactId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.contactEdit(it)) },
            )
        }
        composable(Routes.CONTACT_EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            ContactEditScreen(
                contactId = entry.arguments?.getLong("id"),
                number = null,
                onClose = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }
        composable(
            Routes.CONTACT_NEW,
            arguments = listOf(navArgument("number") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            ContactEditScreen(
                contactId = null,
                number = entry.arguments?.getString("number")?.takeIf { it.isNotBlank() },
                onClose = { nav.popBackStack() },
                // Replace the editor with the new contact's card.
                onSaved = { id ->
                    nav.popBackStack()
                    nav.navigate(Routes.contact(id))
                },
            )
        }
    }
    }
}
