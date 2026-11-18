package com.nubiaphone.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MyOsTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

/*
 * MyOS bottom tab bar, measured on NX741J in Saat (zte.com.cn.alarmclock) and matched with
 * SystemUI_MFV tokens (TASARIM.md → "Telefon uygulaması bileşenleri"):
 *   height       60dp (mfvc_bottom_height), 8dp side inset, equal-width tabs
 *   background   the page itself, translucent (mfvc_bottom_tab_bg_color #EBF5F5F5 / #E6000000), no divider
 *   item         ≈9dp top on screen (token says 6dp: screen wins), 24dp icon, ≈3dp to the label (measured),
 *                11sp label on a 15sp line, ≈10dp left below
 *   selected     filled icon + label in the system accent
 *   unselected   dark: icon #8F8F8F, label #B9B9B9 (white ≈56 % / ≈72 %); light: same ratios on #111111
 */
private val BarLight = Color(0xEBF5F5F5)
private val BarDark = Color(0xE6000000)

@Composable
fun MyOsTabBar(
    tabs: List<MyOsTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color(0xFF111111)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (dark) BarDark else BarLight)
            .navigationBarsPadding()
            .height(60.dp)
            .padding(horizontal = 8.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val active = index == selected
            val accent = myOsAccentInk()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = active,
                            onClick = { onSelect(index) },
                            role = Role.Tab,
                            // No ripple: MyOS tab bars do not flash on tap.
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ).padding(top = 9.dp),
            ) {
                Icon(
                    imageVector = if (active) tab.selectedIcon else tab.icon,
                    contentDescription = null,
                    tint = if (active) accent else ink.copy(alpha = 0.56f),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = tab.label,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = if (active) accent else ink.copy(alpha = 0.72f),
                    maxLines = 1,
                )
            }
        }
    }
}
