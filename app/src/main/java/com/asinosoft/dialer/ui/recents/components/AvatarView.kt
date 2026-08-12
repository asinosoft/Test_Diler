package com.asinosoft.dialer.ui.recents.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AvatarView(name: String, photoUri: String?) {
    val context = LocalContext.current
    var avatarBitmap by remember(photoUri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photoUri) {
        if (!photoUri.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val uri = photoUri.toUri()
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        avatarBitmap = bitmap?.asImageBitmap()
                    }
                } catch (_: Exception) {
                    avatarBitmap = null
                }
            }
        } else {
            avatarBitmap = null
        }
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
        val avatarBgColor = remember(name) {
            val colors = listOf(
                Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8),
                Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6),
                Color(0xFF4FC3F7), Color(0xFF4DB6AC), Color(0xFF81C784),
                Color(0xFFAED581), Color(0xFFFF8A65), Color(0xFFA1887F)
            )
            val index = (name.hashCode() and Int.MAX_VALUE) % colors.size
            colors[index]
        }

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