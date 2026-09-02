package com.asinosoft.dialer.ui.incall

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.Call
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.asinosoft.dialer.data.model.CallState
import com.asinosoft.dialer.service.CallManager
import com.asinosoft.dialer.ui.components.SimIcon
import com.asinosoft.dialer.ui.theme.DialerTheme
import com.asinosoft.dialer.ui.theme.MissedRed
import com.asinosoft.dialer.ui.theme.SamsungGreen
import com.asinosoft.dialer.util.PhoneNumberHelper
import kotlinx.coroutines.Dispatchers
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
                        val intent = Intent(this, InCallActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                        finish()
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

    DisposableEffect(activeCall) {
        val current = activeCall
        if (current == null) {
            onDismiss()
            return@DisposableEffect onDispose {}
        }

        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                when (state) {
                    // Answered from BT / notification / system — promote to full in-call UI
                    Call.STATE_ACTIVE,
                    Call.STATE_HOLDING -> onOpenFullScreen()
                    Call.STATE_DISCONNECTED -> onDismiss()
                }
            }
        }

        current.registerCallback(callback)

        onDispose {
            current.unregisterCallback(callback)
        }
    }

    val finalName = contactName
        ?: if (call.displayName.isNotBlank() && call.displayName != call.rawNumber) call.displayName else "Неизвестный номер"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, start = 12.dp, end = 12.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenFullScreen() },
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFF2F0E8), // Light Samsung One UI Card Surface
            tonalElevation = 8.dp,
            shadowElevation = 10.dp
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
                        color = Color(0xFFD8D4C8)
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
                                val initial = finalName.trim().firstOrNull { it.isLetterOrDigit() }
                                    ?.uppercaseChar()?.toString() ?: "?"
                                Text(
                                    text = initial,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.DarkGray
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
                            Text(
                                text = "Входящие вызовы",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black.copy(alpha = 0.65f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = finalName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = PhoneNumberHelper.format(call.rawNumber),
                            fontSize = 15.sp,
                            color = Color.Black.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Action Bar: Green Answer | "Отправить сообщение" | Red Decline
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Green Answer Button
                    FloatingActionButton(
                        onClick = onAnswer,
                        containerColor = SamsungGreen,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Ответить",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Send SMS Button
                    Row(
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
                                        "Не удалось открыть отправку сообщений",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Отправить сообщение",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Быстрый ответ SMS",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Red Decline Button
                    FloatingActionButton(
                        onClick = onDecline,
                        containerColor = MissedRed,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Отклонить",
                            modifier = Modifier.size(24.dp)
                        )
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
