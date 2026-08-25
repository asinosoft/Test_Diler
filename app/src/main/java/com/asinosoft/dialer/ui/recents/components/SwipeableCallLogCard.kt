package com.asinosoft.dialer.ui.recents.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asinosoft.dialer.data.model.CallLogItem
import com.asinosoft.dialer.data.model.CallType
import com.asinosoft.dialer.ui.theme.IncomingGreen
import com.asinosoft.dialer.ui.theme.MissedRed
import com.asinosoft.dialer.ui.theme.OutgoingBlue
import com.asinosoft.dialer.ui.theme.SamsungGreen
import com.asinosoft.dialer.ui.theme.SamsungSmsBlue
import com.asinosoft.dialer.util.formatPhoneNumber
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SharedSimCardShape = SimCardShape(cutSizeDp = 2.5f)

private val timeFormatter = ThreadLocal.withInitial {
    SimpleDateFormat("HH:mm", Locale.getDefault())
}

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
    onItemClick: ((CallLogItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val offsetX = remember { Animatable(0f) }
    var drawnOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { 90.dp.toPx() }
    val maxDragPx = with(density) { 160.dp.toPx() }
    var hasVibratedThreshold by remember { mutableStateOf(false) }

    val contactKey = remember(item.id, item.number) {
        item.id.ifBlank { digitsOnlyPhone(item.number) }
    }

    // Defaults only at compose — SharedPreferences loaded on first drag (avoids scroll jank)
    var rightVisuals by remember {
        mutableStateOf(getSwipeBackgroundVisuals(null, defaultIsRight = true))
    }
    var leftVisuals by remember {
        mutableStateOf(getSwipeBackgroundVisuals(null, defaultIsRight = false))
    }
    var customRightAction by remember { mutableStateOf<CustomSwipeAction?>(null) }
    var customLeftAction by remember { mutableStateOf<CustomSwipeAction?>(null) }
    var swipeActionsLoaded by remember(contactKey) { mutableStateOf(false) }

    fun ensureSwipeActionsLoaded() {
        if (swipeActionsLoaded) return
        swipeActionsLoaded = true
        customRightAction =
            getCustomSwipeAction(context, contactKey, isRight = true, fallbackNumber = item.number)
        customLeftAction =
            getCustomSwipeAction(context, contactKey, isRight = false, fallbackNumber = item.number)
        rightVisuals = getSwipeBackgroundVisuals(customRightAction, defaultIsRight = true)
        leftVisuals = getSwipeBackgroundVisuals(customLeftAction, defaultIsRight = false)
    }

    val formattedNumber = remember(item.number) { formatPhoneNumber(item.number) }
    val displayName = remember(item.name, item.number, item.count, formattedNumber) {
        val baseName = item.name ?: formattedNumber
        if (item.count > 1) "$baseName (${item.count})" else baseName
    }
    val subText = remember(item.name, formattedNumber) {
        if (item.name != null) formattedNumber else "Не сохранено"
    }
    val timeText = remember(item.timestamp) { formatTimeOnly(item.timestamp) }
    val avatarName = remember(item.name, formattedNumber) { item.name ?: formattedNumber }
    val isMissed = item.type == CallType.MISSED || item.type == CallType.REJECTED

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (drawnOffset > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(rightVisuals.backgroundColor),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 24.dp)
                    ) {
                        Icon(
                            imageVector = rightVisuals.icon,
                            contentDescription = rightVisuals.label,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            rightVisuals.label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            if (drawnOffset < 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(leftVisuals.backgroundColor),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 24.dp)
                    ) {
                        Text(
                            leftVisuals.label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            imageVector = leftVisuals.icon,
                            contentDescription = leftVisuals.label,
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
                .graphicsLayer { translationX = drawnOffset }
                .pointerInput(item.id) {
                    detectTapGestures(
                        onTap = {
                            if (kotlin.math.abs(drawnOffset) < 2f && onItemClick != null) {
                                onItemClick(item)
                            }
                        }
                    )
                }
                .pointerInput(item.id) {
                    detectHorizontalDragGestures(
                        onDragStart = { ensureSwipeActionsLoaded() },
                        onDragEnd = {
                            coroutineScope.launch {
                                val finalOffset = drawnOffset
                                ensureSwipeActionsLoaded()
                                if (finalOffset > thresholdPx) {
                                    val action = customRightAction
                                    if (action != null) {
                                        executeCustomSwipeAction(
                                            context,
                                            action,
                                            { num, _ -> onCall(num) },
                                            onSms
                                        )
                                    } else {
                                        onCall(item.number)
                                    }
                                } else if (finalOffset < -thresholdPx) {
                                    val action = customLeftAction
                                    if (action != null) {
                                        executeCustomSwipeAction(
                                            context,
                                            action,
                                            { num, _ -> onCall(num) },
                                            onSms
                                        )
                                    } else {
                                        onSms(item.number)
                                    }
                                }
                                offsetX.snapTo(drawnOffset)
                                offsetX.animateTo(0f, animationSpec = spring()) {
                                    drawnOffset = value
                                }
                                drawnOffset = 0f
                                hasVibratedThreshold = false
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.snapTo(drawnOffset)
                                offsetX.animateTo(0f, animationSpec = spring()) {
                                    drawnOffset = value
                                }
                                drawnOffset = 0f
                                hasVibratedThreshold = false
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset =
                                (drawnOffset + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                            drawnOffset = newOffset
                            if (!hasVibratedThreshold && (newOffset > thresholdPx || newOffset < -thresholdPx)) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                hasVibratedThreshold = true
                            } else if (hasVibratedThreshold && newOffset in -thresholdPx..thresholdPx) {
                                hasVibratedThreshold = false
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
                AvatarView(
                    name = avatarName,
                    photoUri = item.photoUri
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
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
                    text = timeText,
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
            .clip(SharedSimCardShape)
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
private fun CallTypeIcon(type: CallType) {
    val (icon, color, desc) = when (type) {
        CallType.INCOMING -> Triple(
            Icons.AutoMirrored.Filled.CallReceived,
            IncomingGreen,
            "Входящий"
        )

        CallType.OUTGOING -> Triple(Icons.AutoMirrored.Filled.CallMade, OutgoingBlue, "Исходящий")
        CallType.MISSED -> Triple(Icons.AutoMirrored.Filled.CallMissed, MissedRed, "Пропущенный")
        CallType.REJECTED -> Triple(Icons.Default.CallEnd, MissedRed, "Отклоненный")
    }

    Icon(
        imageVector = icon,
        contentDescription = desc,
        tint = color,
        modifier = Modifier.size(16.dp)
    )
}

private fun formatTimeOnly(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return timeFormatter.get()!!.format(Date(timestamp))
}

private fun digitsOnlyPhone(number: String): String {
    val sb = StringBuilder(number.length)
    for (c in number) {
        if (c.isDigit() || c == '+') sb.append(c)
    }
    return sb.toString()
}
