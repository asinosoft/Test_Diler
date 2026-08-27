package com.asinosoft.dialer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Плавающая дата поверх списка (слева, как раньше по цвету/типографике). */
@Composable
fun FloatingStickyDateHeader(
    text: String,
    modifier: Modifier = Modifier,
    startPadding: Dp = 4.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPadding, end = 4.dp, top = 6.dp, bottom = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
            shadowElevation = 2.dp
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
