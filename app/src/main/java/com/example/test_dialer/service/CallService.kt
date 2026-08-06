package com.example.test_dialer.service

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import com.example.test_dialer.ui.incall.InCallActivity

class CallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.setCall(call)

        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (CallManager.currentCall.value == call) {
            CallManager.setCall(null)
        }
    }
}
