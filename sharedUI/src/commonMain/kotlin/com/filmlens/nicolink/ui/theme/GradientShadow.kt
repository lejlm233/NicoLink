package com.filmlens.nicolink.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Claymorphism 风格投影 — 右下偏移实色阴影。
 *
 * 对应 CSS: box-shadow: 4px 7px 8px rgba(0,0,0,0.15)
 * 阴影和按钮一样大，偏移到右下，组件遮住重叠部分，
 * 露出右下边缘的溢出部分作为投影。
 */
fun Modifier.clayShadow(
    shadowColor: Color = Color(0x40000000),
    offsetX: Dp = (-3).dp,
    offsetY: Dp = 5.dp,
    cornerRadius: Dp = 30.dp,
    blurRadius: Dp = 0.dp,
): Modifier = this.drawBehind {
    val offXPx = offsetX.toPx()
    val offYPx = offsetY.toPx()
    val cornerPx = cornerRadius.toPx()

    if (blurRadius > 0.dp) {
        val blurPx = blurRadius.toPx()
        // 多层半透明叠加模拟模糊效果
        val layers = 4
        for (i in 0 until layers) {
            val t = (i + 1).toFloat() / layers
            val alpha = (1f / t) * 0.12f * shadowColor.alpha
            val spread = blurPx * t * 0.8f
            drawRoundRect(
                color = shadowColor.copy(alpha = alpha.coerceIn(0f, shadowColor.alpha)),
                topLeft = Offset(offXPx - spread, offYPx - spread),
                size = androidx.compose.ui.geometry.Size(
                    size.width + spread * 2f,
                    size.height + spread * 2f,
                ),
                cornerRadius = CornerRadius(cornerPx + spread),
            )
        }
    } else {
        // 单层实色阴影，和组件一样大，偏移到右下
        drawRoundRect(
            color = shadowColor,
            topLeft = Offset(offXPx, offYPx),
            size = androidx.compose.ui.geometry.Size(size.width, size.height),
            cornerRadius = CornerRadius(cornerPx),
        )
    }
}
