package com.example.test_dialer.ui.dialer

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import android.telephony.SubscriptionManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import com.example.test_dialer.data.model.CallType
import com.example.test_dialer.ui.theme.IncomingGreen
import com.example.test_dialer.ui.theme.MissedRed
import com.example.test_dialer.ui.theme.OutgoingBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_dialer.ui.recents.RecentsViewModel
import com.example.test_dialer.ui.recents.SearchDialerItem
import com.example.test_dialer.ui.recents.components.executeCustomSwipeAction
import com.example.test_dialer.ui.recents.components.getCustomSwipeAction
import com.example.test_dialer.ui.recents.components.getSwipeBackgroundVisuals
import com.example.test_dialer.ui.theme.SamsungGreen
import com.example.test_dialer.ui.theme.SamsungSmsBlue
import com.example.test_dialer.util.formatPhoneNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SearchDialerScreen(
    viewModel: RecentsViewModel,
    onCall: (String, Int?) -> Unit,
    onSms: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val dialerQuery by viewModel.dialerQuery.collectAsState()
    val results by viewModel.filteredDialerResults.collectAsState()

    val defaultSimSlot = remember(context) {
        try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val defaultSubId = SubscriptionManager.getDefaultVoiceSubscriptionId()
            if (subManager != null && defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                @Suppress("MissingPermission")
                val activeList = subManager.activeSubscriptionInfoList
                val matchedInfo = activeList?.find { it.subscriptionId == defaultSubId }
                if (matchedInfo != null) {
                    matchedInfo.simSlotIndex + 1
                } else 1
            } else 1
        } catch (e: Exception) {
            1
        }
    }

    var selectedSimSlot by remember { mutableIntStateOf(defaultSimSlot) }
    var isDialpadVisible by remember { mutableStateOf(true) }

    val targetSimBgColor = if (selectedSimSlot == 1) SamsungSmsBlue else SamsungGreen
    val animatedSimBgColor by animateColorAsState(
        targetValue = targetSimBgColor,
        animationSpec = tween(durationMillis = 200),
        label = "simBgColor"
    )

    BackHandler {
        onClose()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp)
        ) {
            // Top Search Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                OutlinedTextField(
                    value = dialerQuery,
                    onValueChange = { viewModel.onDialerQueryChange(it) },
                    placeholder = {
                        Text(
                            text = "Поиск по имени или номеру...",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Поиск",
                            tint = SamsungGreen
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (dialerQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearDialerQuery() }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Очистить"
                                    )
                                }
                            }
                            IconButton(onClick = { isDialpadVisible = !isDialpadVisible }) {
                                Icon(
                                    imageVector = if (isDialpadVisible) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = if (isDialpadVisible) "Скрыть клавиатуру" else "Показать клавиатуру",
                                    tint = SamsungGreen
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SamsungGreen,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // Results List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.any { it.changedToDown() }) {
                                    isDialpadVisible = false
                                }
                            }
                        }
                    }
            ) {
                if (results.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (dialerQuery.isBlank()) "Введите номер или имя" else "Контакты не найдены",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = results,
                            key = { it.id }
                        ) { item ->
                            SwipeableSearchDialerCard(
                                item = item,
                                context = context,
                                selectedSimSlot = selectedSimSlot,
                                onCall = onCall,
                                onSms = onSms
                            )
                        }
                    }
                }
            }

            // Floating Dialpad FAB Button (shown when dialpad is hidden)
            if (!isDialpadVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp, end = 20.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    FloatingActionButton(
                        onClick = { isDialpadVisible = true },
                        containerColor = SamsungGreen,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dialpad,
                            contentDescription = "Развернуть клавиатуру",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Bottom Dialpad Component
            AnimatedVisibility(
                visible = isDialpadVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // T9 Dialpad Buttons Grid (3x4)
                        val dialpadButtons = remember {
                            listOf(
                                Triple("1", "", ""),
                                Triple("2", "ABC", "АБВГ"),
                                Triple("3", "DEF", "ДЕЖЗ"),
                                Triple("4", "GHI", "ИЙКЛ"),
                                Triple("5", "JKL", "МНОПР"),
                                Triple("6", "MNO", "СТУФ"),
                                Triple("7", "PQRS", "ХЦЧШ"),
                                Triple("8", "TUV", "ЩЪЫЬ"),
                                Triple("9", "WXYZ", "ЭЮЯ"),
                                Triple("*", "", ""),
                                Triple("0", "+", ""),
                                Triple("#", "", "")
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (row in 0 until 4) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    for (col in 0 until 3) {
                                        val index = row * 3 + col
                                        val (digit, lettersEn, lettersRu) = dialpadButtons[index]

                                        Surface(
                                            modifier = Modifier
                                                .size(width = 90.dp, height = 56.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                                .pointerInput(digit) {
                                                    detectTapGestures(
                                                        onTap = { viewModel.appendDialerDigit(digit) },
                                                        onLongPress = {
                                                            if (digit == "0") {
                                                                viewModel.appendDialerDigit("+")
                                                            }
                                                        }
                                                    )
                                                },
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.background
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = digit,
                                                    fontSize = 22.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                                if (lettersEn.isNotEmpty() || lettersRu.isNotEmpty()) {
                                                    Text(
                                                        text = "$lettersEn $lettersRu".trim(),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Bottom Actions Row: Equal Distance Spacing + Lifted Above System Gesture Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Single Animated SIM Selector Toggle Button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = animatedSimBgColor,
                                modifier = Modifier.clickable {
                                    selectedSimSlot = if (selectedSimSlot == 1) 2 else 1
                                }
                            ) {
                                Text(
                                    text = "SIM $selectedSimSlot",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            // Center: Green Call FAB Button
                            FloatingActionButton(
                                onClick = {
                                    if (dialerQuery.isNotBlank()) {
                                        onCall(dialerQuery, selectedSimSlot)
                                    }
                                },
                                containerColor = SamsungGreen,
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Позвонить",
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Backspace Delete Button
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { viewModel.deleteDialerDigit() },
                                            onLongPress = { viewModel.clearDialerQuery() }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Backspace,
                                    contentDescription = "Удалить",
                                    tint = if (dialerQuery.isNotEmpty()) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeableSearchDialerCard(
    item: SearchDialerItem,
    context: Context,
    selectedSimSlot: Int,
    onCall: (String, Int?) -> Unit,
    onSms: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var photoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val thresholdPx = with(LocalDensity.current) { 90.dp.toPx() }
    val maxDragPx = with(LocalDensity.current) { 150.dp.toPx() }

    var hasVibratedRight by remember { mutableStateOf(false) }
    var hasVibratedLeft by remember { mutableStateOf(false) }

    val contactKey = if (item.id.isNotBlank()) item.id else item.number.replace(Regex("[^0-9+]"), "")

    LaunchedEffect(item.photoUri) {
        if (!item.photoUri.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(item.photoUri)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        photoBitmap = bitmap?.asImageBitmap()
                    }
                } catch (e: Exception) {
                    photoBitmap = null
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
    ) {
        val currentOffset = offsetX.value

        val customRightAction = if (currentOffset > 0f) {
            getCustomSwipeAction(context, contactKey, isRight = true, fallbackNumber = item.number)
        } else null
        val rightVisuals = getSwipeBackgroundVisuals(customRightAction, defaultIsRight = true)

        val customLeftAction = if (currentOffset < 0f) {
            getCustomSwipeAction(context, contactKey, isRight = false, fallbackNumber = item.number)
        } else null
        val leftVisuals = getSwipeBackgroundVisuals(customLeftAction, defaultIsRight = false)

        // Background layer visible during swipe
        if (currentOffset > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(rightVisuals.backgroundColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = rightVisuals.icon,
                        contentDescription = rightVisuals.label,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = rightVisuals.label,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        if (currentOffset < 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(leftVisuals.backgroundColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = leftVisuals.label,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = leftVisuals.icon,
                        contentDescription = leftVisuals.label,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Foreground Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier
                .offset { IntOffset(currentOffset.roundToInt(), 0) }
                .fillMaxWidth()
                .pointerInput(item.id) {
                    detectTapGestures(
                        onTap = {
                            if (customRightAction != null) {
                                executeCustomSwipeAction(context, customRightAction, { num, sim -> onCall(num, sim) }, onSms)
                            } else {
                                onCall(item.number, selectedSimSlot)
                            }
                        }
                    )
                }
                .pointerInput(item.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                val targetOffset = offsetX.value
                                if (targetOffset >= thresholdPx) {
                                    if (customRightAction != null) {
                                        executeCustomSwipeAction(context, customRightAction, { num, sim -> onCall(num, sim) }, onSms)
                                    } else {
                                        onCall(item.number, selectedSimSlot)
                                    }
                                } else if (targetOffset <= -thresholdPx) {
                                    if (customLeftAction != null) {
                                        executeCustomSwipeAction(context, customLeftAction, { num, sim -> onCall(num, sim) }, onSms)
                                    } else {
                                        onSms(item.number)
                                    }
                                }
                                offsetX.animateTo(0f, spring())
                                hasVibratedRight = false
                                hasVibratedLeft = false
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f, spring())
                                hasVibratedRight = false
                                hasVibratedLeft = false
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                                offsetX.snapTo(newOffset)

                                if (newOffset >= thresholdPx && !hasVibratedRight) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    hasVibratedRight = true
                                } else if (newOffset < thresholdPx) {
                                    hasVibratedRight = false
                                }

                                if (newOffset <= -thresholdPx && !hasVibratedLeft) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    hasVibratedLeft = true
                                } else if (newOffset > -thresholdPx) {
                                    hasVibratedLeft = false
                                }
                            }
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar Circle
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (photoBitmap != null) {
                            Image(
                                bitmap = photoBitmap!!,
                                contentDescription = "Аватар",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val initial = item.name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
                            Text(
                                text = initial,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Middle Column: Top Row (Name + Time) and Bottom Row (CallType + SIM Badge + Number)
                Column(modifier = Modifier.weight(1f)) {
                    // Top Row: Name on Left + Call Timestamp on Right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (item.timestamp > 0L) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formatCallTime(item.timestamp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Bottom Row: [Call Type Icon] [SIM Badge] [Formatted Phone Number]
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.callType != null) {
                            CallTypeIcon(type = item.callType)
                            Spacer(modifier = Modifier.width(5.dp))
                        }

                        if (item.simSlot != null) {
                            InCallSimBadge(simNumber = item.simSlot)
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Text(
                            text = formatPhoneNumber(item.number),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallTypeIcon(type: CallType) {
    val (icon, tint) = when (type) {
        CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to IncomingGreen
        CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to OutgoingBlue
        CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed to MissedRed
        CallType.REJECTED -> Icons.Default.CallEnd to MissedRed
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(15.dp)
    )
}

private fun formatCallTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val date = Date(timestamp)
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
}

@Composable
private fun InCallSimBadge(simNumber: Int) {
    val simBgColor = if (simNumber == 2) SamsungGreen else SamsungSmsBlue

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 12.dp, height = 15.dp)
            .clip(com.example.test_dialer.ui.recents.components.SimCardShape(cutSizeDp = 3f))
            .background(simBgColor)
    ) {
        Text(
            text = "$simNumber",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            style = androidx.compose.ui.text.TextStyle(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                    includeFontPadding = false
                )
            ),
            modifier = Modifier.offset(y = (-0.5).dp)
        )
    }
}
