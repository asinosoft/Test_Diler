package com.asinosoft.cdm.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import androidx.core.net.toUri

class CallRingtonePlayer(private val context: Context) {
    private val audioManager: AudioManager? = context.getSystemService(AudioManager::class.java)
    private var ringtone: Ringtone? = null
    private var silenced = false
    private var lastUriString: String? = null
    private var pausedForAnnouncement = false

    val isPlaying: Boolean
        get() = ringtone?.isPlaying == true

    fun start(customUriString: String? = null) {
        if (silenced || ringtone != null) return
        lastUriString = customUriString

        try {
            val uri = if (!customUriString.isNullOrBlank()) {
                customUriString.toUri()
            } else {
                RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            }
            val nextRingtone = RingtoneManager.getRingtone(context, uri)
            nextRingtone.audioAttributes = RINGTONE_ATTRIBUTES
            @Suppress("DEPRECATION")
            nextRingtone.streamType = AudioManager.STREAM_RING
            ringtone = nextRingtone
            audioManager?.mode = AudioManager.MODE_RINGTONE
            ringtone?.play()
        } catch (_: Exception) {
            ringtone = null
        }
    }

    /** Temporarily mutes the melody so the caller announcement can be heard. */
    fun pauseForAnnouncement() {
        val current = ringtone ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            current.volume = 0f
        } else {
            try {
                current.stop()
            } catch (_: Exception) {
                // ignore
            }
            ringtone = null
            pausedForAnnouncement = true
        }
    }

    fun resumeAfterAnnouncement() {
        if (silenced) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone?.volume = 1f
        } else if (pausedForAnnouncement) {
            pausedForAnnouncement = false
            start(lastUriString)
        }
    }

    /** Mute ringtone for the current incoming call (volume / power). */
    fun silence() {
        silenced = true
        stopPlayback()
    }

    fun stop() {
        silenced = false
        stopPlayback()
    }

    private fun stopPlayback() {
        pausedForAnnouncement = false
        try {
            ringtone?.stop()
        } catch (_: Exception) {
            // ignore
        }
        ringtone = null
        try {
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
            // ignore
        }
    }

    companion object {
        val RINGTONE_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
