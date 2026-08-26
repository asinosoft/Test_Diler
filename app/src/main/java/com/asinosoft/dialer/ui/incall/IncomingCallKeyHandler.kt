package com.asinosoft.dialer.ui.incall

import android.telecom.Call
import android.view.KeyEvent
import com.asinosoft.dialer.service.CallManager

/**
 * Volume / power while ringing → silence ringtone (standard dialer behavior).
 * @return true if the key was consumed
 */
fun silenceRingerOnIncomingKey(event: KeyEvent): Boolean {
    if (event.action != KeyEvent.ACTION_DOWN) return false
    if (event.repeatCount != 0) return false

    val isSilenceKey = when (event.keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_POWER -> true
        else -> false
    }
    if (!isSilenceKey) return false

    val call = CallManager.currentCall.value ?: return false
    if (call.state != Call.STATE_RINGING) return false

    CallManager.silenceRinger()
    // Consume volume keys so system volume is not changed; power is usually not delivered here.
    return event.keyCode != KeyEvent.KEYCODE_POWER
}
