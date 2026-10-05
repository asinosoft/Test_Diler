package com.asinosoft.cdm.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import androidx.core.graphics.scale
import androidx.core.graphics.get

/** Full-screen call background ("profile card") attached to a contact. */
data class ProfileCard(
    val mediaPath: String,
    val isVideo: Boolean,
    val mediaWidth: Int,
    val mediaHeight: Int,
    val scale: Float = 1f,
    /** Pan as a fraction of the frame size, so it survives different frame sizes of the same aspect. */
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val textColor: Int = DEFAULT_TEXT_COLOR,
    val textSizeSp: Float = DEFAULT_TEXT_SIZE_SP,
    val dim: Float = DEFAULT_DIM,
    val trimStartMs: Long = 0L,
    /** 0 means "until the end of the video". */
    val trimEndMs: Long = 0L
) {
    companion object {
        const val DEFAULT_TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val DARK_TEXT_COLOR = 0xFF111111.toInt()
        const val DEFAULT_TEXT_SIZE_SP = 34f
        const val DEFAULT_DIM = 0.3f
    }
}

object ProfileCardRepository {
    private const val PREFS = "contact_profile_cards"
    private const val KEY_CARD = "card_"
    private const val KEY_NUMBER = "number_"
    private const val STORAGE_DIR = "profile_cards"
    private const val DRAFT_DIR = "profile_card_draft"
    private const val CAPTURE_DIR = "camera"
    private const val MAX_IMAGE_SIDE = 2560
    private const val POSTER_MAX_SIDE = 1280

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Contact id when the contact exists, otherwise its number (for contacts not created yet). */
    fun keyFor(contactId: String, fallbackNumber: String): String =
        contactId.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
            ?: ("number_" + fallbackNumber.filter(Char::isDigit))

    /** Outlives the edit dialog, which closes right after saving. */
    fun saveInBackground(context: Context, key: String, card: ProfileCard?, numbers: List<String>) {
        val appContext = context.applicationContext
        backgroundScope.launch { save(appContext, key, card, numbers) }
    }

