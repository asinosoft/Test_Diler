package com.asinosoft.dialer.ui.incall

import android.content.Context
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Turns the screen off when the phone is held to the ear during an in-call
 * earpiece session (standard dialer proximity behavior).
 */
@Composable
fun ProximityScreenOffEffect(callState: Int, audioRoute: Int) {
    val context = LocalContext.current
    val enabled = shouldUseProximity(callState, audioRoute)

    DisposableEffect(enabled) {
        val wakeLock = if (enabled) acquireProximityWakeLock(context) else null
        onDispose {
            releaseProximityWakeLock(wakeLock)
        }
    }
}

private fun shouldUseProximity(callState: Int, audioRoute: Int): Boolean {
    val inCall = callState == Call.STATE_ACTIVE ||
            callState == Call.STATE_DIALING ||
            callState == Call.STATE_CONNECTING
    if (!inCall) return false

    return when (audioRoute) {
        CallAudioState.ROUTE_SPEAKER,
        CallAudioState.ROUTE_BLUETOOTH -> false
        else -> true
    }
}

@Suppress("DEPRECATION")
private fun acquireProximityWakeLock(context: Context): PowerManager.WakeLock? {
    return try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            return null
        }
        powerManager.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "dialer:proximity"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    } catch (_: Exception) {
        null
    }
}

private fun releaseProximityWakeLock(wakeLock: PowerManager.WakeLock?) {
    try {
        if (wakeLock?.isHeld == true) {
            wakeLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
    } catch (_: Exception) {
        try {
            if (wakeLock?.isHeld == true) wakeLock.release()
        } catch (_: Exception) {
            // ignore
        }
    }
}
