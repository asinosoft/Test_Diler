package com.example.test_dialer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.example.test_dialer.R
import com.example.test_dialer.ui.incall.InCallActivity
import com.example.test_dialer.util.formatPhoneNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallService : InCallService() {

    companion object {
        const val CHANNEL_ID = "incall_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_DISCONNECT = "com.example.test_dialer.ACTION_DISCONNECT"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        CallManager.inCallService = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            CallManager.disconnect()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.setCall(call)

        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)

        updateNotification(call)

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_DISCONNECTED) {
                    stopForeground(true)
                } else {
                    updateNotification(call)
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

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (CallManager.inCallService == this) {
            CallManager.inCallService = null
        }
    }

    private fun updateNotification(call: Call) {
        serviceScope.launch {
            val handle = call.details?.handle
            val rawNumber = handle?.schemeSpecificPart ?: ""
            val displayName = call.details?.callerDisplayName ?: rawNumber

            var contactName: String? = null

            if (rawNumber.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    try {
                        val uri = android.net.Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(rawNumber))
                        val cursor = contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                        cursor?.use { c ->
                            if (c.moveToFirst()) {
                                val nameIdx = c.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                                if (nameIdx != -1) contactName = c.getString(nameIdx)
                            }
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }

            val title = contactName ?: if (displayName.isNotBlank()) displayName else formatPhoneNumber(rawNumber)
            val formattedNumber = formatPhoneNumber(rawNumber)

            val callerPerson = Person.Builder()
                .setName(title)
                .setImportant(true)
                .build()

            val openActivityIntent = Intent(this@CallService, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                this@CallService,
                0,
                openActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val disconnectIntent = Intent(this@CallService, CallService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            val disconnectPendingIntent = PendingIntent.getService(
                this@CallService,
                1,
                disconnectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val connectTime = call.details?.connectTimeMillis ?: System.currentTimeMillis()
            val isCallActive = call.state == Call.STATE_ACTIVE

            val callStyle = NotificationCompat.CallStyle.forOngoingCall(
                callerPerson,
                disconnectPendingIntent
            )

            val notificationBuilder = NotificationCompat.Builder(this@CallService, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_phone_white)
                .setContentTitle(title)
                .setContentText(formattedNumber)
                .setContentIntent(openPendingIntent)
                .setStyle(callStyle)
                .setOngoing(true)
                .setAutoCancel(false)
                .setUsesChronometer(isCallActive)
                .setWhen(if (isCallActive && connectTime > 0) connectTime else System.currentTimeMillis())
                .setShowWhen(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setSound(null)

            val notification = notificationBuilder.build()
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Текущие вызовы",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомление активного звонка"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
