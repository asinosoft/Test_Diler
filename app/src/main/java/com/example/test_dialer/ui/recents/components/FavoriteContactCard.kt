package com.example.test_dialer.ui.recents.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_dialer.data.model.FavoriteContact
import com.example.test_dialer.ui.theme.SamsungGreen
import com.example.test_dialer.ui.theme.SamsungSmsBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun FavoriteContactCard(
    contact: FavoriteContact,
    isSelected: Boolean,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit,
    onSelect: (FavoriteContact) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { 50.dp.toPx() }
    val maxDragPx = with(density) { 100.dp.toPx() }
    var hasVibratedThreshold by remember { mutableStateOf(false) }

    val currentOffset = offsetX.value
    // Angle in degrees (-90 .. +90)
    val angle = ((currentOffset / maxDragPx).coerceIn(-1f, 1f)) * 90f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // 3D Cube Container (compacted by 20%)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(0.80f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(contact.id) {
                    detectTapGestures(
                        onLongPress = {
                            if (abs(offsetX.value) < 2f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelect(contact)
                            }
                        },
                        onTap = {
                            if (abs(offsetX.value) < 2f) {
                                onCall(contact.number)
                            }
                        }
                    )
                }
                .pointerInput(contact.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                val finalOffset = offsetX.value
                                if (finalOffset > thresholdPx) {
                                    onCall(contact.number)
                                } else if (finalOffset < -thresholdPx) {
                                    onSms(contact.number)
                                }
                                offsetX.animateTo(0f, animationSpec = spring())
                                hasVibratedThreshold = false
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f, animationSpec = spring())
                                hasVibratedThreshold = false
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                                if (!hasVibratedThreshold && (newOffset > thresholdPx || newOffset < -thresholdPx)) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    hasVibratedThreshold = true
                                } else if (hasVibratedThreshold && newOffset in -thresholdPx..thresholdPx) {
                                    hasVibratedThreshold = false
                                }
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
        ) {
            val cardWidthPx = with(density) { maxWidth.toPx() }
            val radiusPx = cardWidthPx / 2f

            // FRONT FACE (Contact Photo / Avatar)
            if (abs(angle) < 89.9f) {
                val rad = Math.toRadians(angle.toDouble())
                val transX = (radiusPx * Math.sin(rad)).toFloat()

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            this.rotationY = angle
                            this.translationX = transX
                            this.cameraDistance = 14f * density.density
                            this.transformOrigin = TransformOrigin(0.5f, 0.5f)
                        }
                        .then(
                            if (isSelected) {
                                Modifier.border(3.dp, SamsungGreen, RoundedCornerShape(20.dp))
                            } else Modifier
                        ),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    FavoriteAvatar(
                        name = contact.name,
                        photoUri = contact.photoUri
                    )
                }
            }

            // SIDE / ADJACENT FACE (Call / SMS)
            if (abs(angle) > 0.5f) {
                val isCall = angle > 0f // Swiping right -> Call face enters from left side
                val sideAngle = if (isCall) angle - 90f else angle + 90f
                val sideRad = Math.toRadians(sideAngle.toDouble())
                val sideTransX = (radiusPx * Math.sin(sideRad)).toFloat()

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            this.rotationY = sideAngle
                            this.translationX = sideTransX
                            this.cameraDistance = 14f * density.density
                            this.transformOrigin = TransformOrigin(0.5f, 0.5f)
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isCall) SamsungGreen else SamsungSmsBlue
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (isCall) Icons.Default.Phone else Icons.AutoMirrored.Filled.Message,
                                contentDescription = if (isCall) "Вызов" else "SMS",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isCall) "Вызов" else "SMS",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Contact Name
        Text(
            text = contact.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FavoriteAvatar(
    name: String,
    photoUri: String?
) {
    val context = LocalContext.current
    var avatarBitmap by remember(photoUri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photoUri) {
        if (!photoUri.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(photoUri)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        avatarBitmap = bitmap?.asImageBitmap()
                    }
                } catch (e: Exception) {
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
            contentDescription = name,
            modifier = Modifier.fillMaxSize(),
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
                .fillMaxSize()
                .background(avatarBgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )
        }
    }
}
