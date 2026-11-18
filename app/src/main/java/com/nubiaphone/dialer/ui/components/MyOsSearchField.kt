package com.nubiaphone.dialer.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubiaphone.dialer.R
import com.nubiaphone.dialer.ui.theme.MyOsSearchShape
import com.nubiaphone.dialer.ui.theme.MyOsSearchSurface
import com.nubiaphone.dialer.ui.theme.MyOsSearchSurfaceLight

/**
 * MyOS shared search fill. Measured on the device: Ayarlar uses the system palette's
 * `system_accent1_50` in light mode (a faint accent tint), so it follows the theme.
 */
@Composable
fun myOsSearchContainer(): Color {
    val dark = isSystemInDarkTheme()
    return when {
        dark -> MyOsSearchSurface
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            colorResource(android.R.color.system_accent1_50)
        else -> MyOsSearchSurfaceLight
    }
}

@Composable
private fun searchMuted(): Color =
    if (isSystemInDarkTheme()) Color(0xFF5C5C5C) else Color(0xFF9A9A9A)

@Composable
private fun searchTextStyle(): TextStyle =
    MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        // Ayarlar placeholder measured ≈15sp (NX741J, side-by-side screencap).
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )

/** Bar chrome shared by the tappable home bar and the live search field. */
@Composable
private fun SearchBarChrome(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(38.dp) // Ayarlar bar measured 38.2dp on NX741J
                .clip(MyOsSearchShape)
                .background(myOsSearchContainer())
                .padding(horizontal = 13.dp), // icon glyph lands 15.7dp in, as in Ayarlar
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_myos_search_line),
            contentDescription = null,
            tint = searchMuted(),
            modifier = Modifier.size(18.5.dp), // glyph ≈13.8dp like Ayarlar
        )
        // Ayarlar keeps the text tight to the icon (≈3.7dp between glyphs).
        Box(Modifier.padding(start = 1.dp).weight(1f)) { content() }
    }
}

/**
 * Home-screen search bar. Like Ayarlar it is not editable in place: tapping it opens
 * the dedicated search page.
 */
@Composable
fun MyOsSearchBar(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchBarChrome(modifier.clip(MyOsSearchShape).clickable(onClick = onClick)) {
        Text(text = placeholder, style = searchTextStyle().copy(color = searchMuted()))
    }
}

/** Live search field used at the top of the search page. */
@Composable
fun MyOsSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
) {
    val style = searchTextStyle()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            SearchBarChrome(Modifier) {
                if (value.isEmpty()) {
                    Text(text = placeholder, style = style.copy(color = searchMuted()))
                }
                inner()
            }
        },
    )
}
