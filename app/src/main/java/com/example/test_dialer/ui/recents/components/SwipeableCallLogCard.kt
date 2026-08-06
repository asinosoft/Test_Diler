package com.example.test_dialer.ui.recents.components

import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_dialer.data.model.CallLogItem
import com.example.test_dialer.data.model.CallType
import com.example.test_dialer.ui.theme.IncomingGreen
import com.example.test_dialer.ui.theme.MissedRed
import com.example.test_dialer.ui.theme.OutgoingBlue
import com.example.test_dialer.ui.theme.SamsungGreen
import com.example.test_dialer.ui.theme.SamsungSmsBlue
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SwipeableCallLogCard(
    item: CallLogItem,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { 90.dp.toPx() }
    val maxDragPx = with(density) { 160.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
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
                            }
                        },
                        onDragCancel = { coroutineScope.launch { offsetX.animateTo(0f, animationSpec = spring()) } },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(-maxDragPx, maxDragPx)
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
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarView(name = item.name ?: item.number)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    val isMissed = item.type == CallType.MISSED || item.type == CallType.REJECTED
                    Text(
                        text = item.name ?: item.number,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMissed) MissedRed else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CallTypeIcon(item.type)
                        Spacer(Modifier.width(6.dp))
                        if (item.name != null) {
                            Text(
                                text = "${item.number} • ",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = formatTimestamp(item.timestamp),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { onCall(item.number) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = SamsungGreen.copy(alpha = 0.15f),
                        contentColor = SamsungGreen
                    ),
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(Icons.Default.Phone, "Позвонить", Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun AvatarView(name: String) {
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

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return if (DateUtils.isToday(timestamp)) {
        "Сегодня, ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))}"
    } else {
        SimpleDateFormat("dd MMM, HH:mm", Locale.forLanguageTag("ru")).format(Date(timestamp))
    }
}
