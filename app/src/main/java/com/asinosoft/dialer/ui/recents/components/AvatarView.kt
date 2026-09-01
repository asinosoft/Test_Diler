package com.asinosoft.dialer.ui.recents.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun avatarColorForKey(key: String): Color {
    val colors = listOf(
        Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8),
        Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6),
        Color(0xFF4FC3F7), Color(0xFF4DB6AC), Color(0xFF81C784),
        Color(0xFFAED581), Color(0xFFFF8A65), Color(0xFFA1887F)
    )
    val index = (key.hashCode() and Int.MAX_VALUE) % colors.size
    return colors[index]
}

@Composable
fun AvatarView(
    name: String,
    photoUri: String?,
    isUnsavedContact: Boolean = false
) {
    if (isUnsavedContact) {
        val avatarBgColor = remember(name) { avatarColorForKey(name) }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(avatarBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = "Контакт не сохранён",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        return
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val targetPx = with(density) { 48.dp.roundToPx() }.coerceAtLeast(1)

    var avatarBitmap by remember(photoUri) {
        mutableStateOf(photoUri?.let { AvatarBitmapCache.getImageBitmap(it) })
    }

    LaunchedEffect(photoUri, targetPx) {
        if (photoUri.isNullOrEmpty()) {
            avatarBitmap = null
            return@LaunchedEffect
        }

        val cached = AvatarBitmapCache.getImageBitmap(photoUri)
        if (cached != null) {
            avatarBitmap = cached
            return@LaunchedEffect
        }

        val decoded = AvatarBitmapCache.withDecodeSlot {
            withContext(Dispatchers.IO) {
                try {
                    decodeSampledBitmap(context, photoUri, targetPx)?.also {
                        AvatarBitmapCache.put(photoUri, it)
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
        avatarBitmap = decoded?.let { AvatarBitmapCache.getImageBitmap(photoUri) }
    }

    val bitmap = avatarBitmap
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Фото контакта",
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val avatarBgColor = remember(name) { avatarColorForKey(name) }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(avatarBgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
    }
}
