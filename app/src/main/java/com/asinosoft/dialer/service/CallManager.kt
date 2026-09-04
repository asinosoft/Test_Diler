package com.asinosoft.dialer.service

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import com.asinosoft.dialer.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BluetoothAudioDevice(
    val id: String,
    val name: String,
    val isCurrent: Boolean = false,
    val bluetoothDevice: BluetoothDevice? = null,
    val endpoint: Any? = null
)

object CallManager {
    private val _calls = MutableStateFlow<List<Call>>(emptyList())
    val calls: StateFlow<List<Call>> = _calls.asStateFlow()

    private val _currentCall = MutableStateFlow<Call?>(null)
    val currentCall: StateFlow<Call?> = _currentCall.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _audioRoute = MutableStateFlow(CallAudioState.ROUTE_WIRED_OR_EARPIECE)
    val audioRoute: StateFlow<Int> = _audioRoute.asStateFlow()

    private val _bluetoothDevices = MutableStateFlow<List<BluetoothAudioDevice>>(emptyList())
    val bluetoothDevices: StateFlow<List<BluetoothAudioDevice>> = _bluetoothDevices.asStateFlow()

    private val _currentBluetoothDeviceName = MutableStateFlow<String?>(null)
    val currentBluetoothDeviceName: StateFlow<String?> = _currentBluetoothDeviceName.asStateFlow()

    private val _isHold = MutableStateFlow(false)
    val isHold: StateFlow<Boolean> = _isHold.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    var inCallService: CallService? = null

    fun isConferenceCall(call: Call?): Boolean {
        if (call == null) return false
        val isConfProperty = call.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true
        val hasChildren = call.children.isNotEmpty()
        return isConfProperty || hasChildren
    }

    fun updateCallsState() {
        val nonDisconnected = _calls.value.filter { it.state != Call.STATE_DISCONNECTED }
        _calls.value = nonDisconnected.toList()

        val conf = nonDisconnected.firstOrNull { isConferenceCall(it) }
        if (conf != null) {
            _currentCall.value = conf
        } else if (_currentCall.value == null || _currentCall.value?.state == Call.STATE_DISCONNECTED) {
            val nextCall = nonDisconnected.firstOrNull { it.state == Call.STATE_ACTIVE }
                ?: nonDisconnected.firstOrNull { it.state == Call.STATE_HOLDING }
                ?: nonDisconnected.firstOrNull()
            _currentCall.value = nextCall
            if (nextCall != null && nextCall.state == Call.STATE_HOLDING) {
                nextCall.unhold()
            }
        }
    }

    fun getDisplayableTopLevelCalls(): List<Call> {
        val nonDisconnected = _calls.value.filter { it.state != Call.STATE_DISCONNECTED }
        val confCall = nonDisconnected.firstOrNull { isConferenceCall(it) }

        if (confCall != null) {
            val children = confCall.children
            return listOf(confCall) + nonDisconnected.filter { it != confCall && !children.contains(it) && it.parent == null }
        }

        return nonDisconnected.filter { it.parent == null }
    }

    fun setCall(call: Call?) {
        _currentCall.value = call
        if (call != null) {
            if (!_calls.value.contains(call)) {
                _calls.value = _calls.value + call
            }
        } else {
            _calls.value = emptyList()
            _isMuted.value = false
            _audioRoute.value = CallAudioState.ROUTE_WIRED_OR_EARPIECE
            _isHold.value = false
            _isRecording.value = false
            _bluetoothDevices.value = emptyList()
            _currentBluetoothDeviceName.value = null
        }
    }

    fun onCallAdded(call: Call) {
        val existingCalls = _calls.value.filter { it.state != Call.STATE_DISCONNECTED }
        val updated = if (!existingCalls.contains(call)) existingCalls + call else existingCalls
        _calls.value = updated

        if (isConferenceCall(call)) {
            _currentCall.value = call
            return
        }

        // If there was an active call and a new call starts dialing/active, hold the previous call
        val activeCall = existingCalls.firstOrNull { it.state == Call.STATE_ACTIVE && !isConferenceCall(it) }
        if (activeCall != null && activeCall != call && (call.state == Call.STATE_DIALING || call.state == Call.STATE_CONNECTING || call.state == Call.STATE_ACTIVE)) {
            activeCall.hold()
        }

        if (_currentCall.value == null || _currentCall.value?.state == Call.STATE_DISCONNECTED) {
            _currentCall.value = call
        } else if (call.state == Call.STATE_RINGING || call.state == Call.STATE_DIALING || call.state == Call.STATE_CONNECTING || call.state == Call.STATE_ACTIVE) {
            _currentCall.value = call
        }
    }

