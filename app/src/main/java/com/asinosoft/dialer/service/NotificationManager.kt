package com.asinosoft.dialer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Context.TELECOM_SERVICE
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.asinosoft.dialer.MainActivity
import com.asinosoft.dialer.R
import com.asinosoft.dialer.data.model.CallState
import com.asinosoft.dialer.ui.incall.InCallActivity
import com.asinosoft.dialer.util.PhoneNumberHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationManager(val service: Service) {
    companion object {
        const val CHANNEL_ID = "incall_service_channel"
        const val MISSED_CHANNEL_ID = "missed_call_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_ANSWER = "com.asinosoft.dialer.ACTION_ANSWER"
        const val ACTION_DISCONNECT = "com.asinosoft.dialer.ACTION_DISCONNECT"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    init {
        createNotificationChannel()
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
            val manager = service.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun hideNotification() {
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
    }

    fun showCallNotification(call: CallState) {
        serviceScope.launch {
            var contactName: String? = null
            var contactBitmap: Bitmap? = null

            if (call.rawNumber.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    try {
                        val uri = Uri.withAppendedPath(
                            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                            Uri.encode(call.rawNumber)
                        )
                        val cursor = service.contentResolver.query(
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
                                        service.contentResolver.openInputStream(photoUriStr.toUri())
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

            val callerName = contactName ?: call.displayName.ifBlank { PhoneNumberHelper.format(call.rawNumber) }

            val callerPerson = Person.Builder()
                .setName(callerName)
                .setImportant(true)
                .build()

            val openActivityIntent = Intent(service, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                service,
                0,
                openActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val answerIntent =
                Intent(service, CallService::class.java).apply { action = ACTION_ANSWER }
            val answerPendingIntent = PendingIntent.getService(
                service,
                10,
                answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val disconnectIntent = Intent(service, CallService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            val disconnectPendingIntent = PendingIntent.getService(
                service,
                11,
                disconnectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val connectTime = call.connectTimeMillis ?: System.currentTimeMillis()
            val isRinging = call.state == Call.STATE_RINGING
            val isCallActive = call.state == Call.STATE_ACTIVE

            val circularAvatar: Bitmap? = if (contactBitmap != null) {
                getCircularBitmap(contactBitmap)
            } else {
                createRoundAvatarBitmap(callerName)
            }

            if (isRinging) {
                val notificationBuilder = NotificationCompat.Builder(service,CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_phone_white)
                    .setLargeIcon(circularAvatar)
                    .setStyle(
                        NotificationCompat.CallStyle.forIncomingCall(
                            callerPerson,
                            disconnectPendingIntent,
                            answerPendingIntent
                        )
                            // CallStyle тонирует verificationIcon в mono — цветная SIM только на large icon
                            .setVerificationText("SIM ${call.simNumber}")
                    )
                    .setContentIntent(openPendingIntent)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setShowWhen(false)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setSound(null)

                val notification = notificationBuilder.build()
                service.startForeground(NOTIFICATION_ID, notification)
            } else {
                val notificationBuilder = NotificationCompat.Builder(service, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_phone_white)
                    .setLargeIcon(circularAvatar)
                    .setStyle(
                        NotificationCompat.CallStyle.forOngoingCall(
                            callerPerson,
                            disconnectPendingIntent
                        )
                            .setVerificationText("SIM ${call.simNumber}")
                    )
                    .setContentIntent(openPendingIntent)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setUsesChronometer(isCallActive)
                    .setWhen(if (isCallActive && connectTime > 0) connectTime else System.currentTimeMillis())
                    .setShowWhen(false)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setSound(null)

                val notification = notificationBuilder.build()
                service.startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    fun showMissedCallNotification(rawNumber: String) {
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
                        val cursor = service.contentResolver.query(
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
                                        service.contentResolver.openInputStream(photoUriStr.toUri())
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

            val notificationManager = service.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

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

            val formattedNumber = PhoneNumberHelper.format(rawNumber)
            val title = "Пропущенный вызов"
            val contactDisplayName = contactName ?: formattedNumber

            val appIntent = Intent(service, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val appPendingIntent = PendingIntent.getActivity(
                service,
                0,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val callBackIntent =
                Intent(Intent.ACTION_CALL, "tel:${Uri.encode(rawNumber)}".toUri()).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            val callBackPendingIntent = PendingIntent.getActivity(
                service,
                1,
                callBackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val smsIntent =
                Intent(Intent.ACTION_SENDTO, "smsto:${Uri.encode(rawNumber)}".toUri()).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            val smsPendingIntent = PendingIntent.getActivity(
                service,
                2,
                smsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()

            val notificationBuilder =
                NotificationCompat.Builder(service, MISSED_CHANNEL_ID)
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

            val circularAvatar: Bitmap? = if (contactBitmap != null) {
                getCircularBitmap(contactBitmap)
            } else {
                createRoundAvatarBitmap(contactDisplayName)
            }

            if (circularAvatar != null) {
                notificationBuilder.setLargeIcon(circularAvatar)
            }

            notificationManager.notify(notificationId, notificationBuilder.build())
            suppressSystemMissedCallNotification(service, rawNumber)
        }
    }

    private fun suppressSystemMissedCallNotification(service:Service, rawNumber: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val telecomManager =
                    service.getSystemService(TELECOM_SERVICE) as? android.telecom.TelecomManager
                @Suppress("MissingPermission")
                telecomManager?.cancelMissedCallsNotification()
            }
        } catch (_: Exception) {
            // ignore
        }

        try {
            if (ContextCompat.checkSelfPermission(
                    service,
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
                service.contentResolver.insert(android.provider.CallLog.Calls.CONTENT_URI, values)
            }
        } catch (_: Exception) {
            // ignore
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
