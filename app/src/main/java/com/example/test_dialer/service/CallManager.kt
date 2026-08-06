package com.example.test_dialer.service

import android.telecom.Call
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CallManager {
    private val _currentCall = MutableStateFlow<Call?>(null)
    val currentCall: StateFlow<Call?> = _currentCall.asStateFlow()

    fun setCall(call: Call?) {
        _currentCall.value = call
    }

    fun answer() {
        _currentCall.value?.answer(0)
    }

    fun disconnect() {
        _currentCall.value?.disconnect()
    }
}
