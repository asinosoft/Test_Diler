package com.asinosoft.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.asinosoft.dialer.ui.theme.MissedRed

/**
 * Compact Samsung One UI style popup menu.
 * @param pressOffset offset inside the anchor (e.g. long-press point).
 * @param alignEnd if true, opens aligned to the end (right) of the anchor — for ⋮ buttons.
 * @param preferBelowAnchor if true, place below/above the anchor; if false, at [pressOffset].
 */
@Composable
fun OneUiPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    pressOffset: IntOffset = IntOffset.Zero,
    alignEnd: Boolean = false,
    preferBelowAnchor: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return

    val density = LocalDensity.current
    val marginPx = with(density) { 8.dp.roundToPx() }
    val positionProvider = remember(pressOffset, marginPx, alignEnd, preferBelowAnchor) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                var x = if (alignEnd) {
                    anchorBounds.right - popupContentSize.width + pressOffset.x
                } else {
                    anchorBounds.left + pressOffset.x
                }
                var y = if (preferBelowAnchor) {
                    val below = anchorBounds.bottom + 4
                    val above = anchorBounds.top - popupContentSize.height - 4
                    when {
                        below + popupContentSize.height <= windowSize.height - marginPx -> below
                        above >= marginPx -> above
                        else -> (windowSize.height - popupContentSize.height - marginPx)
                            .coerceAtLeast(marginPx)
                    }
                } else {
                    anchorBounds.top + pressOffset.y
                }

                if (x + popupContentSize.width > windowSize.width - marginPx) {
                    x = windowSize.width - popupContentSize.width - marginPx
                }
                if (x < marginPx) x = marginPx

                if (y + popupContentSize.height > windowSize.height - marginPx) {
                    y = if (!preferBelowAnchor) {
                        (anchorBounds.top + pressOffset.y - popupContentSize.height)
                            .coerceAtLeast(marginPx)
                    } else {
                        windowSize.height - popupContentSize.height - marginPx
                    }
                }
                if (y < marginPx) y = marginPx

                return IntOffset(x, y)
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .padding(vertical = 4.dp),
                content = content
            )
        }
    }
}

@Composable
fun OneUiPopupMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
    iconBackground: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
    destructive: Boolean = false
) {
    val resolvedLabel = if (destructive) MissedRed else labelColor
    val resolvedTint = if (destructive) MissedRed else iconTint
    val resolvedBg = if (destructive) MissedRed.copy(alpha = 0.10f) else iconBackground

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(resolvedBg)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = resolvedTint,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = resolvedLabel,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun OneUiPopupMenuCustomItem(
    label: String,
    onClick: () -> Unit,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    leading: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = labelColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun OneUiPopupMenuPainterItem(
    painter: Painter,
    label: String,
    onClick: () -> Unit,
    iconBackground: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(iconBackground)
        ) {
            Icon(
                painter = painter,
                contentDescription = label,
                tint = Color.Unspecified,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun OneUiPopupMenuDivider() {
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    )
}
