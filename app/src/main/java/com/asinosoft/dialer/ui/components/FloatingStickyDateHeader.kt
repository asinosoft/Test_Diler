package com.asinosoft.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asinosoft.dialer.ui.theme.SamsungGreen

/** Плавающая дата поверх списка (слева) + плавающий стик фильтра (справа). */
@Composable
fun FloatingStickyDateHeader(
    text: String,
    modifier: Modifier = Modifier,
    startPadding: Dp = 4.dp,
    endPadding: Dp = 4.dp,
    topPadding: Dp = 6.dp,
    onFilterClick: (() -> Unit)? = null,
    isFilterActive: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPadding, end = endPadding, top = topPadding, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date Sticker (слева)
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
            shadowElevation = 2.dp,
            tonalElevation = 1.dp
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        // Filter Sticker (справа)
        if (onFilterClick != null) {
            Surface(
                onClick = onFilterClick,
                shape = RoundedCornerShape(50),
                color = if (isFilterActive) SamsungGreen.copy(alpha = 0.18f) else MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                shadowElevation = 2.dp,
                tonalElevation = 1.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Фильтрация вызовов",
                        tint = if (isFilterActive) SamsungGreen else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                    if (isFilterActive) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(SamsungGreen)
                        )
                    }
                }
            }
        }
    }
}
