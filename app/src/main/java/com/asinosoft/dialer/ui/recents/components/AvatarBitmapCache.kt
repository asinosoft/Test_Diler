package com.asinosoft.dialer.ui.recents.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

internal object AvatarBitmapCache {
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt().coerceIn(2048, 8192)
    private val cache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            synchronized(AvatarBitmapCache) {
                imageCache.remove(key)
            }
        }
    }
    private val imageCache = LruCache<String, ImageBitmap>(120)

    private val decodeMutex = Mutex()
    private var activeDecodes = 0
    private const val MAX_PARALLEL_DECODES = 2

    @Synchronized
    fun get(key: String): Bitmap? = cache.get(key)?.takeUnless { it.isRecycled }

    @Synchronized
    fun getImageBitmap(key: String): ImageBitmap? {
        imageCache.get(key)?.let { return it }
        val bitmap = get(key) ?: return null
        return bitmap.asImageBitmap().also { imageCache.put(key, it) }
    }

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
        imageCache.put(key, bitmap.asImageBitmap())
    }

    suspend fun <T> withDecodeSlot(block: suspend () -> T): T {
        while (true) {
            val acquired = decodeMutex.withLock {
                if (activeDecodes < MAX_PARALLEL_DECODES) {
                    activeDecodes++
                    true
                } else false
            }
            if (acquired) break
            kotlinx.coroutines.delay(16.milliseconds)
        }
        try {
            return block()
        } finally {
            decodeMutex.withLock { activeDecodes-- }
        }
    }

    /** Warm cache before first frame so list does not jank while decoding. */
    suspend fun prefetch(context: Context, uris: Collection<String?>, targetPx: Int) {
        val unique = uris.filterNotNull().filter { it.isNotBlank() }.distinct()
        if (unique.isEmpty()) return
        withContext(Dispatchers.IO) {
            for (uri in unique) {
                if (get(uri) != null) continue
                withDecodeSlot {
                    try {
                        decodeSampledBitmap(context, uri, targetPx)?.let { put(uri, it) }
                    } catch (_: Exception) {
                        // ignore
                    }
                }
            }
        }
    }
}

internal fun decodeSampledBitmap(context: Context, photoUri: String, targetPx: Int): Bitmap? {
    val uri = photoUri.toUri()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetPx)
    }
    return context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    }
}

private fun calculateInSampleSize(width: Int, height: Int, targetPx: Int): Int {
    var inSampleSize = 1
    val w = width.coerceAtLeast(1)
    val h = height.coerceAtLeast(1)
    if (h > targetPx || w > targetPx) {
        val halfH = h / 2
        val halfW = w / 2
        while (halfH / inSampleSize >= targetPx && halfW / inSampleSize >= targetPx) {
            inSampleSize *= 2
        }
    }
    return inSampleSize.coerceAtLeast(1)
}
