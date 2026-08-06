package com.example.test_dialer.ui.recents.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.CallEnd
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_dialer.data.model.CallLogItem
import com.example.test_dialer.data.model.CallType
import com.example.test_dialer.ui.theme.IncomingGreen
import com.example.test_dialer.ui.theme.MissedRed
import com.example.test_dialer.ui.theme.OutgoingBlue
import com.example.test_dialer.ui.theme.SamsungGreen
import com.example.test_dialer.ui.theme.SamsungSmsBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class SimCardShape(private val cutSizeDp: Float = 2.5f) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cut = density.density * cutSizeDp
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width - cut, 0f)
            lineTo(size.width, cut)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun SwipeableCallLogCard(
    item: CallLogItem,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { 90.dp.toPx() }
    val maxDragPx = with(density) { 160.dp.toPx() }
    var hasVibratedThreshold by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        val currentOffset = offsetX.value

        Box(modifier = Modifier.fillMaxSize()) {
            if (currentOffset > 0f) {
                Box(
                    modifier = Modifier.fillMaxSize().background(SamsungGreen),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Позвонить",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Вызов", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            if (currentOffset < 0f) {
                Box(
                    modifier = Modifier.fillMaxSize().background(SamsungSmsBlue),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 24.dp)
                    ) {
                        Text("Сообщение", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Message,
                            contentDescription = "Написать SMS",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(currentOffset.roundToInt(), 0) }
                .pointerInput(item.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                val finalOffset = offsetX.value
                                if (finalOffset > thresholdPx) onCall(item.number)
                                else if (finalOffset < -thresholdPx) onSms(item.number)
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
                },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarView(name = item.name ?: item.number, photoUri = item.photoUri)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    val isMissed = item.type == CallType.MISSED || item.type == CallType.REJECTED
                    val baseName = item.name ?: item.number
                    val displayName = if (item.count > 1) "$baseName (${item.count})" else baseName

                    Text(
                        text = displayName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMissed) MissedRed else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CallTypeIcon(item.type)
                        Spacer(Modifier.width(5.dp))
                        SimBadge(simNumber = item.simNumber)
                        Spacer(Modifier.width(5.dp))
                        val subText = if (item.name != null) {
                            item.number
                        } else {
                            "Не сохранено"
                        }
                        Text(
                            text = subText,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatTimeOnly(item.timestamp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SimBadge(simNumber: Int) {
    val simBgColor = if (simNumber == 2) SamsungGreen else SamsungSmsBlue

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 10.dp, height = 12.dp)
            .clip(SimCardShape(cutSizeDp = 2.5f))
            .background(simBgColor)
    ) {
        Text(
            text = "$simNumber",
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            style = TextStyle(
                platformStyle = PlatformTextStyle(
                    includeFontPadding = false
                )
            ),
            modifier = Modifier.offset(y = (-0.5).dp)
        )
    }
}

@Composable
private fun AvatarView(name: String, photoUri: String?) {
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
            Text(text = initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}

@Composable
private fun CallTypeIcon(type: CallType) {
    val (icon, color, desc) = when (type) {
        CallType.INCOMING -> Triple(Icons.AutoMirrored.Filled.CallReceived, IncomingGreen, "Входящий")
        CallType.OUTGOING -> Triple(Icons.AutoMirrored.Filled.CallMade, OutgoingBlue, "Исходящий")
        CallType.MISSED -> Triple(Icons.AutoMirrored.Filled.CallMissed, MissedRed, "Пропущенный")
        CallType.REJECTED -> Triple(Icons.Default.CallEnd, MissedRed, "Отклоненный")
    }

    Icon(imageVector = icon, contentDescription = desc, tint = color, modifier = Modifier.size(16.dp))
}

private fun formatTimeOnly(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
