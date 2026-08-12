package com.example.test_dialer.ui.incall

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.test_dialer.service.CallManager
import com.example.test_dialer.ui.theme.MissedRed
import com.example.test_dialer.ui.theme.OneUIBgDark
import com.example.test_dialer.ui.theme.SamsungGreen
import com.example.test_dialer.ui.theme.SamsungSmsBlue
import com.example.test_dialer.util.formatPhoneNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.core.net.toUri
import kotlin.time.Duration.Companion.milliseconds

class SimCardShape(private val cutSizeDp: Float = 3f) : Shape {
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
fun InCallScreen(
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val activeCall by CallManager.currentCall.collectAsState()

    val isMuted by CallManager.isMuted.collectAsState()
    val audioRoute by CallManager.audioRoute.collectAsState()
    val isHold by CallManager.isHold.collectAsState()
    val isRecording by CallManager.isRecording.collectAsState()

    var callState by remember { mutableIntStateOf(activeCall?.state ?: Call.STATE_DISCONNECTED) }
    var durationSeconds by remember { mutableIntStateOf(0) }
    var activeSimCount by remember { mutableIntStateOf(1) }
    var showKeypadSheet by remember { mutableStateOf(false) }

    val handle = activeCall?.details?.handle
    val rawNumber = handle?.schemeSpecificPart ?: ""
    val displayName = activeCall?.details?.callerDisplayName ?: rawNumber

    var contactName by remember { mutableStateOf<String?>(null) }
    var contactPhotoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val simNumber = remember(activeCall) { getSimNumberFromCall(activeCall, context) }

    LaunchedEffect(rawNumber) {
        if (rawNumber.isNotBlank()) {
            withContext(Dispatchers.IO) {
                val result = lookupContactInfo(context, rawNumber)
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
                    val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
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
            onFinish()
            return@DisposableEffect onDispose {}
        }

        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                callState = state
                if (state == Call.STATE_DISCONNECTED) {
                    onFinish()
                }
            }
        }

        current.registerCallback(callback)
        callState = current.state

        onDispose {
            current.unregisterCallback(callback)
        }
    }

    // Timer for active call duration
    LaunchedEffect(callState) {
        if (callState == Call.STATE_ACTIVE) {
            while (true) {
                delay(1000L.milliseconds)
                durationSeconds++
            }
        }
    }

