package com.example.test_dialer.ui.incall

import android.telecom.Call
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_dialer.service.CallManager
import com.example.test_dialer.ui.theme.MissedRed
import com.example.test_dialer.ui.theme.OneUIBgDark
import com.example.test_dialer.ui.theme.SamsungGreen
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun InCallScreen(
    onFinish: () -> Unit
) {
    val activeCall by CallManager.currentCall.collectAsState()

    var callState by remember { mutableIntStateOf(activeCall?.state ?: Call.STATE_DISCONNECTED) }
    var durationSeconds by remember { mutableIntStateOf(0) }

    val handle = activeCall?.details?.handle
    val rawNumber = handle?.schemeSpecificPart ?: ""
    val displayName = activeCall?.details?.callerDisplayName ?: rawNumber

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
                delay(1000L)
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Top Section: Avatar & Caller Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2B2D31)),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                    Text(
                        text = initial,
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = if (displayName.isBlank()) "Неизвестный номер" else displayName,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                if (rawNumber.isNotBlank() && rawNumber != displayName) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = rawNumber,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Call Status Text
                val statusText = when (callState) {
                    Call.STATE_RINGING -> "Входящий вызов..."
                    Call.STATE_DIALING -> "Вызов..."
                    Call.STATE_CONNECTING -> "Соединение..."
                    Call.STATE_ACTIVE -> formatDuration(durationSeconds)
                    Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> "Завершение вызова..."
                    else -> "Соединение..."
                }

                Text(
                    text = statusText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = SamsungGreen
                )
            }

            // Bottom Section: Action Buttons (Answer / End Call)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 32.dp)
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
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", m, s)
}
