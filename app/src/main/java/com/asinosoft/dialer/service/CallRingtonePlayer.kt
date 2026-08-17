package com.asinosoft.dialer.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager

class CallRingtonePlayer(private val context: Context) {
    private val audioManager: AudioManager? = context.getSystemService(AudioManager::class.java)
    private var ringtone: Ringtone? = null

    fun start() {
        if (ringtone != null) return

        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            val nextRingtone = RingtoneManager.getRingtone(context, uri)
            nextRingtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            nextRingtone.streamType = AudioManager.STREAM_RING
            ringtone = nextRingtone
            audioManager?.mode = AudioManager.MODE_RINGTONE
            ringtone?.play()
        } catch (_: Exception) {
            ringtone = null
        }
    }

    fun stop() {
        ringtone?.stop()
        ringtone = null
        try {
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
            // ignore
        }
    }
}
