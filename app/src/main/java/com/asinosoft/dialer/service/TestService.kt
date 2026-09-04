package com.asinosoft.dialer.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.asinosoft.dialer.data.model.CallState

class TestService: Service() {
    lateinit var notification: NotificationManager

    inner class LocalBinder : Binder() {
        fun getService(): TestService = this@TestService
    }

    val binder = LocalBinder()
    
    override fun onBind(p0: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        notification = NotificationManager(this)
    }

    fun showCallNotification(call: CallState) = notification.showCallNotification(call)

    fun showMissedCallNotification(rawNumber: String) = notification.showMissedCallNotification(rawNumber)

    fun hideNotification() = notification.hideNotification()
}
