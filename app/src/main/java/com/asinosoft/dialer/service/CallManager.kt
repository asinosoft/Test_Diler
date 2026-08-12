package com.asinosoft.dialer.service

import android.telecom.Call
import android.telecom.CallAudioState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CallManager {
    private val _currentCall = MutableStateFlow<Call?>(null)
    val currentCall: StateFlow<Call?> = _currentCall.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _audioRoute = MutableStateFlow(CallAudioState.ROUTE_WIRED_OR_EARPIECE)
    val audioRoute: StateFlow<Int> = _audioRoute.asStateFlow()

    private val _isHold = MutableStateFlow(false)
    val isHold: StateFlow<Boolean> = _isHold.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    var inCallService: CallService? = null

    fun setCall(call: Call?) {
        _currentCall.value = call
        if (call == null) {
            _isMuted.value = false
            _audioRoute.value = CallAudioState.ROUTE_WIRED_OR_EARPIECE
            _isHold.value = false
            _isRecording.value = false
        }
    }

    fun answer() {
        _currentCall.value?.answer(0)
    }

    fun disconnect() {
        _currentCall.value?.disconnect()
    }

    fun toggleMute() {
        val newMute = !_isMuted.value
        _isMuted.value = newMute
        inCallService?.setMuted(newMute)
    }

    fun setAudioRoute(route: Int) {
        _audioRoute.value = route
        inCallService?.setAudioRoute(route)
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
