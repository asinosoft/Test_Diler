package com.asinosoft.dialer.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asinosoft.dialer.R

@DrawableRes
fun simIconRes(simNumber: Int): Int = when (simNumber) {
    2 -> R.drawable.ic_sim2
    3 -> R.drawable.ic_sim3
    else -> R.drawable.ic_sim1
}

/** Shared SIM chip icon used across the dialer UI. */
@Composable
fun SimIcon(
    simNumber: Int,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    Icon(
        painter = painterResource(simIconRes(simNumber)),
        contentDescription = "SIM $simNumber",
        tint = Color.Unspecified,
        modifier = modifier.size(size)
    )
}