    fun onCallRemoved(call: Call) {
        val remaining = _calls.value.filter { it != call && it.state != Call.STATE_DISCONNECTED }
        _calls.value = remaining

        if (_currentCall.value == call) {
            val conf = remaining.firstOrNull { isConferenceCall(it) }
            val nextCall = conf
                ?: remaining.firstOrNull { it.state == Call.STATE_ACTIVE }
                ?: remaining.firstOrNull { it.state == Call.STATE_HOLDING }
                ?: remaining.firstOrNull()

            _currentCall.value = nextCall
            if (nextCall != null && nextCall.state == Call.STATE_HOLDING) {
                nextCall.unhold()
            }
        }
    }

    fun selectPrimaryCall(call: Call) {
        if (_currentCall.value == call) return
        val current = _currentCall.value
        if (current != null && current.state == Call.STATE_ACTIVE) {
            current.hold()
        }
        if (call.state == Call.STATE_HOLDING) {
            call.unhold()
        }
        _currentCall.value = call
    }

    fun swapCalls() {
        val all = getDisplayableTopLevelCalls()
        if (all.size < 2) return

        val active = all.firstOrNull { it.state == Call.STATE_ACTIVE }
        val held = all.firstOrNull { it.state == Call.STATE_HOLDING }

        if (active != null && held != null) {
            active.hold()
            held.unhold()
            _currentCall.value = held
        } else if (held != null) {
            held.unhold()
            _currentCall.value = held
        }
    }

    fun mergeCalls() {
        val all = getDisplayableTopLevelCalls()
        if (all.size < 2) return
        val call1 = all[0]
        val call2 = all[1]
        try {
            call1.conference(call2)
        } catch (_: Exception) {
            try {
                call2.conference(call1)
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    fun holdAndAnswer(incomingCall: Call) {
        val current = _currentCall.value
        if (current != null && current.state == Call.STATE_ACTIVE) {
            current.hold()
        }
        incomingCall.answer(0)
        _currentCall.value = incomingCall
    }

    fun endAndAnswer(incomingCall: Call) {
        val current = _currentCall.value
        current?.disconnect()
        incomingCall.answer(0)
        _currentCall.value = incomingCall
    }

    fun disconnectCall(call: Call) {
        call.disconnect()
    }

    fun answer() {
        _currentCall.value?.answer(0)
    }

    fun disconnect() {
        val all = _calls.value.filter { it.state != Call.STATE_DISCONNECTED }
        if (all.size > 1) {
            _currentCall.value?.disconnect()
        } else {
            _currentCall.value?.disconnect()
            all.forEach { it.disconnect() }
        }
    }

    fun openNewCallScreen(context: Context) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // ignore
        }
    }

    fun silenceRinger() {
        inCallService?.silenceIncomingRinger()
    }

    fun toggleMute() {
        val newMute = !_isMuted.value
        _isMuted.value = newMute
        inCallService?.setMuted(newMute)
    }

    fun setAudioRoute(route: Int) {
        _audioRoute.value = route
        inCallService?.requestAudioRoute(route)
    }

    fun updateAudioRoute(route: Int) {
        _audioRoute.value = route
    }

    fun updateBluetoothDevices(devices: List<BluetoothAudioDevice>) {
        _bluetoothDevices.value = devices
    }

    fun updateCurrentBluetoothDeviceName(name: String?) {
        _currentBluetoothDeviceName.value = name
    }

    fun selectBluetoothDevice(device: BluetoothAudioDevice) {
        inCallService?.selectBluetoothDevice(device)
    }

    fun toggleSpeaker() {
        if (_audioRoute.value == CallAudioState.ROUTE_SPEAKER) {
            setAudioRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE)
        } else {
            setAudioRoute(CallAudioState.ROUTE_SPEAKER)
        }
    }

    fun toggleBluetooth() {
        if (_audioRoute.value == CallAudioState.ROUTE_BLUETOOTH) {
            setAudioRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE)
        } else {
            setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
        }
    }

    fun toggleHold() {
        val call = _currentCall.value ?: return
        if (_isHold.value) {
            call.unhold()
            _isHold.value = false
        } else {
            call.hold()
            _isHold.value = true
        }
    }

    fun toggleRecord() {
        _isRecording.value = !_isRecording.value
    }

    fun playDtmf(digit: Char) {
        _currentCall.value?.playDtmfTone(digit)
    }

    fun stopDtmf() {
        _currentCall.value?.stopDtmfTone()
    }
}
