package com.asinosoft.dialer.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

/**
 * Гасит системные/OEM-уведомления о пропущенном (в т.ч. отложенные Samsung-напоминания).
 * Нужен доступ «Уведомления» / Notification access в настройках системы.
 */
class MissedCallNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        cancelMatching(activeNotifications)
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (shouldCancel(sbn)) {
            cancelNotification(sbn.key)
        }
    }

    fun cancelActiveSystemMissedCalls() {
        cancelMatching(activeNotifications)
    }

    private fun cancelMatching(notifications: Array<StatusBarNotification>?) {
        notifications?.forEach { sbn ->
            if (shouldCancel(sbn)) {
                cancelNotification(sbn.key)
            }
        }
    }

    private fun shouldCancel(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName) return false

        val notification = sbn.notification ?: return false
        if (notification.category == Notification.CATEGORY_MISSED_CALL) return true

        if (sbn.packageName !in SYSTEM_DIALER_PACKAGES) return false

        // Не трогаем текущий/входящий вызов
        if (notification.category == Notification.CATEGORY_CALL) return false

        val title = notification.extras
            ?.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            .orEmpty()
        val text = notification.extras
            ?.getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            .orEmpty()
        val bigText = notification.extras
            ?.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?.toString()
            .orEmpty()
        val blob = "$title $text $bigText".lowercase()

        return MISSED_KEYWORDS.any { it in blob }
    }

    companion object {
        @Volatile
        private var instance: MissedCallNotificationListener? = null

        private val SYSTEM_DIALER_PACKAGES = setOf(
            "com.android.server.telecom",
            "com.android.phone",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.app.telephonyui",
            "com.samsung.android.phone",
            "com.sec.android.app.clockpackage"
        )

        private val MISSED_KEYWORDS = listOf(
            "пропущ",
            "missed call",
            "missed calls",
            "вызов пропущ",
            "неотвечен"
        )

        fun isEnabled(context: android.content.Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)

        fun cancelActiveIfConnected() {
            instance?.cancelActiveSystemMissedCalls()
        }
    }
}