    fun find(context: Context, contactId: String?, numbers: List<String>): ProfileCard? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val keys = listOfNotNull(contactId?.takeIf { it.isNotBlank() }) +
                numbers.mapNotNull { normalize(it) }.mapNotNull { prefs.getString(KEY_NUMBER + it, null) }
        return keys.firstNotNullOfOrNull { key ->
            prefs.getString(KEY_CARD + key, null)?.let { parse(it) }?.takeIf { File(it.mediaPath).exists() }
        }
    }

    /** Persists [card] (moving draft media into app storage) or removes the card when null. */
    suspend fun save(context: Context, key: String, card: ProfileCard?, numbers: List<String>) =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val previous = prefs.getString(KEY_CARD + key, null)?.let { parse(it) }
            val stored = card?.let { moveToStorage(context, key, it) }

            if (previous != null && previous.mediaPath != stored?.mediaPath) {
                File(previous.mediaPath).delete()
                posterFile(previous.mediaPath).delete()
            }
            if (stored?.isVideo == true) {
                posterFile(stored.mediaPath).delete()
                runCatching { loadPoster(stored, POSTER_MAX_SIDE)?.recycle() }
            }
            prefs.edit {
                prefs.all.keys
                    .filter { it.startsWith(KEY_NUMBER) && prefs.getString(it, null) == key }
                    .forEach { remove(it) }
                if (stored == null) {
                    remove(KEY_CARD + key)
                } else {
                    putString(KEY_CARD + key, stored.toJson())
                    numbers.mapNotNull { normalize(it) }.forEach { putString(KEY_NUMBER + it, key) }
                }
            }
        }

    fun newCaptureFile(context: Context, extension: String): File =
        File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }
            .resolve("capture_${System.currentTimeMillis()}.$extension")

    fun isVideo(context: Context, uri: Uri): Boolean =
        context.contentResolver.getType(uri)?.startsWith("video/") == true

    /** Copies picked media into a draft file and returns a card with defaults for it. */
    suspend fun importMedia(context: Context, uri: Uri, isVideo: Boolean): ProfileCard? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, DRAFT_DIR).apply { mkdirs() }
                if (isVideo) importVideo(context, uri, dir) else importImage(context, uri, dir)
            } catch (_: Exception) {
                null
            }
        }

    /** The picture itself, or for a video its first (trimmed) frame, cached as a poster next to the file. */
    suspend fun loadBitmap(card: ProfileCard, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (card.isVideo) loadPoster(card, maxSide) else decodeSampled(card.mediaPath, maxSide)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadPoster(card: ProfileCard, maxSide: Int): Bitmap? {
        val poster = posterFile(card.mediaPath)
        if (poster.exists()) decodeSampled(poster.path, maxSide)?.let { return it }
        val frame = videoFrame(card.mediaPath, card.trimStartMs) ?: return null
        val scale = POSTER_MAX_SIDE.toFloat() / max(frame.width, frame.height)
        val scaled = if (scale < 1f) {
            frame.scale((frame.width * scale).toInt(), (frame.height * scale).toInt())
                .also { if (it !== frame) frame.recycle() }
        } else frame
        runCatching { poster.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
        return scaled
    }

    private fun posterFile(mediaPath: String) = File("$mediaPath.poster.jpg")

    private fun decodeSampled(path: String, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        }
        return BitmapFactory.decodeFile(path, options)
    }

    /** Picks white or dark text depending on how bright the upper part of the picture is. */
    fun readableTextColor(bitmap: Bitmap, dim: Float): Int {
        val sample = bitmap.scale(24, 40)
        var sum = 0.0
        val rows = sample.height * 2 / 5
        val columns = sample.width
        for (y in 0 until rows) {
            for (x in 0 until columns) {
                val p = sample[x, y]
                sum += (0.299 * (p shr 16 and 0xFF) + 0.587 * (p shr 8 and 0xFF) + 0.114 * (p and 0xFF)) / 255.0
            }
        }
        if (sample !== bitmap) sample.recycle()
        val luminance = sum / (rows * columns) * (1f - dim)
        return if (luminance > 0.55) ProfileCard.DARK_TEXT_COLOR else ProfileCard.DEFAULT_TEXT_COLOR
    }

    private fun importImage(context: Context, uri: Uri, dir: File): ProfileCard? {
        val bitmap = decodeOriented(context, uri) ?: return null
        val file = dir.resolve("draft_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return ProfileCard(
            mediaPath = file.absolutePath,
            isVideo = false,
            mediaWidth = bitmap.width,
            mediaHeight = bitmap.height,
            textColor = readableTextColor(bitmap, ProfileCard.DEFAULT_DIM)
        ).also { bitmap.recycle() }
    }

    private fun importVideo(context: Context, uri: Uri, dir: File): ProfileCard? {
        val file = dir.resolve("draft_${System.currentTimeMillis()}.mp4")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        } ?: return null

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (w <= 0 || h <= 0) {
                file.delete()
                return null
            }
            val rotated = rotation % 180 != 0
            val textColor = retriever.getFrameAtTime(0)
                ?.let { frame -> readableTextColor(frame, ProfileCard.DEFAULT_DIM).also { frame.recycle() } }
                ?: ProfileCard.DEFAULT_TEXT_COLOR
            ProfileCard(
                mediaPath = file.absolutePath,
                isVideo = true,
                mediaWidth = if (rotated) h else w,
                mediaHeight = if (rotated) w else h,
                textColor = textColor
            )
        } finally {
            retriever.release()
        }
    }

    private fun decodeOriented(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(sampleSize(info.size.width, info.size.height, MAX_IMAGE_SIDE))
            }
        }
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_SIDE)
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val degrees = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun videoFrame(path: String, atMs: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.getFrameAtTime(atMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
        } finally {
            retriever.release()
        }
    }

    private fun moveToStorage(context: Context, key: String, card: ProfileCard): ProfileCard {
        val dir = File(context.filesDir, STORAGE_DIR).apply { mkdirs() }
        val source = File(card.mediaPath)
        if (source.parentFile == dir) return card
        val safeKey = key.replace(Regex("[^A-Za-z0-9_]"), "_")
        val target = dir.resolve("${safeKey}_${System.currentTimeMillis()}.${source.extension}")
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
        return card.copy(mediaPath = target.absolutePath)
    }

    private fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    private fun normalize(number: String): String? =
        number.filter(Char::isDigit).takeLast(10).takeIf { it.length >= 5 }

    private fun ProfileCard.toJson(): String = JSONObject()
        .put("path", mediaPath)
        .put("video", isVideo)
        .put("w", mediaWidth)
        .put("h", mediaHeight)
        .put("scale", scale.toDouble())
        .put("x", offsetX.toDouble())
        .put("y", offsetY.toDouble())
        .put("color", textColor)
        .put("size", textSizeSp.toDouble())
        .put("dim", dim.toDouble())
        .put("ts", trimStartMs)
        .put("te", trimEndMs)
        .toString()

    private fun parse(json: String): ProfileCard? = try {
        val o = JSONObject(json)
        ProfileCard(
            mediaPath = o.getString("path"),
            isVideo = o.getBoolean("video"),
            mediaWidth = o.getInt("w"),
            mediaHeight = o.getInt("h"),
            scale = o.optDouble("scale", 1.0).toFloat(),
            offsetX = o.optDouble("x", 0.0).toFloat(),
            offsetY = o.optDouble("y", 0.0).toFloat(),
            textColor = o.optInt("color", ProfileCard.DEFAULT_TEXT_COLOR),
            textSizeSp = o.optDouble("size", ProfileCard.DEFAULT_TEXT_SIZE_SP.toDouble()).toFloat(),
            dim = o.optDouble("dim", ProfileCard.DEFAULT_DIM.toDouble()).toFloat(),
            trimStartMs = o.optLong("ts", 0L),
            trimEndMs = o.optLong("te", 0L)
        )
    } catch (_: Exception) {
        null
    }
}
