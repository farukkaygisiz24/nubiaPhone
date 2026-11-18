package com.nubiaphone.dialer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** From MyOS `mfvc_radius` / `mfvc_card_bg_radius` = 16dp — list/card groups */
val MyOsCorner = 16.dp

/** From MyOS `mfvc_card_item_radius` = 12dp */
val MyOsCornerSmall = 12.dp

/**
 * Ayarlar search bar, measured on the device screenshot (38dp tall, ~12dp corners).
 * The 28dp `search_bar_corner_radius` token does not match what MyOS actually draws.
 */
val MyOsSearchCorner = 12.dp

val NubiaPhoneShapes =
    Shapes(
        extraSmall = RoundedCornerShape(MyOsCornerSmall),
        small = RoundedCornerShape(MyOsCornerSmall),
        medium = RoundedCornerShape(MyOsCorner),
        large = RoundedCornerShape(MyOsCorner),
        extraLarge = RoundedCornerShape(MyOsCorner),
    )

val MyOsCardShape = RoundedCornerShape(MyOsCorner)
val MyOsSearchShape = RoundedCornerShape(MyOsSearchCorner)
