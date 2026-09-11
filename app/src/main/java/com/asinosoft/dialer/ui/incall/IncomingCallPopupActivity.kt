package com.asinosoft.dialer.ui.incall

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.asinosoft.dialer.R
import com.asinosoft.dialer.data.model.CallState
import com.asinosoft.dialer.service.CallManager
import com.asinosoft.dialer.ui.components.SimIcon
import com.asinosoft.dialer.ui.recents.components.executeCustomSwipeAction
import com.asinosoft.dialer.ui.recents.components.getCustomSwipeAction
import com.asinosoft.dialer.ui.recents.components.getSwipeBackgroundVisuals
import com.asinosoft.dialer.ui.theme.DialerTheme
import com.asinosoft.dialer.ui.theme.IncomingGreen
import com.asinosoft.dialer.ui.theme.MissedRed
import com.asinosoft.dialer.ui.theme.OutgoingBlue
import com.asinosoft.dialer.ui.theme.SamsungGreen
import com.asinosoft.dialer.util.PhoneNumberHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class IncomingCallPopupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            window.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }

        // Set window to float at top of screen with 100% transparent status bar
        window.setGravity(Gravity.TOP)
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        window.addFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            DialerTheme {
                IncomingCallPopupScreen(
                    onAnswer = {
                        CallManager.answer()
                        // Don't open InCallActivity, stay in floating popup
                    },
                    onDecline = {
                        CallManager.disconnect()
                        finish()
                    },
                    onOpenFullScreen = {
                        val intent = Intent(this, InCallActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    @Suppress("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (silenceRingerOnIncomingKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(false)
                setTurnScreenOn(false)
            }
            @Suppress("DEPRECATION")
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        } catch (_: Exception) {
            // ignore
        }
    }
}

@Composable
private fun IncomingCallPopupScreen(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onOpenFullScreen: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activeCall by CallManager.currentCall.collectAsState()

    if (activeCall == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val call by remember(activeCall) { derivedStateOf { CallState.fromSystemCall(activeCall as Call, context) } }

    var contactName by remember { mutableStateOf<String?>(null) }
    var contactPhotoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(call) {
        if (call.rawNumber.isNotBlank()) {
            withContext(Dispatchers.IO) {
                val result = lookupContactInfo(context, call.rawNumber)
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

    var callState by remember { mutableStateOf(activeCall?.state ?: Call.STATE_DISCONNECTED) }
    var durationSeconds by remember { mutableStateOf(0) }
    var isDisconnected by remember { mutableStateOf(false) }

    val isMuted by CallManager.isMuted.collectAsState()
    val audioRoute by CallManager.audioRoute.collectAsState()
    val isSpeakerOn = audioRoute == CallAudioState.ROUTE_SPEAKER

    LaunchedEffect(callState) {
        if (callState == Call.STATE_ACTIVE && !isDisconnected) {
            while (true) {
                delay(1000L)
                durationSeconds++
            }
        }
    }

    DisposableEffect(activeCall) {
        val current = activeCall
        if (current == null) {
            isDisconnected = true
            return@DisposableEffect onDispose {}
        }

        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                callState = state
                if (state == Call.STATE_DISCONNECTED) {
                    isDisconnected = true
                }
            }
        }

        current.registerCallback(callback)
        callState = current.state
        if (current.state == Call.STATE_DISCONNECTED) {
            isDisconnected = true
        }

        onDispose {
            current.unregisterCallback(callback)
        }
    }

    LaunchedEffect(isDisconnected) {
        if (isDisconnected) {
            delay(3000L)
            onDismiss()
        }
    }

    val isDark = isSystemInDarkTheme()
    val finalName = contactName
        ?: if (call.displayName.isNotBlank() && call.displayName != call.rawNumber) {
            call.displayName
        } else {
            stringResource(R.string.incall_unknown_number)
        }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, start = 12.dp, end = 12.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenFullScreen() }
                .border(
                    BorderStroke(
                        1.dp,
                        if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
                    ),
                    RoundedCornerShape(28.dp)
                ),
            shape = RoundedCornerShape(28.dp),
            color = if (isDark) Color(0xFF222834) else Color(0xFFF2F0E8), // Samsung One UI Adaptive Surface
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Main Details Row: Avatar on Left + (SIM Header, Contact Name, Number) Column on Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Contact Photo Avatar Circle
                    Surface(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape),
                        shape = CircleShape,
                        color = if (isDark) Color(0xFF333B4A) else Color(0xFFD8D4C8)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (contactPhotoBitmap != null) {
                                Image(
                                    bitmap = contactPhotoBitmap!!,
                                    contentDescription = stringResource(R.string.incall_contact_photo_cd),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                val initial = finalName.trim().firstOrNull { it.isLetterOrDigit() }
                                    ?.uppercaseChar()?.toString() ?: "?"
                                Text(
                                    text = initial,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color.DarkGray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Right Column: SIM Badge Header, Contact Name, Phone Number
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SimIcon(simNumber = call.simNumber, size = 14.dp)
                            Spacer(modifier = Modifier.width(6.dp))

                            if (isDisconnected) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = null,
                                    tint = MissedRed,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formatDuration(durationSeconds),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MissedRed
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.incall_state_disconnected),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MissedRed
                                )
                            } else if (callState == Call.STATE_ACTIVE) {
                                val isIncomingCall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    activeCall?.details?.callDirection == Call.Details.DIRECTION_INCOMING
                                } else {
                                    true
                                }
                                Icon(
                                    imageVector = if (isIncomingCall) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade,
                                    contentDescription = null,
                                    tint = if (isIncomingCall) IncomingGreen else OutgoingBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formatDuration(durationSeconds),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.incall_state_ringing),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = finalName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = PhoneNumberHelper.format(call.rawNumber),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Action Bar: Answer/Decline OR In-Call (Speaker, Mic, End Call) OR Ended Call (Call Back)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isDisconnected) {
                        // After call disconnect: Only centered green "Вызов" button with configured right-swipe action
                        val contactKey = remember(call.rawNumber) { call.rawNumber }
                        val swipeRightAction = remember(contactKey, call.rawNumber, contactName) {
                            getCustomSwipeAction(
                                context,
                                contactKey,
                                isRight = true,
                                fallbackNumber = call.rawNumber,
                                contactName = contactName
                            )
                        }
                        val rightVisuals = remember(swipeRightAction) {
                            getSwipeBackgroundVisuals(
                                swipeRightAction,
                                defaultIsRight = true,
                                context = context
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        FloatingActionButton(
                            onClick = {
                                onDismiss()
                                if (swipeRightAction != null) {
                                    executeCustomSwipeAction(
                                        context = context,
                                        action = swipeRightAction,
                                        onCall = { num, sim ->
                                            startCallFromInCallScreen(context, num, sim)
                                        },
                                        onSms = { num ->
                                            try {
                                                val intent = Intent(Intent.ACTION_SENDTO, "smsto:${Uri.encode(num)}".toUri()).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                    )
                                } else {
                                    startCallFromInCallScreen(context, call.rawNumber, null)
                                }
                            },
                            containerColor = rightVisuals.backgroundColor,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = rightVisuals.icon,
                                contentDescription = rightVisuals.label,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    } else if (callState == Call.STATE_ACTIVE) {
                        // Active Call: Speaker button, Mic button, Red End Call button
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Speaker Button (size 48.dp)
                            FloatingActionButton(
                                onClick = { CallManager.toggleSpeaker() },
                                containerColor = if (isSpeakerOn) SamsungGreen else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSpeakerOn) Color.White else MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = stringResource(R.string.incall_action_speaker),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // 2. Mic Button (size 48.dp)
                            FloatingActionButton(
                                onClick = { CallManager.toggleMute() },
                                containerColor = if (isMuted) MissedRed else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isMuted) Color.White else MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = stringResource(R.string.incall_action_mic),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Red End Call Button
                        FloatingActionButton(
                            onClick = {
                                CallManager.disconnect()
                                isDisconnected = true
                            },
                            containerColor = MissedRed,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = stringResource(R.string.incall_end_cd),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        // Ringing state: Green Answer Button
                        val isBluetoothConnected = CallManager.isBluetoothConnected()
                        FloatingActionButton(
                            onClick = onAnswer,
                            containerColor = SamsungGreen,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = stringResource(R.string.incall_answer),
                                    modifier = Modifier.size(24.dp)
                                )
                                if (isBluetoothConnected) {
                                    Icon(
                                        imageVector = Icons.Default.Bluetooth,
                                        contentDescription = "Bluetooth",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(15.dp)
                                            .align(Alignment.Center)
                                            .offset(x = 8.dp, y = (-8).dp)
                                    )
                                }
                            }
                        }

                        // Send SMS Button
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.45f else 0.7f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    try {
                                        val smsIntent = Intent(
                                            Intent.ACTION_SENDTO,
                                            "smsto:${Uri.encode(call.rawNumber)}".toUri()
                                        ).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(smsIntent)
                                    } catch (_: Exception) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.error_open_messages),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.incall_send_message),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.incall_quick_sms_cd),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Red Decline Button
                        FloatingActionButton(
                            onClick = {
                                onDecline()
                                isDisconnected = true
                            },
                            containerColor = MissedRed,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = stringResource(R.string.incall_decline),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class PopupContactLookupResult(
    val name: String?,
    val photoUri: String?
)

private suspend fun lookupContactInfo(
    context: Context,
    phoneNumber: String
): PopupContactLookupResult = withContext(Dispatchers.IO) {
    if (phoneNumber.isBlank()) return@withContext PopupContactLookupResult(null, null)
    try {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_URI,
            ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
        )
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        var contactName: String? = null
        var contactPhotoUri: String? = null

        cursor?.use { c ->
            if (c.moveToFirst()) {
                val nameIndex = c.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                val fullPhotoIndex = c.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                val thumbPhotoIndex =
                    c.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)

                if (nameIndex != -1) contactName = c.getString(nameIndex)
                if (fullPhotoIndex != -1) contactPhotoUri = c.getString(fullPhotoIndex)
                if (contactPhotoUri.isNullOrEmpty() && thumbPhotoIndex != -1) {
                    contactPhotoUri = c.getString(thumbPhotoIndex)
                }
            }
        }
        PopupContactLookupResult(contactName, contactPhotoUri)
    } catch (_: Exception) {
        PopupContactLookupResult(null, null)
    }
}
