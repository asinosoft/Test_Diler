package com.asinosoft.cdm.ui.profilecard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.asinosoft.cdm.data.repository.ProfileCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

private const val MAX_SCALE = 5f

/** Draws the card media covering the frame, applies the saved zoom/pan and the dim overlay. */
@Composable
fun ProfileCardMedia(
    card: ProfileCard,
    bitmap: ImageBitmap?,
    modifier: Modifier = Modifier,
    dimmed: Boolean = true,
    videoRange: Pair<Long, Long> = card.trimStartMs to card.trimEndMs,
    videoPlaying: Boolean = true,
    onVideoPosition: ((Long) -> Unit)? = null
) {
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val frameW = constraints.maxWidth.toFloat()
        val frameH = constraints.maxHeight.toFloat()
        val mediaAspect = card.mediaWidth.toFloat() / card.mediaHeight.coerceAtLeast(1)
        val coverByHeight = mediaAspect > frameW / frameH
        val baseW = if (coverByHeight) frameH * mediaAspect else frameW
        val baseH = if (coverByHeight) frameH else frameW / mediaAspect
        val density = LocalDensity.current
        val mediaModifier = Modifier
            .requiredSize(with(density) { baseW.toDp() }, with(density) { baseH.toDp() })
            .graphicsLayer {
                scaleX = card.scale
                scaleY = card.scale
                translationX = card.offsetX * frameW
                translationY = card.offsetY * frameH
            }

        if (card.isVideo) {
            LoopingVideo(
                path = card.mediaPath,
                poster = bitmap,
                modifier = mediaModifier,
                startMs = videoRange.first,
                endMs = videoRange.second,
                playing = videoPlaying,
                onPosition = onVideoPosition
            )
        } else if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = mediaModifier
            )
        }
        if (dimmed && card.dim > 0f) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = card.dim)))
        }
    }
}

/** Pinch-to-zoom and drag for image cards; the frame must keep the aspect used for rendering. */
fun Modifier.profileCardGestures(state: MutableState<ProfileCard>, frameAspect: Float): Modifier =
    pointerInput(frameAspect) {
        detectTransformGestures { _, pan, zoom, _ ->
            val card = state.value
            if (card.isVideo) return@detectTransformGestures
            state.value = card.withTransform(
                frameAspect = frameAspect,
                scale = card.scale * zoom,
                offsetX = card.offsetX + pan.x / size.width,
                offsetY = card.offsetY + pan.y / size.height
            )
        }
    }

/** Clamps zoom and pan so the media always covers a frame with [frameAspect] (width / height). */
fun ProfileCard.withTransform(frameAspect: Float, scale: Float, offsetX: Float, offsetY: Float): ProfileCard {
    val s = scale.coerceIn(1f, MAX_SCALE)
    val mediaAspect = mediaWidth.toFloat() / mediaHeight.coerceAtLeast(1)
    val relW = if (mediaAspect > frameAspect) mediaAspect / frameAspect else 1f
    val relH = if (mediaAspect > frameAspect) 1f else frameAspect / mediaAspect
    val maxX = (relW * s - 1f) / 2f
    val maxY = (relH * s - 1f) / 2f
    return copy(scale = s, offsetX = offsetX.coerceIn(-maxX, maxX), offsetY = offsetY.coerceIn(-maxY, maxY))
}

