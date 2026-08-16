package com.asinosoft.dialer.service

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import com.asinosoft.dialer.ui.incall.InCallActivity
import com.asinosoft.dialer.ui.incall.IncomingCallPopupActivity

class CallService : InCallService() {
    lateinit var notification: NotificationManager

    override fun onCreate() {
        super.onCreate()
        CallManager.inCallService = this
        notification = NotificationManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            NotificationManager.ACTION_ANSWER -> CallManager.answer()
            NotificationManager.ACTION_DISCONNECT -> CallManager.disconnect()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.setCall(call)

        var wasRinging = (call.state == Call.STATE_RINGING)
        var wasAnswered = (call.state == Call.STATE_ACTIVE)

        val handle = call.details?.handle
        val rawNumber = handle?.schemeSpecificPart ?: ""

        val showPopup = (call.state == Call.STATE_RINGING) && shouldShowFloatingPopup(this)
        val activityClass = if (showPopup) {
            IncomingCallPopupActivity::class.java
        } else {
            InCallActivity::class.java
        }

        val intent = Intent(this, activityClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)

        notification.showCallNotification(CallState.fromSystemCall(call))

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_RINGING) {
                    wasRinging = true
                } else if (state == Call.STATE_ACTIVE) {
                    wasAnswered = true
                }

                if (state == Call.STATE_DISCONNECTED) {
                    stopForeground(true)
                    if (wasRinging && !wasAnswered && rawNumber.isNotBlank()) {
                        notification.showMissedCallNotification(rawNumber)
                    }
                } else {
                    notification.showCallNotification(CallState.fromSystemCall(call))
                }
            }
        })
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (CallManager.currentCall.value == call) {
            CallManager.setCall(null)
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (CallManager.inCallService == this) {
            CallManager.inCallService = null
        }
    }

    private fun shouldShowFloatingPopup(context: Context): Boolean {
        try {
            val keyguardManager = context.getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
            if (keyguardManager != null && keyguardManager.isKeyguardLocked) {
                return false // Screen is locked -> Full Screen InCallActivity
            }
            return true // Screen is unlocked -> Floating Call Pop-Up
        } catch (_: Exception) {
            return true
        }
    }
}
