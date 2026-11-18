package com.nubiaphone.dialer.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Round contact picture: the phone-book photo when there is one, otherwise the first
 * letter of the contact's name, otherwise a person glyph (unknown number / sender).
 *
 * Photos are decoded off the main thread, downsampled to the avatar's pixel size and
 * kept in a small in-memory cache, so fast list flings never re-read the provider.
 */
@Composable
fun ContactAvatar(
    photoUri: String?,
    name: String?,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val photo by produceState<ImageBitmap?>(initialValue = photoUri?.let { AvatarCache.get(it) }, photoUri, px) {
        // produceState keeps the old value when its keys change; reset it so a reused slot
        // never shows the previous person's picture.
        value = photoUri?.let { AvatarCache.get(it) }
        if (photoUri != null && value == null) value = AvatarCache.load(context, photoUri, px)
    }
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = photo
        val initial = name?.trim()?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
        when {
            bitmap != null ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size),
                )
            initial != null ->
                Text(
                    text = initial.toString(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium,
                    fontSize = (size.value * 0.44f).sp,
                )
            else ->
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(size * 0.56f),
                )
        }
    }
}

private object AvatarCache {
    // ~4 MB of small thumbnails is plenty for a conversation list.
    private val cache =
        object : LruCache<String, ImageBitmap>(4 * 1024 * 1024) {
            override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
        }

    fun get(uri: String): ImageBitmap? = cache.get(uri)

    suspend fun load(context: Context, uri: String, sizePx: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            cache.get(uri)?.let { return@withContext it }
            try {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp: Bitmap =
                    resolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, opts) }
                        ?: return@withContext null
                bmp.asImageBitmap().also { cache.put(uri, it) }
            } catch (_: Exception) {
                null
            }
        }
}
