@file:Suppress("FunctionNaming")

package com.tospery.suite.ui

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity

@Composable
internal fun OnePixelHorizontalDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA),
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = with(LocalDensity.current) { 1.toDp() },
        color = color,
    )
}

private const val DIVIDER_ALPHA = 0.7f
