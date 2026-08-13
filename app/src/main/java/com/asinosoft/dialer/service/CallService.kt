package com.asinosoft.dialer.service

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.createBitmap
import com.asinosoft.dialer.MainActivity
import com.asinosoft.dialer.R
import com.asinosoft.dialer.ui.incall.InCallActivity
import com.asinosoft.dialer.ui.incall.IncomingCallPopupActivity
import com.asinosoft.dialer.util.formatPhoneNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

class CallService : InCallService() {

    companion object {
        const val CHANNEL_ID = "incall_service_channel"
        const val MISSED_CHANNEL_ID = "missed_call_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_ANSWER = "com.asinosoft.dialer.ACTION_ANSWER"
        const val ACTION_DISCONNECT = "com.asinosoft.dialer.ACTION_DISCONNECT"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        CallManager.inCallService = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ANSWER -> CallManager.answer()
            ACTION_DISCONNECT -> CallManager.disconnect()
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

        updateNotification(call)

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
                        showMissedCallNotification(rawNumber)
                    }
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
            var contactBitmap: Bitmap? = null

            if (rawNumber.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    try {
                        val uri = Uri.withAppendedPath(
                            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                            Uri.encode(rawNumber)
                        )
                        val cursor = contentResolver.query(
                            uri,
                            arrayOf(
                                ContactsContract.PhoneLookup.DISPLAY_NAME,
                                ContactsContract.PhoneLookup.PHOTO_URI
                            ),
                            null,
                            null,
                            null
                        )
                        cursor?.use { c ->
                            if (c.moveToFirst()) {
                                val nameIdx =
                                    c.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                                val photoIdx =
                                    c.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                                if (nameIdx != -1) contactName = c.getString(nameIdx)
                                if (photoIdx != -1) {
                                    val photoUriStr = c.getString(photoIdx)
                                    if (!photoUriStr.isNullOrBlank()) {
                                        contentResolver.openInputStream(photoUriStr.toUri())
                                            ?.use { stream ->
                                                contactBitmap = BitmapFactory.decodeStream(stream)
                                            }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // ignore
                    }
                }
            }

            val title = contactName ?: displayName.ifBlank { formatPhoneNumber(rawNumber) }
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

            val answerIntent =
                Intent(this@CallService, CallService::class.java).apply { action = ACTION_ANSWER }
            val answerPendingIntent = PendingIntent.getService(
                this@CallService,
                10,
                answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val disconnectIntent = Intent(this@CallService, CallService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            val disconnectPendingIntent = PendingIntent.getService(
                this@CallService,
                11,
                disconnectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val connectTime = call.details?.connectTimeMillis ?: System.currentTimeMillis()
            val isRinging = call.state == Call.STATE_RINGING
            val isCallActive = call.state == Call.STATE_ACTIVE

            val circularAvatar: Bitmap? = if (contactBitmap != null) {
                getCircularBitmap(contactBitmap)
            } else {
                createRoundAvatarBitmap(title)
            }

            if (isRinging) {
                val collapsedView = android.widget.RemoteViews(
                    packageName,
                    R.layout.notification_incoming_call_collapsed
                ).apply {
                    setTextViewText(R.id.notification_title, title)
                    setTextViewText(R.id.notification_subtitle, "Входящий вызов")
                    if (circularAvatar != null) {
                        setImageViewBitmap(R.id.notification_avatar, circularAvatar)
                    }
                    setOnClickPendingIntent(R.id.btn_answer, answerPendingIntent)
                    setOnClickPendingIntent(R.id.btn_decline, disconnectPendingIntent)
                }

                val expandedView = android.widget.RemoteViews(
                    packageName,
                    R.layout.notification_incoming_call_expanded
                ).apply {
                    setTextViewText(R.id.notification_title, "Входящий вызов")
                    setTextViewText(R.id.notification_subtitle, title)
                    setTextViewText(R.id.notification_number, formattedNumber)
                    if (circularAvatar != null) {
                        setImageViewBitmap(R.id.notification_avatar, circularAvatar)
                    }
                    setOnClickPendingIntent(R.id.btn_answer, answerPendingIntent)
                    setOnClickPendingIntent(R.id.btn_decline, disconnectPendingIntent)
                }

                val notificationBuilder = NotificationCompat.Builder(this@CallService, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_phone_white)
                    .setCustomContentView(collapsedView)
                    .setCustomBigContentView(expandedView)
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    .setContentIntent(openPendingIntent)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setShowWhen(false)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setSound(null)

                val notification = notificationBuilder.build()
                startForeground(NOTIFICATION_ID, notification)
            } else {
                val ongoingStyle = NotificationCompat.CallStyle.forOngoingCall(
                    callerPerson,
                    disconnectPendingIntent
                )

                val notificationBuilder = NotificationCompat.Builder(this@CallService, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_phone_white)
                    .setContentTitle(title)
                    .setContentText(formattedNumber)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(formattedNumber))
                    .setContentIntent(openPendingIntent)
                    .setStyle(ongoingStyle)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setUsesChronometer(isCallActive)
                    .setWhen(if (isCallActive && connectTime > 0) connectTime else System.currentTimeMillis())
                    .setShowWhen(false)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setSound(null)

                if (contactBitmap != null) {
                    notificationBuilder.setLargeIcon(contactBitmap)
                }

                val notification = notificationBuilder.build()
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun showMissedCallNotification(rawNumber: String) {
        serviceScope.launch {
            var contactName: String? = null
            var contactBitmap: Bitmap? = null

            if (rawNumber.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    try {
                        val uri = Uri.withAppendedPath(
                            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                            Uri.encode(rawNumber)
                        )
                        val cursor = contentResolver.query(
                            uri,
                            arrayOf(
                                ContactsContract.PhoneLookup.DISPLAY_NAME,
                                ContactsContract.PhoneLookup.PHOTO_URI
                            ),
                            null,
                            null,
                            null
                        )
                        cursor?.use { c ->
                            if (c.moveToFirst()) {
                                val nameIdx =
                                    c.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                                val photoIdx =
                                    c.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                                if (nameIdx != -1) contactName = c.getString(nameIdx)
                                if (photoIdx != -1) {
                                    val photoUriStr = c.getString(photoIdx)
                                    if (!photoUriStr.isNullOrBlank()) {
                                        contentResolver.openInputStream(photoUriStr.toUri())
                                            ?.use { stream ->
                                                contactBitmap = BitmapFactory.decodeStream(stream)
                                            }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // ignore
                    }
                }
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    MISSED_CHANNEL_ID,
                    "Пропущенные вызовы",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Уведомления о пропущенных звонках"
                    enableVibration(true)
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val formattedNumber = formatPhoneNumber(rawNumber)
            val title = "Пропущенный вызов"
            val contactDisplayName = contactName ?: formattedNumber

            val appIntent = Intent(this@CallService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val appPendingIntent = PendingIntent.getActivity(
                this@CallService,
                0,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val callBackIntent =
                Intent(Intent.ACTION_CALL, "tel:${Uri.encode(rawNumber)}".toUri()).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            val callBackPendingIntent = PendingIntent.getActivity(
                this@CallService,
                1,
                callBackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val smsIntent =
                Intent(Intent.ACTION_SENDTO, "smsto:${Uri.encode(rawNumber)}".toUri()).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            val smsPendingIntent = PendingIntent.getActivity(
                this@CallService,
                2,
                smsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()

            val notificationBuilder =
                NotificationCompat.Builder(this@CallService, MISSED_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_missed_call)
                    .setColor(0xFFFF3B30.toInt())
                    .setColorized(true)
                    .setContentTitle(title)
                    .setContentText(contactDisplayName)
                    .setContentIntent(appPendingIntent)
                    .setAutoCancel(true)
                    .setNumber(1)
                    .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setShowWhen(true)
                    .setWhen(System.currentTimeMillis())
                    .addAction(
                        android.R.drawable.ic_menu_call,
                        "Перезвонить",
                        callBackPendingIntent
                    )
                    .addAction(
                        android.R.drawable.ic_menu_send,
                        "Сообщение",
                        smsPendingIntent
                    )

            val redIconBitmap = contactBitmap ?: createRedMissedCallBitmap(this@CallService)
            if (redIconBitmap != null) {
                notificationBuilder.setLargeIcon(redIconBitmap)
            }

            notificationManager.notify(notificationId, notificationBuilder.build())
            suppressSystemMissedCallNotification(rawNumber)
        }
    }

    private fun createRedMissedCallBitmap(context: Context): Bitmap? {
        try {
            val size = 128
            val bitmap = createBitmap(size, size)
            val canvas = android.graphics.Canvas(bitmap)

            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFF3B30.toInt()
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

            val drawable =
                androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_missed_call)
            if (drawable != null) {
                val iconSize = 72
                val margin = (size - iconSize) / 2
                drawable.setBounds(margin, margin, margin + iconSize, margin + iconSize)
                drawable.setTint(android.graphics.Color.WHITE)
                drawable.draw(canvas)
            }
            return bitmap
        } catch (_: Exception) {
            return null
        }
    }

    private fun suppressSystemMissedCallNotification(rawNumber: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val telecomManager =
                    getSystemService(TELECOM_SERVICE) as? android.telecom.TelecomManager
                @Suppress("MissingPermission")
                telecomManager?.cancelMissedCallsNotification()
            }
        } catch (_: Exception) {
            // ignore
        }

        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_CALL_LOG
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.CallLog.Calls.NUMBER, rawNumber)
                    put(
                        android.provider.CallLog.Calls.TYPE,
                        android.provider.CallLog.Calls.MISSED_TYPE
                    )
                    put(android.provider.CallLog.Calls.DATE, System.currentTimeMillis())
                    put(android.provider.CallLog.Calls.NEW, 0)
                    put(android.provider.CallLog.Calls.IS_READ, 1)
                }
                contentResolver.insert(android.provider.CallLog.Calls.CONTENT_URI, values)
            }
        } catch (_: Exception) {
            // ignore
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
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
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

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = Math.min(bitmap.width, bitmap.height)
        val output = createBitmap(size, size)
        val canvas = android.graphics.Canvas(output)

        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val rect = android.graphics.Rect(0, 0, size, size)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, null, rect, paint)

        return output
    }

    private fun createRoundAvatarBitmap(name: String): Bitmap? {
        try {
            val size = 128
            val bitmap = createBitmap(size, size)
            val canvas = android.graphics.Canvas(bitmap)

            val avatarBgColor = run {
                val colors = intArrayOf(
                    0xFFE57373.toInt(), 0xFFF06292.toInt(), 0xFFBA68C8.toInt(),
                    0xFF9575CD.toInt(), 0xFF7986CB.toInt(), 0xFF64B5F6.toInt()
                )
                val index = (name.hashCode() and Int.MAX_VALUE) % colors.size
                colors[index]
            }

            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = avatarBgColor
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

            val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 54f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val initial = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
            val yPos = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(initial, size / 2f, yPos, textPaint)

            return bitmap
        } catch (_: Exception) {
            return null
        }
    }
}