/** Renders what the circular (square) frame shows into a contact photo bitmap. */
fun cropSquare(source: Bitmap, transform: ProfileCard, outSize: Int = 720): Bitmap {
    val bw = source.width.toFloat()
    val bh = source.height.toFloat()
    val k = max(1f / bw, 1f / bh) * transform.scale
    val half = 0.5f / k
    val cx = bw / 2f - transform.offsetX / k
    val cy = bh / 2f - transform.offsetY / k
    val src = Rect(
        (cx - half).roundToInt().coerceAtLeast(0),
        (cy - half).roundToInt().coerceAtLeast(0),
        (cx + half).roundToInt().coerceAtMost(source.width),
        (cy + half).roundToInt().coerceAtMost(source.height)
    )
    return createBitmap(outSize, outSize).also { out ->
        Canvas(out).drawBitmap(
            source,
            src,
            RectF(0f, 0f, outSize.toFloat(), outSize.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
    }
}

/** Shadow in the opposite tone so card text stays readable over any picture. */
fun profileTextShadow(textColor: Color): Shadow {
    val isDark = textColor.red + textColor.green + textColor.blue < 1.5f
    return Shadow(
        color = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.6f),
        offset = Offset(0f, 2f),
        blurRadius = 10f
    )
}

/**
 * Plays [path] between [startMs] and [endMs] (0 = end of file) in a loop.
 * [poster] stays visible until the first video frame is rendered.
 * When paused, range changes seek so the frame under the dragged trim handle is shown.
 */
@Composable
fun LoopingVideo(
    path: String,
    modifier: Modifier = Modifier,
    poster: ImageBitmap? = null,
    startMs: Long = 0L,
    endMs: Long = 0L,
    playing: Boolean = true,
    onPosition: ((Long) -> Unit)? = null
) {
    key(path) {
        var rendered by remember { mutableStateOf(false) }
        val player = remember { LoopingVideoPlayer(path) { rendered = it } }
        DisposableEffect(player) { onDispose { player.release() } }
        SideEffect {
            player.onPosition = onPosition
            player.configure(startMs, endMs, playing)
        }
        Box(modifier) {
            if (!rendered && poster != null) {
                Image(
                    bitmap = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
            AndroidView(
                factory = { context ->
                    TextureView(context).apply {
                        isOpaque = false
                        surfaceTextureListener = player
                    }
                },
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

data class VideoStrip(val durationMs: Long, val frames: List<ImageBitmap>)

suspend fun loadVideoStrip(path: String, frameCount: Int, frameHeightPx: Int): VideoStrip? =
    withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return@withContext null
            val frames = (0 until frameCount).mapNotNull { index ->
                val timeUs = duration * 1000L * index / frameCount
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { frame ->
                    val width = (frame.width * frameHeightPx / frame.height.toFloat()).roundToInt().coerceAtLeast(1)
                    val scaled = frame.scale(width, frameHeightPx)
                    if (scaled !== frame) frame.recycle()
                    scaled.asImageBitmap()
                }
            }
            VideoStrip(duration, frames)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

/**
 * Muted playback so the card never competes with the ringtone.
 * MediaPlayer release can block for a noticeable time, so it runs off the main thread.
 */
private class LoopingVideoPlayer(
    private val path: String,
    /** true once a frame actually reached the TextureView, false again when its surface is recreated. */
    private val onRenderedChange: (Boolean) -> Unit
) : TextureView.SurfaceTextureListener {
    private var frameShown = false
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var texture: SurfaceTexture? = null
    private var prepared = false
    private var durationMs = 0L
    private var startMs = 0L
    private var endMs = 0L
    private var playing = true
    var onPosition: ((Long) -> Unit)? = null

    /** Trim end that actually cuts the clip; 0 when the clip plays to the file end. */
    private val effectiveEnd: Long
        get() = endMs.takeIf { it > 0 && (durationMs <= 0 || it < durationMs - END_TOLERANCE_MS) } ?: 0L

    private val ticker = Runnable { tick() }
    private var tickPending = false

    private fun tick() {
        tickPending = false
        val mp = player?.takeIf { prepared } ?: return
        val position = mp.currentPosition.toLong()
        val end = effectiveEnd
        if (playing && end > 0 && position >= end) {
            seek(startMs)
            onPosition?.invoke(startMs)
        } else {
            onPosition?.invoke(position)
        }
        scheduleTick(position)
    }

    /** Polls often only while someone shows the position; otherwise wakes up right before the trim end. */
    private fun scheduleTick(position: Long = player?.takeIf { prepared }?.currentPosition?.toLong() ?: 0L) {
        handler.removeCallbacks(ticker)
        tickPending = false
        if (!prepared) return
        val end = effectiveEnd
        val delay = when {
            onPosition != null -> TICK_MS
            playing && end > 0 -> (end - position).coerceIn(MIN_TICK_MS, MAX_TICK_MS)
            else -> return
        }
        tickPending = true
        handler.postDelayed(ticker, delay)
    }

    fun configure(start: Long, end: Long, play: Boolean) {
        val startChanged = start != startMs
        val endChanged = end != endMs
        val playChanged = play != playing
        startMs = start
        endMs = end
        playing = play
        val mp = player?.takeIf { prepared } ?: return
        if (!startChanged && !endChanged && !playChanged) {
            if (onPosition != null && !tickPending) scheduleTick()
            return
        }
        mp.isLooping = startMs == 0L && effectiveEnd == 0L
        when {
            startChanged -> seek(start)
            endChanged && !play && end > 0 -> seek(end)
        }
        if (play && !mp.isPlaying) {
            val position = mp.currentPosition.toLong()
            val cut = effectiveEnd
            if (position < startMs || (cut in 1..position)) seek(startMs)
            mp.start()
        } else if (!play && mp.isPlaying) {
            mp.pause()
        }
        scheduleTick()
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        release()
        frameShown = false
        onRenderedChange(false)
        this.texture = texture
        val target = Surface(texture).also { surface = it }
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                setDataSource(path)
                setSurface(target)
                setVolume(0f, 0f)
                setOnPreparedListener { mp ->
                    if (mp !== player) return@setOnPreparedListener
                    prepared = true
                    durationMs = mp.duration.toLong()
                    mp.isLooping = startMs == 0L && effectiveEnd == 0L
                    if (startMs > 0) seek(startMs)
                    if (playing) mp.start()
                    scheduleTick()
                }
                setOnCompletionListener { mp ->
                    if (mp === player && playing && !mp.isLooping) {
                        seek(startMs)
                        mp.start()
                    }
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return false
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
        if (!frameShown && prepared) {
            frameShown = true
            onRenderedChange(true)
        }
    }

    private fun seek(ms: Long) {
        val mp = player ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mp.seekTo(ms, MediaPlayer.SEEK_CLOSEST)
        } else {
            mp.seekTo(ms.toInt())
        }
    }

    fun release() {
        handler.removeCallbacks(ticker)
        tickPending = false
        prepared = false
        val mp = player
        val target = surface
        val tex = texture
        player = null
        surface = null
        texture = null
        if (mp == null && target == null && tex == null) return
        releaseExecutor.execute {
            runCatching { mp?.release() }
            target?.release()
            tex?.release()
        }
    }

    private companion object {
        const val TICK_MS = 50L
        const val MIN_TICK_MS = 10L
        const val MAX_TICK_MS = 1000L
        const val END_TOLERANCE_MS = 150L
        val releaseExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    }
}
