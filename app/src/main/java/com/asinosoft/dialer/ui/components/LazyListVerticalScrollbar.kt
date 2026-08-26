package com.asinosoft.dialer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Thin side scrollbar for [LazyListState].
 * Visible while scrolling/dragging, fades out when idle.
 */
@Composable
fun LazyListVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thickness: Dp = 4.dp,
    hitAreaWidth: Dp = 24.dp,
    thumbMinHeight: Dp = 48.dp,
    hideDelayMs: Long = 900L
) {
    // Stabilize item-size estimate across frames (short date headers must not double the thumb)
    val stableItemSize = remember { mutableFloatStateOf(0f) }

    val scrollMetrics by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            val canScroll = listState.canScrollForward || listState.canScrollBackward
            if (!canScroll || totalItems == 0 || visibleItems.isEmpty()) {
                return@derivedStateOf null
            }

            val viewportSize =
                (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
                    .toFloat()
                    .coerceAtLeast(1f)

            val rawAverage = stableAverageItemSize(visibleItems.map { it.size })
            val previous = stableItemSize.floatValue
            val averageItemSize = when {
                previous <= 0f -> rawAverage
                // Soften spikes from short headers / tall favorite rows
                kotlin.math.abs(rawAverage - previous) / previous > 0.28f ->
                    previous * 0.9f + rawAverage * 0.1f
                else -> previous * 0.75f + rawAverage * 0.25f
            }

            val estimatedContentSize = averageItemSize * totalItems
            if (estimatedContentSize <= viewportSize) return@derivedStateOf null

            val firstIndex = listState.firstVisibleItemIndex
            val firstOffset = listState.firstVisibleItemScrollOffset.toFloat()
            val scrollOffset = firstIndex * averageItemSize + firstOffset
            val maxScroll = (estimatedContentSize - viewportSize).coerceAtLeast(1f)
            val listScrollFraction = (scrollOffset / maxScroll).coerceIn(0f, 1f)

            // Thumb height from item count ratio — independent of pixel-size spikes
            val visibleCount = visibleItems.size.coerceAtLeast(1)
            val thumbHeightFraction =
                (visibleCount.toFloat() / totalItems.toFloat()).coerceIn(0.06f, 1f)

            ScrollbarMetrics(
                totalItems = totalItems,
                averageItemSize = averageItemSize,
                listScrollFraction = listScrollFraction,
                thumbHeightFraction = thumbHeightFraction
            )
        }
    }

    val metrics = scrollMetrics ?: return

    // Commit smoothed size outside derivedStateOf snapshot write restrictions
    LaunchedEffect(metrics.averageItemSize) {
        stableItemSize.floatValue = metrics.averageItemSize
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var showScrollbar by remember { mutableStateOf(false) }

    val scrollFraction = if (isDragging) dragFraction else metrics.listScrollFraction

    val isScrolling = listState.isScrollInProgress || isDragging
    LaunchedEffect(isScrolling) {
        if (isScrolling) {
            showScrollbar = true
        } else {
            delay(hideDelayMs.milliseconds)
            showScrollbar = false
        }
    }

    val targetAlpha = when {
        !showScrollbar -> 0f
        isDragging -> 0.85f
        else -> 0.65f
    }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = if (showScrollbar) 120 else 280),
        label = "scrollbarAlpha"
    )

    val density = LocalDensity.current
    val thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    val visualThickness = if (isDragging) thickness + 2.dp else thickness

    val scope = rememberCoroutineScope()
    val totalItemsState = rememberUpdatedState(metrics.totalItems)
    val listStateRef = rememberUpdatedState(listState)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 4.dp)
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val thumbMinHeightPx = with(density) { thumbMinHeight.toPx() }
        val thumbHeightPx = (trackHeightPx * metrics.thumbHeightFraction)
            .coerceIn(thumbMinHeightPx, trackHeightPx)
        val thumbOffsetPx = (trackHeightPx - thumbHeightPx) * scrollFraction

        val thumbHeightState = rememberUpdatedState(thumbHeightPx)
        val trackHeightState = rememberUpdatedState(trackHeightPx)

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(hitAreaWidth)
                .fillMaxHeight()
                // Stable key — do not recreate gesture detector mid-drag
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()

                        val trackH = trackHeightState.value.coerceAtLeast(1f)
                        val thumbH = thumbHeightState.value
                        val usable = (trackH - thumbH).coerceAtLeast(1f)

                        fun fractionFromY(y: Float): Float =
                            ((y - thumbH / 2f) / usable).coerceIn(0f, 1f)

                        fun applyFraction(fraction: Float) {
                            dragFraction = fraction
                            val lastIndex = (totalItemsState.value - 1).coerceAtLeast(0)
                            val targetIndex = (lastIndex * fraction).roundToInt()
                                .coerceIn(0, lastIndex)
                            scope.launch {
                                listStateRef.value.scrollToItem(targetIndex)
                            }
                        }

                        isDragging = true
                        showScrollbar = true
                        try {
                            applyFraction(fractionFromY(down.position.y))

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: break

                                if (change.changedToUp()) {
                                    change.consume()
                                    break
                                }

                                // Keep tracking even if finger slides left/right off the bar
                                if (change.positionChange() != Offset.Zero) {
                                    change.consume()
                                    applyFraction(fractionFromY(change.position.y))
                                }
                            }
                        } finally {
                            isDragging = false
                        }
                    }
                }
        ) {
            if (alpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 3.dp)
                        .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                        .width(visualThickness)
                        .height(with(density) { thumbHeightPx.toDp() })
                        .clip(RoundedCornerShape(50))
                        .background(thumbColor)
                )
            }
        }
    }
}

private data class ScrollbarMetrics(
    val totalItems: Int,
    val averageItemSize: Float,
    val listScrollFraction: Float,
    val thumbHeightFraction: Float
)

/** Median of typical row sizes; short headers are filtered out. */
private fun stableAverageItemSize(sizes: List<Int>): Float {
    if (sizes.isEmpty()) return 1f
    val maxSize = sizes.max().toFloat().coerceAtLeast(1f)
    val typical = sizes.filter { it >= maxSize * 0.45f }.ifEmpty { sizes }
    val sorted = typical.sorted()
    return sorted[sorted.size / 2].toFloat().coerceAtLeast(1f)
}
