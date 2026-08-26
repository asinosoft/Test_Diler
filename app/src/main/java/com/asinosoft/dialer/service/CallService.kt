package com.asinosoft.dialer.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.OutcomeReceiver
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService
import androidx.annotation.RequiresApi
import com.asinosoft.dialer.data.model.CallState
import com.asinosoft.dialer.ui.incall.InCallActivity
import com.asinosoft.dialer.ui.incall.IncomingCallPopupActivity
import java.util.concurrent.Executors

class CallService : InCallService() {
    lateinit var notification: NotificationManager
    private val ringtonePlayer by lazy { CallRingtonePlayer(this) }
    private var silenceReceiverRegistered = false
    private var availableEndpoints: List<CallEndpoint> = emptyList()
    private val endpointExecutor by lazy { Executors.newSingleThreadExecutor() }

    private val silenceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> silenceIncomingRinger()
                VOLUME_CHANGED_ACTION -> {
                    val stream = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                    if (stream == AudioManager.STREAM_RING ||
                        stream == AudioManager.STREAM_MUSIC ||
                        stream == AudioManager.STREAM_SYSTEM
                    ) {
                        silenceIncomingRinger()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        CallManager.inCallService = this
        notification = NotificationManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            NotificationManager.ACTION_ANSWER -> {
                CallManager.answer()
                promoteToFullInCallUi()
            }
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

        if (call.state == Call.STATE_RINGING) {
            ringtonePlayer.start()
            registerSilenceReceiver()
        }

        notification.showCallNotification(CallState.fromSystemCall(call, this))

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_RINGING) {
                    wasRinging = true
                    ringtonePlayer.start()
                    registerSilenceReceiver()
                } else if (state == Call.STATE_ACTIVE) {
                    wasAnswered = true
                    ringtonePlayer.stop()
                    unregisterSilenceReceiver()
                    // BT headset / external answer while floating popup is showing
                    promoteToFullInCallUi()
                } else {
                    unregisterSilenceReceiver()
                }

                if (state == Call.STATE_DISCONNECTED) {
                    ringtonePlayer.stop()
                    unregisterSilenceReceiver()
                    stopForeground(true)
                    if (wasRinging && !wasAnswered && rawNumber.isNotBlank()) {
                        // Сразу гасим системный баннер — он часто появляется до нашего notify
                        notification.suppressSystemMissedCallNotification()
                        notification.showMissedCallNotification(rawNumber)
                    }
                } else {
                    notification.showCallNotification(CallState.fromSystemCall(call, this@CallService))
                }
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        CallManager.updateAudioRoute(endpointTypeToRoute(callEndpoint.endpointType))
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) {
        this.availableEndpoints = availableEndpoints
    }

    override fun onSilenceRinger() {
        silenceIncomingRinger()
    }

    fun silenceIncomingRinger() {
        ringtonePlayer.silence()
    }

    fun requestAudioRoute(route: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requestEndpointForRoute(route)
        } else {
            @Suppress("DEPRECATION")
            setAudioRoute(route)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun requestEndpointForRoute(route: Int) {
        val preferredType = routeToEndpointType(route)
        val endpoint = availableEndpoints.firstOrNull { it.endpointType == preferredType }
            ?: when (route) {
                CallAudioState.ROUTE_WIRED_OR_EARPIECE,
                CallAudioState.ROUTE_EARPIECE -> availableEndpoints.firstOrNull {
                    it.endpointType == CallEndpoint.TYPE_EARPIECE ||
                            it.endpointType == CallEndpoint.TYPE_WIRED_HEADSET
                }
                else -> null
            }
            ?: return

        requestCallEndpointChange(
            endpoint,
            endpointExecutor,
            object : OutcomeReceiver<Void, CallEndpointException> {
                override fun onResult(result: Void?) = Unit
                override fun onError(error: CallEndpointException) = Unit
            }
        )
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        ringtonePlayer.stop()
        unregisterSilenceReceiver()
        if (CallManager.currentCall.value == call) {
            CallManager.setCall(null)
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ringtonePlayer.stop()
        unregisterSilenceReceiver()
        if (CallManager.inCallService == this) {
            CallManager.inCallService = null
        }
    }

    private fun registerSilenceReceiver() {
        if (silenceReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(VOLUME_CHANGED_ACTION)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(silenceReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(silenceReceiver, filter)
            }
            silenceReceiverRegistered = true
        } catch (_: Exception) {
            silenceReceiverRegistered = false
        }
    }

    private fun unregisterSilenceReceiver() {
        if (!silenceReceiverRegistered) return
        try {
            unregisterReceiver(silenceReceiver)
        } catch (_: Exception) {
            // ignore
        }
        silenceReceiverRegistered = false
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

    private fun promoteToFullInCallUi() {
        try {
            val intent = Intent(this, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        } catch (_: Exception) {
            // ignore
        }
    }

    companion object {
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        private fun endpointTypeToRoute(endpointType: Int): Int = when (endpointType) {
            CallEndpoint.TYPE_SPEAKER -> CallAudioState.ROUTE_SPEAKER
            CallEndpoint.TYPE_BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
            CallEndpoint.TYPE_WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
            CallEndpoint.TYPE_EARPIECE -> CallAudioState.ROUTE_EARPIECE
            else -> CallAudioState.ROUTE_WIRED_OR_EARPIECE
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        private fun routeToEndpointType(route: Int): Int = when (route) {
            CallAudioState.ROUTE_SPEAKER -> CallEndpoint.TYPE_SPEAKER
            CallAudioState.ROUTE_BLUETOOTH -> CallEndpoint.TYPE_BLUETOOTH
            CallAudioState.ROUTE_WIRED_HEADSET -> CallEndpoint.TYPE_WIRED_HEADSET
            CallAudioState.ROUTE_EARPIECE -> CallEndpoint.TYPE_EARPIECE
            else -> CallEndpoint.TYPE_EARPIECE
        }
    }
}
