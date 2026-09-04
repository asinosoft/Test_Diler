package com.asinosoft.dialer.service

import android.app.KeyguardManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
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
import com.asinosoft.dialer.ui.incall.FloatingCallOverlayManager
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
        CallManager.onCallAdded(call)

        var wasRinging = (call.state == Call.STATE_RINGING)
        var wasAnswered = (call.state == Call.STATE_ACTIVE)

        val handle = call.details?.handle
        val rawNumber = handle?.schemeSpecificPart ?: ""

        val showPopup = (call.state == Call.STATE_RINGING) && shouldShowFloatingPopup(this, call)
        if (showPopup) {
            FloatingCallOverlayManager.show(this, onPromoteToFullScreen = { promoteToFullInCallUi() })
        } else {
            FloatingCallOverlayManager.hide()
            val intent = Intent(this, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        }

        if (call.state == Call.STATE_RINGING) {
            ringtonePlayer.start()
            registerSilenceReceiver()
        }

        notification.showCallNotification(CallState.fromSystemCall(call, this))

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                CallManager.updateCallsState()
                if (state == Call.STATE_RINGING) {
                    wasRinging = true
                    ringtonePlayer.start()
                    registerSilenceReceiver()
                } else if (state == Call.STATE_ACTIVE) {
                    wasAnswered = true
                    ringtonePlayer.stop()
                    unregisterSilenceReceiver()
                    FloatingCallOverlayManager.hide()
                    // BT headset / external answer while floating popup is showing
                    promoteToFullInCallUi()
                } else {
                    unregisterSilenceReceiver()
                }

                if (state == Call.STATE_DISCONNECTED) {
                    if (CallManager.calls.value.none { it.state == Call.STATE_RINGING }) {
                        ringtonePlayer.stop()
                        unregisterSilenceReceiver()
                        FloatingCallOverlayManager.hide()
                    }
                    val topCalls = CallManager.getDisplayableTopLevelCalls()
                    if (topCalls.isEmpty() && CallManager.calls.value.none { it.state != Call.STATE_DISCONNECTED }) {
                        stopForeground(true)
                    }
                    if (wasRinging && !wasAnswered && rawNumber.isNotBlank() && CallManager.calls.value.isEmpty()) {
                        notification.showMissedCallNotification(rawNumber)
                    }
                } else {
                    notification.showCallNotification(CallState.fromSystemCall(call, this@CallService))
                }
            }

            override fun onParentChanged(call: Call, parent: Call?) {
                CallManager.updateCallsState()
            }

            override fun onChildrenChanged(call: Call, children: MutableList<Call>?) {
                CallManager.updateCallsState()
            }

            override fun onDetailsChanged(call: Call, details: Call.Details?) {
                CallManager.updateCallsState()
            }
        })
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (audioState != null) {
            CallManager.updateAudioRoute(audioState.route)
            updateBluetoothDevicesFromAudioState(audioState)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        val route = endpointTypeToRoute(callEndpoint.endpointType)
        CallManager.updateAudioRoute(route)
        if (callEndpoint.endpointType == CallEndpoint.TYPE_BLUETOOTH) {
            val resolvedName = resolveBluetoothDeviceName(callEndpoint.endpointName.toString())
            CallManager.updateCurrentBluetoothDeviceName(resolvedName)
        } else {
            CallManager.updateCurrentBluetoothDeviceName(null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) {
        this.availableEndpoints = availableEndpoints
        val btEndpoints = availableEndpoints.filter { it.endpointType == CallEndpoint.TYPE_BLUETOOTH }
        if (btEndpoints.isNotEmpty()) {
            val devices = btEndpoints.map { endpoint ->
                val resolvedName = resolveBluetoothDeviceName(endpoint.endpointName.toString())
                BluetoothAudioDevice(
                    id = endpoint.identifier.toString(),
                    name = resolvedName,
                    isCurrent = resolvedName == CallManager.currentBluetoothDeviceName.value,
                    endpoint = endpoint
                )
            }
            CallManager.updateBluetoothDevices(devices)
        }
    }

    fun selectBluetoothDevice(device: BluetoothAudioDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && device.endpoint is CallEndpoint) {
            requestCallEndpointChange(
                device.endpoint as CallEndpoint,
                endpointExecutor,
                object : OutcomeReceiver<Void, CallEndpointException> {
                    override fun onResult(result: Void?) = Unit
                    override fun onError(error: CallEndpointException) = Unit
                }
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && device.bluetoothDevice != null) {
            try {
                requestBluetoothAudio(device.bluetoothDevice)
            } catch (_: Exception) {
                setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
            }
        } else {
            requestAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
        }
    }

    private fun updateBluetoothDevicesFromAudioState(audioState: CallAudioState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val activeBt = audioState.activeBluetoothDevice
            val supportedBt = audioState.supportedBluetoothDevices?.toList().orEmpty()

            val activeName = getDeviceDisplayName(activeBt)
            if (!activeName.isNullOrBlank()) {
                CallManager.updateCurrentBluetoothDeviceName(activeName)
            }

            val devices = supportedBt.map { device ->
                val name = getDeviceDisplayName(device) ?: "Bluetooth"
                val isCurrent = device == activeBt || (activeBt != null && device.address == activeBt.address)
                BluetoothAudioDevice(
                    id = device.address ?: name,
                    name = name,
                    isCurrent = isCurrent,
                    bluetoothDevice = device
                )
            }
            if (devices.isNotEmpty()) {
                CallManager.updateBluetoothDevices(devices)
            }
        }
    }

    private fun resolveBluetoothDeviceName(rawNameOrAddress: String?): String {
        if (rawNameOrAddress.isNullOrBlank()) return "Bluetooth"
        val isMac = isMacAddressString(rawNameOrAddress)
        if (!isMac) return rawNameOrAddress

        // Try to match from CallAudioState active/supported devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val audioState = callAudioState
            if (audioState != null) {
                val active = audioState.activeBluetoothDevice
                val activeName = getDeviceDisplayName(active)
                if (!activeName.isNullOrBlank() && !isMacAddressString(activeName) && activeName != "Bluetooth") {
                    return activeName
                }
                for (dev in audioState.supportedBluetoothDevices.orEmpty()) {
                    val name = getDeviceDisplayName(dev)
                    if (!name.isNullOrBlank() && !isMacAddressString(name) && name != "Bluetooth") {
                        return name
                    }
                }
            }
        }

        // Try to match from BluetoothAdapter bonded devices
        try {
            val btManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter != null) {
                val cleanAddress = rawNameOrAddress.replace("bt_", "", ignoreCase = true).trim()
                @Suppress("MissingPermission")
                val bonded = adapter.bondedDevices
                val matched = bonded?.find { it.address.equals(cleanAddress, ignoreCase = true) }
                if (matched != null) {
                    val name = getDeviceDisplayName(matched)
                    if (!name.isNullOrBlank() && !isMacAddressString(name) && name != "Bluetooth") {
                        return name
                    }
                }
            }
        } catch (_: Exception) {
            // ignore
        }

        return "Bluetooth"
    }

    private fun isMacAddressString(str: String): Boolean {
        val clean = str.trim()
        return clean.matches(Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")) ||
                clean.matches(Regex(".*([0-9A-Fa-f]{2}:){3,}.*")) ||
                clean.startsWith("bt_", ignoreCase = true)
    }

    private fun getDeviceDisplayName(device: BluetoothDevice?): String? {
        if (device == null) return null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                @Suppress("MissingPermission")
                val alias = device.alias
                if (!alias.isNullOrBlank() && !isMacAddressString(alias)) return alias
            }
            @Suppress("MissingPermission")
            val name = device.name
            if (!name.isNullOrBlank() && !isMacAddressString(name)) return name
        } catch (_: Exception) {
            // ignore
        }
        return null
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
        CallManager.onCallRemoved(call)
        if (CallManager.calls.value.none { it.state == Call.STATE_RINGING }) {
            ringtonePlayer.stop()
            unregisterSilenceReceiver()
            FloatingCallOverlayManager.hide()
        }
        if (CallManager.calls.value.isEmpty()) {
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ringtonePlayer.stop()
        unregisterSilenceReceiver()
        FloatingCallOverlayManager.hide()
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

    private fun shouldShowFloatingPopup(context: Context, call: Call): Boolean {
        // If there are other ongoing calls (active, held, dialing), show only Full Screen InCallActivity
        val otherCalls = CallManager.calls.value.filter { it != call && it.state != Call.STATE_DISCONNECTED }
        if (otherCalls.isNotEmpty()) {
            return false
        }

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
            FloatingCallOverlayManager.hide()
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
