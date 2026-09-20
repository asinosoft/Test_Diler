package com.asinosoft.cdm.service

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
import androidx.core.content.ContextCompat
import com.asinosoft.cdm.data.model.CallState
import com.asinosoft.cdm.data.repository.ContactRingtoneManager
import com.asinosoft.cdm.ui.incall.FloatingCallOverlayManager
import com.asinosoft.cdm.ui.incall.InCallActivity
import com.asinosoft.cdm.ui.incall.IncomingCallPopupActivity
import java.util.concurrent.Executor

class CallService : InCallService() {
    lateinit var notification: NotificationManager
    private val ringtonePlayer by lazy { CallRingtonePlayer(this) }
    private var silenceReceiverRegistered = false
    private var availableEndpoints: List<CallEndpoint> = emptyList()
    private val endpointExecutor: Executor
        get() = ContextCompat.getMainExecutor(this)

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
                // Don't force open InCallActivity if floating overlay is handling it
                if (!FloatingCallOverlayManager.isShowing()) {
                    promoteToFullInCallUi()
                }
            }
            NotificationManager.ACTION_DISCONNECT -> CallManager.disconnect()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)

        // Check and update bluetooth audio state immediately upon call added
        try {
            val audioState = callAudioState
            if (audioState != null) {
                CallManager.updateAudioRoute(audioState.route)
                updateBluetoothDevicesFromAudioState(audioState)
            }
        } catch (_: Exception) {
            // ignore
        }

        var wasRinging = (call.state == Call.STATE_RINGING)
        var wasAnswered = (call.state == Call.STATE_ACTIVE)

        val handle = call.details?.handle
        val rawNumber = handle?.schemeSpecificPart ?: ""

        val showPopup = (call.state == Call.STATE_RINGING) && shouldShowFloatingPopup(this, call)
        if (showPopup) {
            if (FloatingCallOverlayManager.canDrawOverlay(this)) {
                FloatingCallOverlayManager.show(this, onPromoteToFullScreen = { promoteToFullInCallUi() })
            } else {
                val intent = Intent(this, IncomingCallPopupActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)
            }
        } else {
            FloatingCallOverlayManager.hide()
            val intent = Intent(this, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        }

        val customRingtoneUri = ContactRingtoneManager.getCustomRingtoneForNumber(this, rawNumber)

        if (call.state == Call.STATE_RINGING) {
            ringtonePlayer.start(customRingtoneUri)
            registerSilenceReceiver()
        }

        notification.showCallNotification(CallState.fromSystemCall(call, this))

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                CallManager.updateCallsState()
                if (state == Call.STATE_RINGING) {
                    wasRinging = true
                    ringtonePlayer.start(customRingtoneUri)
                    registerSilenceReceiver()
                } else if (state == Call.STATE_ACTIVE) {
                    wasAnswered = true
                    ringtonePlayer.stop()
                    unregisterSilenceReceiver()
                    // Stay in Floating window if it is already handling the call
                } else {
                    unregisterSilenceReceiver()
                }

                if (state == Call.STATE_DISCONNECTED) {
                    if (CallManager.calls.value.none { it.state == Call.STATE_RINGING }) {
                        ringtonePlayer.stop()
                        unregisterSilenceReceiver()
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
            val resolvedName = resolveBluetoothDeviceName(callEndpoint.endpointName?.toString())
            CallManager.updateCurrentBluetoothDeviceName(resolvedName)
            refreshBluetoothDevicesFromEndpoints()
        } else {
            CallManager.updateCurrentBluetoothDeviceName(null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) {
        this.availableEndpoints = availableEndpoints
        refreshBluetoothDevicesFromEndpoints()
    }

    fun selectBluetoothDevice(device: BluetoothAudioDevice) {
        CallManager.updateCurrentBluetoothDeviceName(device.name)
        CallManager.updateAudioRoute(CallAudioState.ROUTE_BLUETOOTH)

        // Android 14+: requestBluetoothAudio is deprecated and on many OEMs disconnects BT
        // when switching devices. Always use CallEndpoint here.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val endpoint = findFreshBluetoothEndpoint(device) ?: return
            requestCallEndpointChange(
                endpoint,
                endpointExecutor,
                object : OutcomeReceiver<Void, CallEndpointException> {
                    override fun onResult(result: Void?) {
                        CallManager.updateAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
                        CallManager.updateCurrentBluetoothDeviceName(
                            resolveBluetoothDeviceName(endpoint.endpointName?.toString())
                                .takeIf { it != "Bluetooth" }
                                ?: device.name
                        )
                    }

                    override fun onError(error: CallEndpointException) {
                        // Retry once with a freshly resolved endpoint from the current list.
                        val retry = findFreshBluetoothEndpoint(device) ?: return
                        if (retry.identifier == endpoint.identifier) return
                        requestCallEndpointChange(
                            retry,
                            endpointExecutor,
                            object : OutcomeReceiver<Void, CallEndpointException> {
                                override fun onResult(result: Void?) {
                                    CallManager.updateAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
                                    CallManager.updateCurrentBluetoothDeviceName(device.name)
                                }

                                override fun onError(error: CallEndpointException) = Unit
                            }
                        )
                    }
                }
            )
            return
        }

        // API 28–33: switch the specific BluetoothDevice while staying on BT route.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && requestBluetoothAudioForDevice(device)) {
            return
        }

        @Suppress("DEPRECATION")
        setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun findFreshBluetoothEndpoint(device: BluetoothAudioDevice): CallEndpoint? {
        val btEndpoints = availableEndpoints.filter { it.endpointType == CallEndpoint.TYPE_BLUETOOTH }
        if (btEndpoints.isEmpty()) return null

        btEndpoints.firstOrNull { it.identifier.toString() == device.id }?.let { return it }

        val stored = device.endpoint as? CallEndpoint
        if (stored != null) {
            btEndpoints.firstOrNull { it.identifier == stored.identifier }?.let { return it }
        }

        val byName = btEndpoints.filter {
            resolveBluetoothDeviceName(it.endpointName?.toString()) == device.name
        }
        return byName.singleOrNull() ?: byName.firstOrNull()
    }

    private fun requestBluetoothAudioForDevice(device: BluetoothAudioDevice): Boolean {
        // Never use on API 34+ — use CallEndpoint instead.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            return false
        }
        val btDevice = device.bluetoothDevice
            ?: findSupportedBluetoothDevice(device)
            ?: return false
        return try {
            requestBluetoothAudio(btDevice)
            CallManager.updateAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
            CallManager.updateCurrentBluetoothDeviceName(device.name)
            true
        } catch (_: Exception) {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun findSupportedBluetoothDevice(device: BluetoothAudioDevice): BluetoothDevice? {
        val supported = callAudioState?.supportedBluetoothDevices?.toList().orEmpty()
        if (supported.isEmpty()) return null

        supported.firstOrNull { it.address.equals(device.id, ignoreCase = true) }?.let { return it }

        val byName = supported.filter {
            getDeviceDisplayName(it)?.equals(device.name, ignoreCase = true) == true ||
                    resolveBluetoothDeviceName(it.address).equals(device.name, ignoreCase = true)
        }
        if (byName.size == 1) return byName.first()

        return null
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun refreshBluetoothDevicesFromEndpoints() {
        val btEndpoints = availableEndpoints.filter { it.endpointType == CallEndpoint.TYPE_BLUETOOTH }
        val headsetConnected = hasConnectedBluetoothHeadset()
        CallManager.updateBluetoothHeadsetConnected(headsetConnected)

        if (!headsetConnected) {
            // Endpoints can linger after BT audio toggle; don't treat them as a connected headset.
            val hasBtRoute = callAudioState?.let {
                (it.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH) != 0
            } == true
            CallManager.updateBluetoothDevices(
                if (hasBtRoute) {
                    listOf(BluetoothAudioDevice(id = "default_bt", name = "Bluetooth", isCurrent = false))
                } else {
                    emptyList()
                }
            )
            if (CallManager.audioRoute.value != CallAudioState.ROUTE_BLUETOOTH) {
                CallManager.updateCurrentBluetoothDeviceName(null)
            }
            return
        }

        if (btEndpoints.isEmpty()) return

        val supportedBt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            callAudioState?.supportedBluetoothDevices?.toList().orEmpty()
        } else {
            emptyList()
        }
        val activeBt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            callAudioState?.activeBluetoothDevice
        } else {
            null
        }
        val currentName = CallManager.currentBluetoothDeviceName.value

        val devices = btEndpoints.map { endpoint ->
            val rawName = endpoint.endpointName?.toString()
            val resolvedName = resolveBluetoothDeviceName(rawName)
            val matchedBt = matchBluetoothDeviceForEndpoint(resolvedName, rawName, supportedBt)
            val isCurrentByActive = matchedBt != null && activeBt != null &&
                    matchedBt.address.equals(activeBt.address, ignoreCase = true)
            val isCurrent = isCurrentByActive ||
                    resolvedName == currentName ||
                    (!rawName.isNullOrBlank() && rawName == currentName)

            BluetoothAudioDevice(
                id = endpoint.identifier.toString(),
                name = resolvedName,
                isCurrent = isCurrent,
                bluetoothDevice = matchedBt,
                endpoint = endpoint
            )
        }
        CallManager.updateBluetoothDevices(devices)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun matchBluetoothDeviceForEndpoint(
        resolvedName: String,
        rawName: String?,
        supported: Collection<BluetoothDevice>
    ): BluetoothDevice? {
        if (supported.isEmpty()) return null

        if (!rawName.isNullOrBlank() && isMacAddressString(rawName)) {
            val address = rawName.replace("bt_", "", ignoreCase = true).trim()
            supported.firstOrNull { it.address.equals(address, ignoreCase = true) }?.let { return it }
        }

        val byName = supported.filter {
            val name = getDeviceDisplayName(it) ?: resolveBluetoothDeviceName(it.address)
            name.equals(resolvedName, ignoreCase = true)
        }
        return byName.singleOrNull()
    }

    private fun updateBluetoothDevicesFromAudioState(audioState: CallAudioState) {
        val hasBtRoute = (audioState.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH) != 0
        val headsetConnected = hasConnectedBluetoothHeadset(audioState)
        CallManager.updateBluetoothHeadsetConnected(headsetConnected)

        // API 34+: prefer CallEndpoint list — but only when a headset is really connected.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            availableEndpoints.any { it.endpointType == CallEndpoint.TYPE_BLUETOOTH }
        ) {
            if (headsetConnected) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val activeName = resolveBluetoothDeviceName(
                        getDeviceDisplayName(audioState.activeBluetoothDevice)
                            ?: audioState.activeBluetoothDevice?.address
                    )
                    if (activeName != "Bluetooth") {
                        CallManager.updateCurrentBluetoothDeviceName(activeName)
                    }
                }
                refreshBluetoothDevicesFromEndpoints()
            } else {
                CallManager.updateBluetoothDevices(
                    if (hasBtRoute) {
                        listOf(BluetoothAudioDevice(id = "default_bt", name = "Bluetooth", isCurrent = false))
                    } else {
                        emptyList()
                    }
                )
                if (audioState.route != CallAudioState.ROUTE_BLUETOOTH) {
                    CallManager.updateCurrentBluetoothDeviceName(null)
                }
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val activeBt = audioState.activeBluetoothDevice
            val supportedBt = audioState.supportedBluetoothDevices?.toList().orEmpty()

            if (headsetConnected && supportedBt.isNotEmpty()) {
                val activeName = resolveBluetoothDeviceName(
                    getDeviceDisplayName(activeBt) ?: activeBt?.address
                )
                if (activeName != "Bluetooth") {
                    CallManager.updateCurrentBluetoothDeviceName(activeName)
                }

                val devices = supportedBt.map { device ->
                    val name = resolveBluetoothDeviceName(
                        getDeviceDisplayName(device) ?: device.address
                    )
                    val isCurrent = device == activeBt ||
                            (activeBt != null && device.address.equals(activeBt.address, ignoreCase = true))
                    BluetoothAudioDevice(
                        id = device.address ?: name,
                        name = name,
                        isCurrent = isCurrent,
                        bluetoothDevice = device
                    )
                }
                CallManager.updateBluetoothDevices(devices)
            } else {
                CallManager.updateBluetoothDevices(
                    if (hasBtRoute) {
                        listOf(BluetoothAudioDevice(id = "default_bt", name = "Bluetooth", isCurrent = false))
                    } else {
                        emptyList()
                    }
                )
                if (audioState.route != CallAudioState.ROUTE_BLUETOOTH) {
                    CallManager.updateCurrentBluetoothDeviceName(null)
                }
            }
        } else {
            CallManager.updateBluetoothDevices(
                if (hasBtRoute) {
                    listOf(BluetoothAudioDevice(id = "default_bt", name = "Bluetooth", isCurrent = false))
                } else {
                    emptyList()
                }
            )
        }
    }

    private fun hasConnectedBluetoothHeadset(
        audioState: CallAudioState? = callAudioState
    ): Boolean {
        if (audioState != null && audioState.route == CallAudioState.ROUTE_BLUETOOTH) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && audioState != null) {
            if (audioState.activeBluetoothDevice != null) return true
            if (!audioState.supportedBluetoothDevices.isNullOrEmpty()) return true
        }
        return hasConnectedBluetoothAudioDevice()
    }

    private fun hasConnectedBluetoothAudioDevice(): Boolean {
        return try {
            val am = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return false
            val btTypes = setOf(
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
            )
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            outputs.any { it.type in btTypes } || inputs.any { it.type in btTypes }
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveBluetoothDeviceName(rawNameOrAddress: String?): String {
        if (rawNameOrAddress.isNullOrBlank()) return "Bluetooth"
        if (!isMacAddressString(rawNameOrAddress)) return rawNameOrAddress.trim()

        val cleanAddress = rawNameOrAddress.replace("bt_", "", ignoreCase = true).trim()

        // Match exact device by address from CallAudioState
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val audioState = callAudioState
            if (audioState != null) {
                val candidates = buildList {
                    audioState.activeBluetoothDevice?.let { add(it) }
                    addAll(audioState.supportedBluetoothDevices.orEmpty())
                }
                for (dev in candidates) {
                    if (!dev.address.equals(cleanAddress, ignoreCase = true)) continue
                    val name = getDeviceDisplayName(dev)
                    if (!name.isNullOrBlank() && !isMacAddressString(name)) return name
                }
            }
        }

        lookupBondedDeviceName(cleanAddress)?.let { return it }
        lookupAudioDeviceProductName(cleanAddress)?.let { return it }

        return "Bluetooth"
    }

    private fun isMacAddressString(str: String): Boolean {
        val clean = str.trim()
        return clean.matches(Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")) ||
                clean.matches(Regex("^[0-9A-Fa-f]{2}(-[0-9A-Fa-f]{2}){5}$")) ||
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
            // BLUETOOTH_CONNECT may be missing
        }
        return lookupBondedDeviceName(device.address)
            ?: lookupAudioDeviceProductName(device.address)
    }

    private fun lookupBondedDeviceName(address: String?): String? {
        if (address.isNullOrBlank()) return null
        return try {
            val btManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            @Suppress("MissingPermission")
            val matched = adapter?.bondedDevices?.find {
                it.address.equals(address, ignoreCase = true)
            } ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                @Suppress("MissingPermission")
                val alias = matched.alias
                if (!alias.isNullOrBlank() && !isMacAddressString(alias)) return alias
            }
            @Suppress("MissingPermission")
            val name = matched.name
            if (!name.isNullOrBlank() && !isMacAddressString(name)) name else null
        } catch (_: Exception) {
            null
        }
    }

    private fun lookupAudioDeviceProductName(address: String?): String? {
        if (address.isNullOrBlank()) return null
        return try {
            val am = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return null
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS) +
                    am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val btTypes = setOf(
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
                android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER
            )
            for (device in devices) {
                if (device.type !in btTypes) continue
                val deviceAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    device.address
                } else {
                    null
                }
                if (deviceAddress.isNullOrBlank() ||
                    !deviceAddress.equals(address, ignoreCase = true)
                ) {
                    continue
                }
                val product = device.productName?.toString()?.trim()
                if (!product.isNullOrBlank() && !isMacAddressString(product)) return product
            }
            null
        } catch (_: Exception) {
            null
        }
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
