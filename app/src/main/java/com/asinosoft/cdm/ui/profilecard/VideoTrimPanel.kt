package com.asinosoft.cdm.ui.profilecard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asinosoft.cdm.R
import java.util.Locale
import kotlin.math.roundToInt

private const val STRIP_FRAMES = 8
private const val MIN_RANGE_MS = 1_000L
private val HANDLE_WIDTH = 14.dp

/**
 * Samsung-like trimmer: frame strip with start/end handles, playhead, play/pause and time.
 * [range] is live so the preview above plays exactly the selected part.
 */
@Composable
fun VideoTrimPanel(
    path: String,
    range: Pair<Long, Long>,
    positionMs: Long,
    playing: Boolean,
    onPlayingChange: (Boolean) -> Unit,
    onRangeChange: (Pair<Long, Long>) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val frameHeightPx = with(LocalDensity.current) { 56.dp.roundToPx() }
    var strip by remember(path) { mutableStateOf<VideoStrip?>(null) }
    LaunchedEffect(path) {
        strip = loadVideoStrip(path, STRIP_FRAMES, frameHeightPx)
        val duration = strip?.durationMs ?: return@LaunchedEffect
        if (range.second <= 0L || range.second > duration) onRangeChange(range.first to duration)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color(0xF21E1E1E)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
            Spacer(Modifier.height(16.dp))

            val current = strip
            if (current != null && range.second > 0L) {
                TrimStrip(current, range, positionMs, onRangeChange)
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                )
            }

            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.12f),
                onClick = { onPlayingChange(!playing) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    val elapsed = (positionMs - range.first).coerceIn(0L, (range.second - range.first).coerceAtLeast(0L))
                    Text(
                        text = "${formatTime(elapsed)} / ${formatTime(range.second - range.first)}",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel), color = Color.White, fontSize = 17.sp)
                }
                TextButton(onClick = onDone, enabled = range.second > 0L) {
                    Text(stringResource(R.string.action_done), color = Color.White, fontSize = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun TrimStrip(
    strip: VideoStrip,
    range: Pair<Long, Long>,
    positionMs: Long,
    onRangeChange: (Pair<Long, Long>) -> Unit
) {
    val currentRange by rememberUpdatedState(range)
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val handlePx = with(density) { HANDLE_WIDTH.toPx() }
        val trackPx = (constraints.maxWidth - handlePx * 2).coerceAtLeast(1f)
        val duration = strip.durationMs.coerceAtLeast(1L)
        fun msToPx(ms: Long) = handlePx + ms.toFloat() / duration * trackPx
        val pxPerMs = trackPx / duration

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = HANDLE_WIDTH)
                .clip(RoundedCornerShape(4.dp))
        ) {
            strip.frames.forEach { frame ->
                Image(
                    bitmap = frame,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        val startPx = msToPx(range.first)
        val endPx = msToPx(range.second)
        // Dim the parts that are cut off.
        Box(
            Modifier
                .offset { IntOffset(handlePx.roundToInt(), 0) }
                .width(with(density) { (startPx - handlePx).coerceAtLeast(0f).toDp() })
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.6f))
        )
        Box(
            Modifier
                .offset { IntOffset(endPx.roundToInt(), 0) }
                .width(with(density) { (handlePx + trackPx - endPx).coerceAtLeast(0f).toDp() })
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.6f))
        )
        Box(
            Modifier
                .offset { IntOffset(startPx.roundToInt(), 0) }
                .width(with(density) { (endPx - startPx).coerceAtLeast(0f).toDp() })
                .fillMaxHeight()
                .border(2.dp, Color.White.copy(alpha = 0.9f))
        )

        if (positionMs in range.first..range.second) {
            Box(
                Modifier
                    .offset { IntOffset((msToPx(positionMs) - 1.5f * density.density).roundToInt(), 0) }
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Color.White)
            )
        }

        TrimHandle(
            offsetPx = startPx - handlePx,
            onDrag = { dx ->
                val (start, end) = currentRange
                val newStart = (start + (dx / pxPerMs).toLong()).coerceIn(0L, end - MIN_RANGE_MS)
                onRangeChange(newStart to end)
            }
        )
        TrimHandle(
            offsetPx = endPx,
            onDrag = { dx ->
                val (start, end) = currentRange
                val newEnd = (end + (dx / pxPerMs).toLong()).coerceIn(start + MIN_RANGE_MS, duration)
                onRangeChange(start to newEnd)
            }
        )
    }
}

@Composable
private fun TrimHandle(offsetPx: Float, onDrag: (Float) -> Unit) {
    val drag by rememberUpdatedState(onDrag)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset { IntOffset(offsetPx.roundToInt(), 0) }
            .width(HANDLE_WIDTH)
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.9f))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dx ->
                    change.consume()
                    drag(dx)
                }
            }
    ) {
        Box(
            Modifier
                .size(width = 2.dp, height = 18.dp)
                .background(Color(0xFF1E1E1E))
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) + 500L) / 1000L
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
