package com.asinosoft.dialer.ui.incall

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.asinosoft.dialer.MainActivity
import com.asinosoft.dialer.service.CallManager
import com.asinosoft.dialer.ui.components.OneUiPopupMenu
import com.asinosoft.dialer.ui.components.OneUiPopupMenuItem
import com.asinosoft.dialer.ui.components.SimIcon
import com.asinosoft.dialer.ui.recents.components.executeCustomSwipeAction
import com.asinosoft.dialer.ui.recents.components.getCustomSwipeAction
import com.asinosoft.dialer.ui.recents.components.getSwipeBackgroundVisuals
import com.asinosoft.dialer.ui.theme.MissedRed
import com.asinosoft.dialer.ui.theme.SamsungGreen
import com.asinosoft.dialer.ui.theme.SamsungSmsBlue
import com.asinosoft.dialer.util.PhoneNumberHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun InCallScreen(
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val allCalls by CallManager.calls.collectAsState()
    val activeCall by CallManager.currentCall.collectAsState()

    val isMuted by CallManager.isMuted.collectAsState()
    val audioRoute by CallManager.audioRoute.collectAsState()
    val isHold by CallManager.isHold.collectAsState()
    val isRecording by CallManager.isRecording.collectAsState()
    val bluetoothDevices by CallManager.bluetoothDevices.collectAsState()
    val currentBtName by CallManager.currentBluetoothDeviceName.collectAsState()

    var showBluetoothMenu by remember { mutableStateOf(false) }
    var showCallWaitingAnswerSheet by remember { mutableStateOf(false) }

    var callState by remember { mutableIntStateOf(activeCall?.state ?: Call.STATE_DISCONNECTED) }
    var durationSeconds by remember { mutableIntStateOf(0) }
    var activeSimCount by remember { mutableIntStateOf(1) }
    var showKeypadSheet by remember { mutableStateOf(false) }

    val handle = activeCall?.details?.handle
    val rawNumber = handle?.schemeSpecificPart ?: ""
    val displayName = activeCall?.details?.callerDisplayName ?: rawNumber

    var contactId by remember { mutableStateOf<String?>(null) }
    var contactName by remember { mutableStateOf<String?>(null) }
    var contactPhotoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val simNumber = remember(activeCall) { getSimNumberFromCall(activeCall, context) }

    val displayableCalls = remember(allCalls, activeCall) { CallManager.getDisplayableTopLevelCalls() }
    val incomingWaitingCall = displayableCalls.firstOrNull { it.state == Call.STATE_RINGING && it != activeCall }
    val isConference = remember(activeCall, allCalls) { CallManager.isConferenceCall(activeCall) || (displayableCalls.size == 1 && CallManager.isConferenceCall(displayableCalls.firstOrNull())) }

    val hasAnyActiveCall = displayableCalls.any { it.state == Call.STATE_ACTIVE || it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING || it.state == Call.STATE_HOLDING || CallManager.isConferenceCall(it) }
    val isCallDisconnected = displayableCalls.isEmpty() || !hasAnyActiveCall
    val isCallActive = hasAnyActiveCall && !isCallDisconnected

    LaunchedEffect(rawNumber, isConference) {
        if (isConference) {
            contactId = null
            contactName = "Конференция"
            contactPhotoBitmap = null
        } else if (rawNumber.isNotBlank()) {
            withContext(Dispatchers.IO) {
                val result = lookupContactInfo(context, rawNumber)
                contactId = result.contactId
                contactName = result.name

                if (!result.photoUri.isNullOrEmpty()) {
                    try {
                        val uri = result.photoUri.toUri()
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bitmap = BitmapFactory.decodeStream(stream)
                            contactPhotoBitmap = bitmap?.asImageBitmap()
                        }
                    } catch (_: Exception) {
                        contactPhotoBitmap = null
                    }
                } else {
                    contactPhotoBitmap = null
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    val sm =
                        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

                    @Suppress("MissingPermission")
                    val count = sm?.activeSubscriptionInfoCount ?: 1
                    activeSimCount = if (count > 1) count else 1
                } else {
                    activeSimCount = 1
                }
            } catch (_: Exception) {
                activeSimCount = 1
            }
        }
    }

    // Call state callback listener
    DisposableEffect(activeCall) {
        val current = activeCall
        if (current == null) {
            return@DisposableEffect onDispose {}
        }

        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                callState = state
            }
        }

        current.registerCallback(callback)
        callState = current.state

        onDispose {
            current.unregisterCallback(callback)
        }
    }

    // Auto-finish after 2 seconds on disconnect when all calls have ended
    LaunchedEffect(isCallDisconnected) {
        if (isCallDisconnected) {
            callState = Call.STATE_DISCONNECTED
            delay(2000L)
            onFinish()
        }
    }

    // Timer for active call duration
    LaunchedEffect(isCallActive) {
        if (isCallActive) {
            while (true) {
                delay(1000L.milliseconds)
                durationSeconds++
            }
        }
    }

    ProximityScreenOffEffect(callState = callState, audioRoute = audioRoute)

    val contactKey = remember(contactId, rawNumber) {
        contactId?.ifBlank { rawNumber } ?: rawNumber
    }
    val swipeRightAction = remember(contactKey, rawNumber, contactId) {
        if (rawNumber.isNotBlank() || !contactId.isNullOrBlank()) {
            getCustomSwipeAction(context, contactKey, isRight = true, fallbackNumber = rawNumber)
        } else null
    }
    val swipeLeftAction = remember(contactKey, rawNumber, contactId) {
        if (rawNumber.isNotBlank() || !contactId.isNullOrBlank()) {
            getCustomSwipeAction(context, contactKey, isRight = false, fallbackNumber = rawNumber)
        } else null
    }
    val rightVisuals = remember(swipeRightAction) {
        getSwipeBackgroundVisuals(swipeRightAction, defaultIsRight = true)
    }
    val leftVisuals = remember(swipeLeftAction) {
        getSwipeBackgroundVisuals(swipeLeftAction, defaultIsRight = false)
    }

    val formattedName = contactName ?: if (displayName.isBlank()) {
        "Неизвестный номер"
    } else if (displayName == rawNumber) {
        PhoneNumberHelper.format(displayName)
    } else {
        displayName
    }

    // Samsung One UI In-Call Screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF222834),
                        Color(0xFF151922),
                        Color(0xFF0C0E14)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Multiple Calls List OR Single Call Info
            if (displayableCalls.size >= 2 && incomingWaitingCall == null && !isCallDisconnected) {
                MultiCallCardsView(
                    calls = displayableCalls,
                    primaryCall = activeCall,
                    durationSeconds = durationSeconds,
                    context = context,
                    onSelectCall = { CallManager.selectPrimaryCall(it) },
                    onMergeCalls = { CallManager.mergeCalls() },
                    onDisconnectCall = { CallManager.disconnectCall(it) }
                )
            } else {
                // Top Section: Avatar, Contact Name, (SIM Icon + Number), Status badge
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Large One UI Circular Avatar (170dp)
                    Surface(
                        modifier = Modifier
                            .size(170.dp)
                            .clip(CircleShape)
                            .border(BorderStroke(2.5.dp, Color.White.copy(alpha = 0.18f)), CircleShape),
                        shape = CircleShape,
                        color = Color(0xFF282E3C),
                        shadowElevation = 14.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (contactPhotoBitmap != null) {
                                Image(
                                    bitmap = contactPhotoBitmap!!,
                                    contentDescription = "Фото контакта",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (isConference) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF2E6B4F), Color(0xFF1E4634))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    @Suppress("DEPRECATION")
                                    Icon(
                                        imageVector = Icons.Default.CallMerge,
                                        contentDescription = "Конференция",
                                        tint = Color.White,
                                        modifier = Modifier.size(72.dp)
                                    )
                                }
                            } else {
                                val titleForInitial = contactName ?: (displayName.ifBlank { rawNumber })
                                val initial =
                                    titleForInitial.trim().firstOrNull { it.isLetterOrDigit() }
                                        ?.uppercaseChar()?.toString() ?: "?"
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF3E4758), Color(0xFF262C38))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initial,
                                        color = Color.White,
                                        fontSize = 64.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = formattedName,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Phone number line with SIM icon in front (without "SIM" text)
                    if (rawNumber.isNotBlank() && !isConference) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (activeSimCount > 1) {
                                SimIcon(simNumber = simNumber, size = 15.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            val numberToDisplay = if (contactName != null || displayName != rawNumber) {
                                PhoneNumberHelper.format(rawNumber)
                            } else {
                                ""
                            }
                            if (numberToDisplay.isNotBlank()) {
                                Text(
                                    text = numberToDisplay,
                                    fontSize = 16.sp,
                                    color = Color.White.copy(alpha = 0.65f),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Call Status & Timer Badge
                    val statusText = when {
                        isCallDisconnected -> "Вызов завершен"
                        isHold -> "На удержании"
                        callState == Call.STATE_RINGING -> "Входящий вызов"
                        callState == Call.STATE_DIALING -> "Вызов..."
                        callState == Call.STATE_CONNECTING -> "Соединение..."
                        isCallActive -> formatDuration(durationSeconds)
                        else -> "Вызов завершен"
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = when {
                            isCallDisconnected -> Color.White.copy(alpha = 0.12f)
                            isHold -> Color(0xFFFFB300).copy(alpha = 0.22f)
                            isCallActive -> Color.White.copy(alpha = 0.12f)
                            else -> SamsungGreen.copy(alpha = 0.18f)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            if (isCallActive) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(SamsungGreen)
                                )
                            }
                            Text(
                                text = statusText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    isCallDisconnected -> Color.White.copy(alpha = 0.85f)
                                    isHold -> Color(0xFFFFC107)
                                    isCallActive -> Color.White
                                    else -> SamsungGreen
                                }
                            )
                        }
                    }

                    // 3 Quick Action Buttons placed directly under "Вызов завершен"
                    if (isCallDisconnected) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Button 1: Call (Swipe Right Action)
                            InCallPostCallActionButton(
                                icon = rightVisuals.icon,
                                label = rightVisuals.label,
                                containerColor = rightVisuals.backgroundColor,
                                onClick = {
                                    onFinish()
                                    if (swipeRightAction != null) {
                                        executeCustomSwipeAction(
                                            context = context,
                                            action = swipeRightAction,
                                            onCall = { num, sim -> startCallFromInCallScreen(context, num, sim) },
                                            onSms = { num -> startSmsFromInCallScreen(context, num) }
                                        )
                                    } else {
                                        startCallFromInCallScreen(context, rawNumber, null)
                                    }
                                }
                            )

                            // Button 2: Message/Messenger (Swipe Left Action - Blue)
                            InCallPostCallActionButton(
                                icon = leftVisuals.icon,
                                label = leftVisuals.label,
                                containerColor = leftVisuals.backgroundColor,
                                onClick = {
                                    onFinish()
                                    if (swipeLeftAction != null) {
                                        executeCustomSwipeAction(
                                            context = context,
                                            action = swipeLeftAction,
                                            onCall = { num, sim -> startCallFromInCallScreen(context, num, sim) },
                                            onSms = { num -> startSmsFromInCallScreen(context, num) }
                                        )
                                    } else {
                                        startSmsFromInCallScreen(context, rawNumber)
                                    }
                                }
                            )

                            // Button 3: Info -> opens ContactDetailDialog on Contact Tab (tab 0)
                            InCallPostCallActionButton(
                                icon = Icons.Default.Person,
                                label = "Инфо",
                                containerColor = Color.White.copy(alpha = 0.15f),
                                onClick = {
                                    onFinish()
                                    openContactInApp(context, rawNumber, contactName, contactId)
                                }
                            )
                        }
                    }
                }
            }

            // Middle Section: Samsung One UI 3x2 Action Button Grid
            if ((callState == Call.STATE_ACTIVE || callState == Call.STATE_DIALING || isHold) && !isCallDisconnected) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Row 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // 1. Record
                        InCallActionButton(
                            icon = if (isRecording) Icons.Default.Pause else Icons.Default.PlayArrow,
                            label = if (isRecording) "Запись..." else "Запись",
                            isActive = isRecording,
                            activeColor = MissedRed,
                            onClick = { CallManager.toggleRecord() }
                        )

                        // 2. Add Call Button (replaces Hold button)
                        InCallActionButton(
                            icon = Icons.Default.Add,
                            label = "Добавить",
                            isActive = false,
                            onClick = { CallManager.openNewCallScreen(context) }
                        )

                        // 3. Bluetooth
                        val isBluetoothActive = audioRoute == CallAudioState.ROUTE_BLUETOOTH
                        val rawBtName = if (isBluetoothActive && !currentBtName.isNullOrBlank()) {
                            currentBtName!!
                        } else {
                            "Bluetooth"
                        }
                        val isMacAddress = rawBtName.matches(Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")) ||
                                rawBtName.matches(Regex(".*([0-9A-Fa-f]{2}:){3,}.*")) ||
                                rawBtName.startsWith("bt_", ignoreCase = true)

                        val btDeviceName = if (isMacAddress) {
                            bluetoothDevices.firstOrNull { it.isCurrent && !it.name.contains(":") }?.name
                                ?: bluetoothDevices.firstOrNull { !it.name.contains(":") }?.name
                                ?: "Bluetooth"
                        } else {
                            rawBtName
                        }
                        val hasMultipleBtDevices = bluetoothDevices.size > 1

                        Box(contentAlignment = Alignment.Center) {
                            InCallActionButton(
                                icon = if (isBluetoothActive) Icons.Default.BluetoothAudio else Icons.Default.Bluetooth,
                                label = if (hasMultipleBtDevices) "$btDeviceName ›" else btDeviceName,
                                isActive = isBluetoothActive,
                                activeColor = SamsungSmsBlue,
                                onClick = {
                                    if (hasMultipleBtDevices) {
                                        showBluetoothMenu = true
                                    } else {
                                        CallManager.toggleBluetooth()
                                    }
                                }
                            )

                            if (hasMultipleBtDevices) {
                                OneUiPopupMenu(
                                    expanded = showBluetoothMenu,
                                    onDismissRequest = { showBluetoothMenu = false }
                                ) {
                                    bluetoothDevices.forEach { device ->
                                        val isSelected = device.isCurrent || (isBluetoothActive && device.name == currentBtName)
                                        OneUiPopupMenuItem(
                                            icon = if (isSelected) Icons.Default.Check else Icons.Default.Bluetooth,
                                            label = device.name,
                                            labelColor = if (isSelected) SamsungGreen else MaterialTheme.colorScheme.onSurface,
                                            iconTint = if (isSelected) SamsungGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            iconBackground = if (isSelected) SamsungGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                                            onClick = {
                                                showBluetoothMenu = false
                                                CallManager.selectBluetoothDevice(device)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Row 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // 4. Speaker
                        val isSpeakerActive = audioRoute == CallAudioState.ROUTE_SPEAKER
                        InCallActionButton(
                            icon = if (isSpeakerActive) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            label = "Динамик",
                            isActive = isSpeakerActive,
                            activeColor = SamsungGreen,
                            onClick = { CallManager.toggleSpeaker() }
                        )

                        // 5. Mute Microphone
                        InCallActionButton(
                            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = if (isMuted) "Выкл. микр." else "Микрофон",
                            isActive = isMuted,
                            activeColor = MissedRed,
                            onClick = { CallManager.toggleMute() }
                        )

                        // 6. DTMF Keypad
                        InCallActionButton(
                            icon = Icons.Default.Dialpad,
                            label = "Клавиатура",
                            isActive = showKeypadSheet,
                            activeColor = SamsungGreen,
                            onClick = { showKeypadSheet = true }
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(100.dp))
            }

            // Bottom Section: Answer / Decline / End Call Buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.height(if (isCallDisconnected) 0.dp else 76.dp)
            ) {
                when {
                    isCallDisconnected -> {
                        // Handled above directly under "Вызов завершен"
                    }
                    incomingWaitingCall != null -> {
                        // Call Waiting: Second incoming call banner with swipe controls
                        SamsungSwipeAnswerDeclineRow(
                            onAnswer = { showCallWaitingAnswerSheet = true },
                            onDecline = { incomingWaitingCall.disconnect() }
                        )
                    }
                    callState == Call.STATE_RINGING -> {
                        SamsungSwipeAnswerDeclineRow(
                            onAnswer = { CallManager.answer() },
                            onDecline = { CallManager.disconnect() }
                        )
                    }
                    else -> {
                        // Active Call: Centered End Call Button (Red) without "Завершить" text
                        FloatingActionButton(
                            onClick = { CallManager.disconnect() },
                            containerColor = MissedRed,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(76.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Завершить вызов",
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Call Waiting Answer Bottom Sheet
    if (showCallWaitingAnswerSheet && incomingWaitingCall != null) {
        CallWaitingAnswerBottomSheet(
            activeContactName = formattedName,
            onHoldAndAnswer = {
                CallManager.holdAndAnswer(incomingWaitingCall)
                showCallWaitingAnswerSheet = false
            },
            onEndAndAnswer = {
                CallManager.endAndAnswer(incomingWaitingCall)
                showCallWaitingAnswerSheet = false
            },
            onDismiss = { showCallWaitingAnswerSheet = false }
        )
    }

    // DTMF Dialpad Bottom Sheet Overlay
    if (showKeypadSheet) {
        InCallKeypadSheet(
            onDigitClick = { digit ->
                CallManager.playDtmf(digit)
            },
            onDismiss = { showKeypadSheet = false }
        )
    }
}

/**
 * Multi-call list showing active call (bright) and held call (dimmed) with conference merge button.
 */
@Composable
private fun MultiCallCardsView(
    calls: List<Call>,
    primaryCall: Call?,
    durationSeconds: Int,
    context: Context,
    onSelectCall: (Call) -> Unit,
    onMergeCalls: () -> Unit,
    onDisconnectCall: (Call) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        calls.forEach { call ->
            val isCurrent = (call == primaryCall) || (call.state == Call.STATE_ACTIVE)
            val isHeld = call.state == Call.STATE_HOLDING
            val rawNum = call.details?.handle?.schemeSpecificPart.orEmpty()
            var cName by remember(rawNum) { mutableStateOf<String?>(null) }

            LaunchedEffect(rawNum) {
                if (rawNum.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        cName = lookupContactInfo(context, rawNum).name
                    }
                }
            }

            val isConf = CallManager.isConferenceCall(call)
            val title = if (isConf) {
                "Конференция"
            } else {
                cName ?: if (rawNum.isNotBlank()) PhoneNumberHelper.format(rawNum) else "Неизвестный"
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isCurrent) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.07f),
                tonalElevation = if (isCurrent) 4.dp else 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSelectCall(call) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (isCurrent) SamsungGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isConf) Icons.Default.Person else if (isCurrent) Icons.Default.Call else Icons.Default.Pause,
                                contentDescription = null,
                                tint = if (isCurrent) SamsungGreen else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = title,
                                fontSize = 17.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    isHeld -> "На удержании"
                                    isCurrent -> formatDuration(durationSeconds)
                                    call.state == Call.STATE_DIALING -> "Вызов..."
                                    else -> "Соединение..."
                                },
                                fontSize = 13.sp,
                                color = if (isHeld) Color(0xFFFFC107) else if (isCurrent) SamsungGreen else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }

                    // Disconnect specific call icon
                    IconButton(
                        onClick = { onDisconnectCall(call) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Завершить",
                            tint = MissedRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Merge Conference Button (only when at least 2 top level calls can be merged)
        if (calls.size >= 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onMergeCalls() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        @Suppress("DEPRECATION")
                        Icon(
                            imageVector = Icons.Default.CallMerge,
                            contentDescription = "Объединить",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Объединить",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Call waiting confirmation bottom sheet: Hold vs End current call.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallWaitingAnswerBottomSheet(
    activeContactName: String,
    onHoldAndAnswer: () -> Unit,
    onEndAndAnswer: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF222834),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "Ответить на вызов и:",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Option 1: Hold current call
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onHoldAndAnswer()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFB300).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Text(
                        text = "Поместить «$activeContactName» на удержание",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Option 2: End current call
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onEndAndAnswer()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MissedRed.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = null,
                            tint = MissedRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Text(
                        text = "Завершить вызов с «$activeContactName»",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Samsung One UI Swipe to Answer (Green, swipes Right) / Swipe to Decline (Red, swipes Left)
 */
@Composable
private fun SamsungSwipeAnswerDeclineRow(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val answerOffsetX = remember { Animatable(0f) }
    val declineOffsetX = remember { Animatable(0f) }

    val thresholdPx = with(density) { 80.dp.toPx() }

    var hasTriggered by remember { mutableStateOf(false) }

    // Pulsing chevrons guide animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Answer Button (Green) - Swipes RIGHT
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Guided pulsing chevrons pointing right >>>
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(start = 78.dp)
                ) {
                    repeat(3) { i ->
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = SamsungGreen.copy(alpha = (pulseAlpha - i * 0.2f).coerceIn(0.12f, 1f)),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Green Draggable Answer Button
                Surface(
                    shape = CircleShape,
                    color = SamsungGreen,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .offset { IntOffset(answerOffsetX.value.roundToInt(), 0) }
                        .size(74.dp)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    coroutineScope.launch {
                                        if (answerOffsetX.value >= thresholdPx && !hasTriggered) {
                                            hasTriggered = true
                                            performSwipeActionVibration(context)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onAnswer()
                                        } else {
                                            answerOffsetX.animateTo(0f, spring())
                                        }
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        answerOffsetX.animateTo(0f, spring())
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    val newOffset = (answerOffsetX.value + dragAmount).coerceIn(0f, thresholdPx)
                                    coroutineScope.launch {
                                        answerOffsetX.snapTo(newOffset)
                                        if (newOffset >= thresholdPx && !hasTriggered) {
                                            hasTriggered = true
                                            performSwipeActionVibration(context)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onAnswer()
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Ответить",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ответить",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Right: Decline Button (Red) - Swipes LEFT
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Guided pulsing chevrons pointing left <<<
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(end = 78.dp)
                ) {
                    repeat(3) { i ->
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = null,
                            tint = MissedRed.copy(alpha = (pulseAlpha - i * 0.2f).coerceIn(0.12f, 1f)),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Red Draggable Decline Button
                Surface(
                    shape = CircleShape,
                    color = MissedRed,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .offset { IntOffset(declineOffsetX.value.roundToInt(), 0) }
                        .size(74.dp)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    coroutineScope.launch {
                                        if (declineOffsetX.value <= -thresholdPx && !hasTriggered) {
                                            hasTriggered = true
                                            performSwipeActionVibration(context)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDecline()
                                        } else {
                                            declineOffsetX.animateTo(0f, spring())
                                        }
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        declineOffsetX.animateTo(0f, spring())
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    val newOffset = (declineOffsetX.value + dragAmount).coerceIn(-thresholdPx, 0f)
                                    coroutineScope.launch {
                                        declineOffsetX.snapTo(newOffset)
                                        if (newOffset <= -thresholdPx && !hasTriggered) {
                                            hasTriggered = true
                                            performSwipeActionVibration(context)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDecline()
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Отклонить",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Отклонить",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun InCallActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    activeColor: Color = SamsungGreen,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = if (isActive) activeColor else Color.White.copy(alpha = 0.12f),
            shadowElevation = if (isActive) 6.dp else 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isActive) activeColor else Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InCallKeypadSheet(
    onDigitClick: (Char) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var dialedDigits by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E232E),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: Digits Text & Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dialedDigits.ifEmpty { "Клавиатура" },
                        fontSize = if (dialedDigits.isNotEmpty()) 24.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (dialedDigits.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { dialedDigits = dialedDigits.dropLast(1) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Удалить",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DTMF Dialpad Grid (3x4) with T9 Subletters
            val dtmfButtons = remember {
                listOf(
                    Pair("1", ""),
                    Pair("2", "ABC"),
                    Pair("3", "DEF"),
                    Pair("4", "GHI"),
                    Pair("5", "JKL"),
                    Pair("6", "MNO"),
                    Pair("7", "PQRS"),
                    Pair("8", "TUV"),
                    Pair("9", "WXYZ"),
                    Pair("*", ""),
                    Pair("0", "+"),
                    Pair("#", "")
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (row in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0 until 3) {
                            val (digit, letters) = dtmfButtons[row * 3 + col]
                            Surface(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        val char = digit.firstOrNull() ?: ' '
                                        onDigitClick(char)
                                        dialedDigits += digit
                                    },
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.12f)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = digit,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (letters.isNotEmpty()) {
                                        Text(
                                            text = letters,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = Color.White.copy(alpha = 0.5f),
                                            lineHeight = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getSimNumberFromCall(call: Call?, context: Context): Int {
    if (call == null) return 1
    val details = call.details ?: return 1
    val accountHandle = details.accountHandle ?: return 1
    val accountId = accountHandle.id ?: return 1

    try {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val subManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

            @Suppress("MissingPermission")
            val activeList = subManager?.activeSubscriptionInfoList

            if (!activeList.isNullOrEmpty()) {
                if (activeList.size == 1) {
                    return activeList[0].simSlotIndex + 1
                }

                for (info in activeList) {
                    val subId = info.subscriptionId.toString()
                    val slotIndex = info.simSlotIndex // 0 for SIM1, 1 for SIM2
                    val iccId = info.iccId.orEmpty()

                    if (accountId == subId || accountId == "sub_$subId") {
                        return slotIndex + 1
                    }

                    if (iccId.isNotBlank() && accountId.contains(iccId)) {
                        return slotIndex + 1
                    }

                    if (accountId == slotIndex.toString() ||
                        accountId.endsWith(":$slotIndex") ||
                        accountId.endsWith("_$slotIndex") ||
                        accountId.contains("slot$slotIndex", ignoreCase = true) ||
                        accountId.contains("sim${slotIndex + 1}", ignoreCase = true)
                    ) {
                        return slotIndex + 1
                    }
                }
            }
        }
    } catch (_: Exception) {
        // ignore
    }

    val cleanId = accountId.lowercase().trim()

    if (cleanId.contains("sim2") || cleanId.contains("slot1") || cleanId.contains("sub2") || cleanId.endsWith(
            "_1"
        ) || cleanId.endsWith(":1")
    ) {
        return 2
    }

    if (cleanId.contains("sim1") || cleanId.contains("slot0") || cleanId.contains("sub1") || cleanId.endsWith(
            "_0"
        ) || cleanId.endsWith(":0")
    ) {
        return 1
    }

    return 1
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", m, s)
}

private data class ContactLookupResult(
    val name: String?,
    val photoUri: String?,
    val contactId: String? = null
)

private suspend fun lookupContactInfo(context: Context, phoneNumber: String): ContactLookupResult =
    withContext(Dispatchers.IO) {
        if (phoneNumber.isBlank()) return@withContext ContactLookupResult(null, null, null)
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_URI,
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
            )
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            var contactId: String? = null
            var contactName: String? = null
            var contactPhotoUri: String? = null

            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val idIndex = c.getColumnIndex(ContactsContract.PhoneLookup._ID)
                    val nameIndex = c.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val fullPhotoIndex = c.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                    val thumbPhotoIndex =
                        c.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)

                    if (idIndex != -1) contactId = c.getString(idIndex)
                    if (nameIndex != -1) contactName = c.getString(nameIndex)
                    if (fullPhotoIndex != -1) contactPhotoUri = c.getString(fullPhotoIndex)
                    if (contactPhotoUri.isNullOrEmpty() && thumbPhotoIndex != -1) {
                        contactPhotoUri = c.getString(thumbPhotoIndex)
                    }
                }
            }
            ContactLookupResult(contactName, contactPhotoUri, contactId)
        } catch (_: Exception) {
            ContactLookupResult(null, null, null)
        }
    }

private fun performSwipeActionVibration(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val vibrator = vibratorManager?.defaultVibrator
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(55L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            @Suppress("DEPRECATION")
            vibrator?.vibrate(55L)
        }
    } catch (_: Exception) {
        // ignore
    }
}

@Composable
private fun InCallPostCallActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Surface(
            modifier = Modifier.size(60.dp),
            shape = CircleShape,
            color = containerColor,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun startCallFromInCallScreen(context: Context, number: String, simSlot: Int? = null) {
    try {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val uri = Uri.fromParts("tel", number, null)
        val extras = Bundle().apply {
            if (simSlot != null) {
                val accountHandle = getPhoneAccountHandleForSimSlot(context, simSlot)
                if (accountHandle != null) {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle)
                }
            }
        }
        if (telecomManager != null && ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            telecomManager.placeCall(uri, extras)
        } else {
            val intent = Intent(Intent.ACTION_CALL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    } catch (_: Exception) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}

private fun startSmsFromInCallScreen(context: Context, number: String) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO, "smsto:${Uri.encode(number)}".toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        // ignore
    }
}

private fun openSystemContactFromInCallScreen(context: Context, contactNumber: String) {
    try {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(contactNumber)
        )
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CONTACTS)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Не удалось открыть информацию о контакте", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun openContactInApp(context: Context, number: String, name: String?, contactId: String?) {
    try {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_CONTACT_NUMBER, number)
            if (!name.isNullOrBlank()) putExtra(MainActivity.EXTRA_OPEN_CONTACT_NAME, name)
            if (!contactId.isNullOrBlank()) putExtra(MainActivity.EXTRA_OPEN_CONTACT_ID, contactId)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        openSystemContactFromInCallScreen(context, number)
    }
}

private fun getPhoneAccountHandleForSimSlot(context: Context, simSlot: Int): PhoneAccountHandle? {
    try {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return null
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null

        val phoneAccountHandles = telecomManager.callCapablePhoneAccounts
        if (!phoneAccountHandles.isNullOrEmpty()) {
            val targetSlotIndex = simSlot - 1
            return phoneAccountHandles.getOrNull(targetSlotIndex) ?: phoneAccountHandles.firstOrNull()
        }
    } catch (_: Exception) {
        // ignore
    }
    return null
}
