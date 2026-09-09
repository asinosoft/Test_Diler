package com.asinosoft.dialer.ui.recents.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.asinosoft.dialer.R
import com.asinosoft.dialer.data.model.FavoriteContact
import com.asinosoft.dialer.ui.theme.SamsungGreen
import com.asinosoft.dialer.util.PhoneNumberHelper
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SwipeableFavoriteContactCard(
    contact: FavoriteContact,
    isSelected: Boolean = false,
    isDragging: Boolean = false,
    dragVisualOffsetY: Float = 0f,
    onCall: (String, Int?) -> Unit,
    onSms: (String) -> Unit,
    onClick: (FavoriteContact) -> Unit,
    onDragStart: (() -> Unit)? = null,
    onDrag: ((Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { 90.dp.toPx() }
    val maxDragPx = with(density) { 160.dp.toPx() }
    var hasVibratedThreshold by remember { mutableStateOf(false) }

    val contactKey = remember(contact) {
        contact.id.ifBlank { contact.number.replace(Regex("[^0-9+]"), "") }
    }

    val cacheCleared by SwipeActionCache.lastChangedAt.collectAsState()

    val customRightAction = remember(contactKey, contact.number, contact.name, cacheCleared) {
        getCustomSwipeAction(
            context,
            contactKey,
            isRight = true,
            fallbackNumber = contact.number,
            contactName = contact.name
        )
    }
    val customLeftAction = remember(contactKey, contact.number, contact.name, cacheCleared) {
        getCustomSwipeAction(
            context,
            contactKey,
            isRight = false,
            fallbackNumber = contact.number,
            contactName = contact.name
        )
    }

    val rightVisuals = remember(customRightAction) {
        getSwipeBackgroundVisuals(customRightAction, defaultIsRight = true, context = context)
    }
    val leftVisuals = remember(customLeftAction) {
        getSwipeBackgroundVisuals(customLeftAction, defaultIsRight = false, context = context)
    }

    val formattedNumber = remember(contact.number) {
        PhoneNumberHelper.format(contact.number)
    }

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnCall by rememberUpdatedState(onCall)
    val currentOnSms by rememberUpdatedState(onSms)
    val currentCustomRightAction by rememberUpdatedState(customRightAction)
    val currentCustomLeftAction by rememberUpdatedState(customLeftAction)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .zIndex(if (isDragging) 100f else 0f)
            .graphicsLayer {
                if (isDragging) {
                    translationY = dragVisualOffsetY
                    scaleX = 1.03f
                    scaleY = 1.03f
                    shadowElevation = 16f
                }
            }
            .clip(RoundedCornerShape(20.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (offsetX.value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(rightVisuals.backgroundColor),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(start = 24.dp)
                    ) {
                        val isSim1 = customRightAction?.actionType == "call_sim1"
                        val isSim2 = customRightAction?.actionType == "call_sim2"
                        if (isSim1) {
                            Icon(
                                painter = painterResource(R.drawable.ic_sim1),
                                contentDescription = "SIM 1",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(26.dp)
                            )
                        } else if (isSim2) {
                            Icon(
                                painter = painterResource(R.drawable.ic_sim2),
                                contentDescription = "SIM 2",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(26.dp)
                            )
                        } else {
                            Icon(
                                imageVector = rightVisuals.icon,
                                contentDescription = rightVisuals.label,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = rightVisuals.label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            } else if (offsetX.value < 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(leftVisuals.backgroundColor),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(end = 24.dp)
                    ) {
                        Text(
                            text = leftVisuals.label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        if (leftVisuals.iconBitmap != null) {
                            Image(
                                bitmap = leftVisuals.iconBitmap,
                                contentDescription = leftVisuals.label,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = leftVisuals.icon,
                                contentDescription = leftVisuals.label,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .fillMaxSize()
                    .pointerInput(contact.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    offsetX.snapTo(0f)
                                }
                                currentOnDragStart?.invoke()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentOnDrag?.invoke(dragAmount.y)
                            },
                            onDragEnd = {
                                currentOnDragEnd?.invoke()
                            },
                            onDragCancel = {
                                currentOnDragEnd?.invoke()
                            }
                        )
                    }
                    .pointerInput(contact.id, isDragging) {
                        // Restart only when reorder drag begins/ends — keeps long-press detector alive.
                        if (!isDragging) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    coroutineScope.launch {
                                        val current = offsetX.value
                                        if (current >= thresholdPx) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            offsetX.snapTo(0f)
                                            executeSwipe(
                                                context = context,
                                                isRight = true,
                                                action = currentCustomRightAction,
                                                fallbackNumber = contact.number,
                                                onCall = { num, sim -> currentOnCall(num, sim) },
                                                onSms = currentOnSms
                                            )
                                        } else if (current <= -thresholdPx) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            offsetX.snapTo(0f)
                                            executeSwipe(
                                                context = context,
                                                isRight = false,
                                                action = currentCustomLeftAction,
                                                fallbackNumber = contact.number,
                                                onCall = { num, sim -> currentOnCall(num, sim) },
                                                onSms = currentOnSms
                                            )
                                        }

                                        offsetX.animateTo(0f, spring())
                                        hasVibratedThreshold = false
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        offsetX.animateTo(0f, spring())
                                        hasVibratedThreshold = false
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    val target = (offsetX.value + dragAmount)
                                        .coerceIn(-maxDragPx, maxDragPx)

                                    val crossedThreshold =
                                        abs(target) >= thresholdPx && abs(offsetX.value) < thresholdPx
                                    if (crossedThreshold && !hasVibratedThreshold) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        hasVibratedThreshold = true
                                    } else if (abs(target) < thresholdPx) {
                                        hasVibratedThreshold = false
                                    }

                                    coroutineScope.launch {
                                        offsetX.snapTo(target)
                                    }
                                }
                            )
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        currentOnClick(contact)
                    },
                color = if (isSelected) {
                    SamsungGreen.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarView(
                        name = contact.name,
                        photoUri = contact.photoUri
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = contact.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = formattedNumber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Gold Star Icon
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Избранное",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun executeSwipe(
    context: Context,
    isRight: Boolean,
    action: CustomSwipeAction?,
    fallbackNumber: String,
    onCall: (String, Int?) -> Unit,
    onSms: (String) -> Unit
) {
    if (action != null) {
        executeCustomSwipeAction(
            context = context,
            action = action,
            onCall = onCall,
            onSms = onSms
        )
    } else {
        if (isRight) {
            onCall(fallbackNumber, null)
        } else {
            try {
                val smsIntent = Intent(Intent.ACTION_SENDTO, "smsto:${Uri.encode(fallbackNumber)}".toUri()).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(smsIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "Не удалось открыть SMS", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