    // Auto-finish if no call
    LaunchedEffect(activeCall) {
        if (activeCall == null) {
            onFinish()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = OneUIBgDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Top Section: Avatar, Contact Name, Number, Status, SIM
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Avatar (170dp)
                Surface(
                    modifier = Modifier
                        .size(170.dp)
                        .clip(CircleShape),
                    shape = CircleShape,
                    color = Color(0xFF2B2D31)
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
                        } else {
                            val titleForInitial = contactName ?: (displayName.ifBlank { rawNumber })
                            val initial = titleForInitial.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
                            Text(
                                text = initial,
                                color = Color.White,
                                fontSize = 60.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                val formattedName = contactName ?: if (displayName.isBlank()) {
                    "Неизвестный номер"
                } else if (displayName == rawNumber) {
                    formatPhoneNumber(displayName)
                } else {
                    displayName
                }

                Text(
                    text = formattedName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )

                if (rawNumber.isNotBlank() && (contactName != null || displayName != rawNumber)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatPhoneNumber(rawNumber),
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Call Status Text & Timer
                val statusText = when {
                    isHold -> "На удержании"
                    callState == Call.STATE_RINGING -> "Входящий вызов..."
                    callState == Call.STATE_DIALING -> "Вызов..."
                    callState == Call.STATE_CONNECTING -> "Соединение..."
                    callState == Call.STATE_ACTIVE -> formatDuration(durationSeconds)
                    callState == Call.STATE_DISCONNECTING || callState == Call.STATE_DISCONNECTED -> "Завершение вызова..."
                    else -> "Соединение..."
                }

                Text(
                    text = statusText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHold) Color(0xFFFFB300) else SamsungGreen
                )

                if (activeSimCount > 1) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // SIM Indicator Chip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        InCallSimBadge(simNumber = simNumber)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "СИМ $simNumber",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Middle Section: Samsung One UI 3x2 Action Button Grid
            if (callState == Call.STATE_ACTIVE || callState == Call.STATE_DIALING || isHold) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
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

                        // 2. Add Call
                        InCallActionButton(
                            icon = Icons.Default.PersonAdd,
                            label = "Добавить",
                            isActive = false,
                            onClick = {
                                Toast.makeText(context, "Откройте приложение для второго звонка", Toast.LENGTH_SHORT).show()
                            }
                        )

                        // 3. Bluetooth
                        val isBluetoothActive = audioRoute == CallAudioState.ROUTE_BLUETOOTH
                        InCallActionButton(
                            icon = if (isBluetoothActive) Icons.Default.BluetoothAudio else Icons.Default.Bluetooth,
                            label = "Bluetooth",
                            isActive = isBluetoothActive,
                            activeColor = SamsungSmsBlue,
                            onClick = { CallManager.toggleBluetooth() }
                        )
                    }

                    // Row 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // 4. Speaker
                        val isSpeakerActive = audioRoute == CallAudioState.ROUTE_SPEAKER
                        InCallActionButton(
                            icon = if (isSpeakerActive) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            label = "Динамик",
                            isActive = isSpeakerActive,
                            activeColor = SamsungGreen,
                            onClick = { CallManager.toggleSpeaker() }
                        )

                        // 5. Mute
                        InCallActionButton(
                            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = if (isMuted) "Выкл. микр." else "Микрофон",
                            isActive = isMuted,
                            activeColor = MissedRed,
                            onClick = { CallManager.toggleMute() }
                        )

                        // 6. Keypad
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
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                if (callState == Call.STATE_RINGING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Decline Button (Red)
                        FloatingActionButton(
                            onClick = { CallManager.disconnect() },
                            containerColor = MissedRed,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Отклонить",
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Answer Button (Green)
                        FloatingActionButton(
                            onClick = { CallManager.answer() },
                            containerColor = SamsungGreen,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Ответить",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // End Call Button (Red)
                        FloatingActionButton(
                            onClick = { CallManager.disconnect() },
                            containerColor = MissedRed,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Завершить вызов",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }
        }
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
            .width(80.dp)
            .clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier.size(60.dp),
            shape = CircleShape,
            color = if (isActive) activeColor else Color.White.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OneUIBgDark,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Клавиатура тонального набора",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DTMF Dialpad Grid (3x4)
            val digits = listOf(
                listOf('1', '2', '3'),
                listOf('4', '5', '6'),
                listOf('7', '8', '9'),
                listOf('*', '0', '#')
            )

            digits.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { char ->
                        Surface(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onDigitClick(char)
                                },
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.15f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$char",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
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
private fun InCallSimBadge(simNumber: Int) {
    val simBgColor = if (simNumber == 2) SamsungGreen else SamsungSmsBlue

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 12.dp, height = 15.dp)
            .clip(SimCardShape(cutSizeDp = 3f))
            .background(simBgColor)
    ) {
        Text(
            text = "$simNumber",
            fontSize = 11.sp,
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
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
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
                        accountId.contains("sim${slotIndex + 1}", ignoreCase = true)) {
                        return slotIndex + 1
                    }
                }
            }
        }
    } catch (_: Exception) {
        // ignore
    }

    val cleanId = accountId.lowercase().trim()

    if (cleanId.contains("sim2") || cleanId.contains("slot1") || cleanId.contains("sub2") || cleanId.endsWith("_1") || cleanId.endsWith(":1")) {
        return 2
    }

    if (cleanId.contains("sim1") || cleanId.contains("slot0") || cleanId.contains("sub1") || cleanId.endsWith("_0") || cleanId.endsWith(":0")) {
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
    val photoUri: String?
)

private suspend fun lookupContactInfo(context: Context, phoneNumber: String): ContactLookupResult = withContext(Dispatchers.IO) {
    if (phoneNumber.isBlank()) return@withContext ContactLookupResult(null, null)
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
                val thumbPhotoIndex = c.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)

                if (nameIndex != -1) contactName = c.getString(nameIndex)
                if (fullPhotoIndex != -1) contactPhotoUri = c.getString(fullPhotoIndex)
                if (contactPhotoUri.isNullOrEmpty() && thumbPhotoIndex != -1) {
                    contactPhotoUri = c.getString(thumbPhotoIndex)
                }
            }
        }
        ContactLookupResult(contactName, contactPhotoUri)
    } catch (_: Exception) {
        ContactLookupResult(null, null)
    }
}
