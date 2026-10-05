package com.asinosoft.cdm.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * Plays the melody, then speaks the contact name (or the number) in the Bluetooth
 * headset only and resumes the melody. Never falls back to the phone speaker.
 */
class IncomingCallAnnouncer(private val context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var voiceLinkOpened = false
    private var previousAudioMode = AudioManager.MODE_RINGTONE

    fun start(rawNumber: String, player: CallRingtonePlayer) {
        if (rawNumber.isBlank() || job?.isActive == true) return
        Log.d(TAG, "announcement scheduled")

        job = scope.launch {
            val file = withContext(Dispatchers.IO) { resolveSpokenText(rawNumber) }?.let { text ->
                createEngine()?.let { engine ->
                    withTimeoutOrNull(SYNTHESIS_TIMEOUT_MS.milliseconds) { synthesizeToFile(engine, text) }
                }
            }
            if (file == null) {
                Log.w(TAG, "speech synthesis failed")
                return@launch
            }
            if (findBluetoothOutput() == null) {
                Log.w(TAG, "no Bluetooth output device")
                return@launch
            }

            player.pauseForAnnouncement()
            try {
                withTimeoutOrNull(PLAYBACK_TIMEOUT_MS.milliseconds) {
                    // Headsets usually ignore A2DP while a call rings, so prefer the voice (SCO) link.
                    val scoDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        openVoiceLink()
                    } else {
                        null
                    }
                    val device = scoDevice ?: findBluetoothOutput()
                    if (device == null) {
                        Log.w(TAG, "no Bluetooth output device")
                        return@withTimeoutOrNull
                    }
                    Log.d(TAG, "target device: ${device.type} ${device.productName}")
                    playOnDevice(file, device)
                }
            } finally {
                releasePlayer()
                closeVoiceLink()
                player.resumeAfterAnnouncement()
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        releasePlayer()
        closeVoiceLink()
        tts?.let {
            try {
                it.stop()
                it.shutdown()
            } catch (_: Exception) {
                // ignore
            }
        }
        tts = null
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun openVoiceLink(): AudioDeviceInfo? {
        val am = audioManager ?: return null
        val device = am.availableCommunicationDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        } ?: return null

        previousAudioMode = am.mode
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        voiceLinkOpened = true
        if (!am.setCommunicationDevice(device)) {
            Log.w(TAG, "setCommunicationDevice rejected")
            closeVoiceLink()
            return null
        }

        for (attempt in 0 until VOICE_LINK_ATTEMPTS) {
            if (am.communicationDevice?.id == device.id) {
                // Give the headset time to actually open the SCO audio link.
                delay(VOICE_LINK_SETTLE_MS.milliseconds)
                Log.d(TAG, "voice link ready after #$attempt")
                return device
            }
            delay(ROUTING_CHECK_INTERVAL_MS.milliseconds)
        }
        Log.w(TAG, "voice link not established")
        closeVoiceLink()
        return null
    }

    private fun closeVoiceLink() {
        if (!voiceLinkOpened) return
        voiceLinkOpened = false
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.clearCommunicationDevice()
            am.mode = previousAudioMode
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun findBluetoothOutput(): AudioDeviceInfo? {
        val outputs = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: return null
        return BLUETOOTH_OUTPUT_PRIORITY.firstNotNullOfOrNull { type ->
            outputs.firstOrNull { it.type == type }
        }
    }

    private suspend fun playOnDevice(file: File, device: AudioDeviceInfo) =
        suspendCancellableCoroutine { cont ->
            val mp = MediaPlayer()
            mediaPlayer = mp
            try {
                mp.setAudioAttributes(attributesFor(device))
                mp.setDataSource(file.absolutePath)
                if (Build.VERSION.SDK_INT >= 28) mp.setPreferredDevice(device)
                mp.setVolume(0f, 0f)
                mp.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                mp.setOnErrorListener { _, _, _ ->
                    if (cont.isActive) cont.resume(Unit)
                    true
                }
                mp.setOnPreparedListener {
                    it.isLooping = true
                    it.start()
                    if (Build.VERSION.SDK_INT >= 28) scope.launch {
                        // Only unmute once the system confirms Bluetooth routing.
                        var routed: AudioDeviceInfo? = null
                        for (attempt in 0 until ROUTING_CHECK_ATTEMPTS) {
                            if (!cont.isActive || mediaPlayer !== it) return@launch
                            routed = it.routedDevice
                            Log.d(TAG, "routing check #$attempt: ${routed?.type} ${routed?.productName}")
                            if (routed?.type in BLUETOOTH_OUTPUT_PRIORITY) break
                            delay(ROUTING_CHECK_INTERVAL_MS.milliseconds)
                        }
                        if (!cont.isActive || mediaPlayer !== it) return@launch
                        if (routed?.type in BLUETOOTH_OUTPUT_PRIORITY) {
                            it.isLooping = false
                            it.seekTo(0)
                            it.setVolume(1f, 1f)
                            Log.d(TAG, "playing announcement on ${routed?.productName}")
                        } else {
                            Log.w(TAG, "not routed to Bluetooth (${routed?.type}), skipping")
                            cont.resume(Unit)
                        }
                    }
                }
                mp.prepareAsync()
            } catch (e: Exception) {
                Log.w(TAG, "playback failed", e)
                if (cont.isActive) cont.resume(Unit)
            }
            cont.invokeOnCancellation { releasePlayer() }
        }

    private fun attributesFor(device: AudioDeviceInfo): AudioAttributes {
        val usage = if (voiceLinkOpened || device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            AudioAttributes.USAGE_VOICE_COMMUNICATION
        } else {
            AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
        }
        return AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }

    private fun releasePlayer() {
        mediaPlayer?.let {
            try {
                it.stop()
            } catch (_: Exception) {
                // ignore
            }
            it.release()
        }
        mediaPlayer = null
    }

    private suspend fun createEngine(): TextToSpeech? = suspendCancellableCoroutine { cont ->
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context.applicationContext) { status ->
            if (!cont.isActive) return@TextToSpeech
            if (status == TextToSpeech.SUCCESS) {
                engine.language = Locale.getDefault()
                tts = engine
                cont.resume(engine)
            } else {
                engine.shutdown()
                cont.resume(null)
            }
        }
        cont.invokeOnCancellation { engine.shutdown() }
    }

    private suspend fun synthesizeToFile(engine: TextToSpeech, text: String): File? =
        suspendCancellableCoroutine { cont ->
            val file = File(context.cacheDir, AUDIO_FILE_NAME)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    if (cont.isActive) cont.resume(file.takeIf { it.length() > 0 })
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }
            })
            val result = engine.synthesizeToFile(text, null, file, UTTERANCE_ID)
            if (result != TextToSpeech.SUCCESS && cont.isActive) cont.resume(null)
            cont.invokeOnCancellation { engine.stop() }
        }

    private fun resolveSpokenText(rawNumber: String): String? {
        lookupContactName(rawNumber)?.let { return it }
        val digits = rawNumber.filter { it.isDigit() || it == '+' }
        if (digits.isEmpty()) return null
        // Separate digits so TTS reads the number digit by digit.
        return digits.toList().joinToString(" ")
    }

    private fun lookupContactName(rawNumber: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(rawNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (idx == -1) null else cursor.getString(idx)?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val SYNTHESIS_TIMEOUT_MS = 5_000
        const val PLAYBACK_TIMEOUT_MS = 10_000
        const val ROUTING_CHECK_ATTEMPTS = 30
        const val ROUTING_CHECK_INTERVAL_MS = 50
        const val TAG = "CallAnnouncer"
        const val VOICE_LINK_ATTEMPTS = 40
        const val VOICE_LINK_SETTLE_MS = 400
        const val UTTERANCE_ID = "incoming_call_announcement"
        const val AUDIO_FILE_NAME = "incoming_call_announcement.wav"

        val BLUETOOTH_OUTPUT_PRIORITY = listOf(
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        )
    }
}
