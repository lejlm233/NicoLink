package com.filmlens.nicolink.ui.shared

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.filmlens.nicolink.ui.theme.LocalHapticEnabled
import com.filmlens.nicolink.ui.theme.LocalLazyFilmColors
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.roundToInt

/** 步进短震动 5ms */
private fun gStep(ctx: android.content.Context, en: Boolean) {
    if (!en) return
    try { val v = ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(5, VibrationEffect.DEFAULT_AMPLITUDE))
        else v.vibrate(5) } catch (_: Exception) {}
}

/**
 * 编辑器旧版 Canvas 滑块（HSL 同款样式）：4dp 轨道 + 刻度线 + 双极品牌绿填充。
 * 数据流与旧版完全一致：lastV Int 去重 + rememberUpdatedState 回调 +
 * 5ms 步进震动；拖动才改值（轻点不跳值）。
 *
 * 双极填充以 range 中心为零点（range 需对称含 0，如 -100..100 / -30..30）；
 * 单极（range.first >= 0，如 0..100）填充从最左端向右延伸，零点刻度不强调。
 *
 * 玻璃 thumb（glassThumb=true）：Canvas 不画 thumb，由玻璃水珠替代——滑块内容经
 * layerBackdrop 注册为坐标依赖记录层，水珠移动到哪就采样哪（同矩阵小圆点机制）；
 * 按住/拖动时水珠 22dp 圆形变形为 46dp 药丸（RoundedCornerShape(50) 随宽高比自动取形）
 * + 折射增强（lens ×2）+ 高光增强。enabled=false 时不挂手势，滑块锁定。
 *
 * @param label 非空时显示「标签 …… 数值」行，null 时不显示数值行（调整面板用）
 * @param range 值域（默认 -100..100），拖动与刻度线按 range 映射
 * @param valueSuffix 数值行后缀（如 "°"）
 */
@Composable
fun GlassThumbSlider(
    label: String?,
    value: Int,
    onValueChange: (Int) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
    glassThumb: Boolean = false,
    enabled: Boolean = true,
    range: IntRange = -100..100,
    valueSuffix: String = "",
    trackBrush: Brush? = null,
) {
    val colors = LocalLazyFilmColors.current
    val ctx = LocalContext.current
    val he = LocalHapticEnabled.current
    var lastV by remember(value) { mutableIntStateOf(value) }

    // 按压进度：按住/拖动时玻璃 thumb 折射增强 + 药丸变形
    var dragging by remember { mutableStateOf(false) }
    val pressProgress by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = tween(150),
        label = "glassThumbPress",
    )
    // 水珠宽度：静止 22dp 圆形 → 按住 46dp 药丸（高度不变）
    val beadWidth by animateDpAsState(
        targetValue = if (dragging) 46.dp else 22.dp,
        animationSpec = tween(180),
        label = "glassThumbWidth",
    )
    val beadRadiusPx = with(LocalDensity.current) { beadWidth.toPx() / 2f }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    // 共用滑块模式下同一个 composable 会切换不同 spec（value/range 都变），
    // pointerInput(Unit) 的闭包捕获首次组合的 rMin/rMax 会导致拖动映射错乱、参数串位。
    // 用 rememberUpdatedState 让拖动闭包始终读到最新的 range/value。
    val currentRange by rememberUpdatedState(range)
    val rMin = range.first.toFloat()
    val rMax = range.last.toFloat()
    // progress 必须 clamp 到 0..1，否则 value 越界时 thumb 画到屏幕外
    val progress = ((value - rMin) / (rMax - rMin)).coerceIn(0f, 1f)  // range → 0..1
    val accentColor = colors.brandLight1
    var sliderPx by remember { mutableStateOf(IntSize.Zero) }

    // 记录层：滑块内容（轨道+填充+刻度）注册为坐标依赖 Backdrop，玻璃 thumb 移动采样
    val sliderContentBackdrop = rememberLayerBackdrop()

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (label != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "$value$valueSuffix",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .onSizeChanged { sliderPx = it },
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (glassThumb) Modifier.layerBackdrop(sliderContentBackdrop) else Modifier
                    )
                    .then(
                        // enabled=false 时不挂拖动手势——滑块锁定
                        if (enabled) Modifier.pointerInput(Unit) {
                            // 只吃水平拖动：垂直滑动不 consume，交回父级 verticalScroll，
                            // 否则用户在滑块区域想滚动面板（够到藏在深处的参数）会被拦截。
                            detectHorizontalDragGestures(
                                onDragStart = { onDragStart() },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                            ) { change, _ ->
                                change.consume()
                                val pct = (change.position.x / size.width).coerceIn(0f, 1f)
                                // 用 currentRange（rememberUpdatedState）：共用滑块切换 spec 后读到新 range
                                val cr = currentRange
                                val cMin = cr.first.toFloat()
                                val cMax = cr.last.toFloat()
                                val newV = (cMin + pct * (cMax - cMin)).roundToInt().coerceIn(cr)
                                if (newV != lastV) { lastV = newV; gStep(ctx, he) }
                                currentOnValueChange(newV)
                            }
                        } else Modifier
                    )
                    .then(
                        // 按压观察者：只读 pressed 状态驱动玻璃 thumb 动画，不消费事件、不改数值
                        if (enabled) Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    dragging = event.changes.any { it.pressed }
                                }
                            }
                        } else Modifier
                    ),
            ) {
                val canvasW = size.width
                val canvasH = size.height
                val centerX = canvasW / 2f
                val centerY = canvasH / 2f
                val trackHeight = 4.dp.toPx()
                val trackTop = centerY - trackHeight / 2f
                val cornerR = CornerRadius(2.dp.toPx())

                // ── Background track ──
                // trackBrush 非空 = 渐变轨道（色相条等取色器用法）：整条即值域，
                // 不画填充/刻度，thumb 位置即当前值
                if (trackBrush != null) {
                    drawRoundRect(
                        brush = trackBrush,
                        topLeft = Offset(0f, trackTop),
                        size = Size(canvasW, trackHeight),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                } else {
                    drawRoundRect(
                        color = colors.bgControl,
                        topLeft = Offset(0f, trackTop),
                        size = Size(canvasW, trackHeight),
                        cornerRadius = cornerR,
                    )

                    // 单极（range.first >= 0，如 0..100）：填充从最左端（最小值）向右延伸到当前值；
                    // 双极（对称含 0，如 -100..100）：填充以中心零点向左右延伸。
                    val bipolar = range.first < 0
                    if (bipolar) {
                        if (progress > 0.5f) {
                            val rightWidth = (progress - 0.5f) * canvasW
                            drawRoundRect(
                                color = accentColor,
                                topLeft = Offset(centerX, trackTop),
                                size = Size(rightWidth, trackHeight),
                                cornerRadius = cornerR,
                            )
                        } else if (progress < 0.5f) {
                            val leftWidth = (0.5f - progress) * canvasW
                            drawRoundRect(
                                color = accentColor,
                                topLeft = Offset(centerX - leftWidth, trackTop),
                                size = Size(leftWidth, trackHeight),
                                cornerRadius = cornerR,
                            )
                        }
                    } else if (progress > 0f) {
                        drawRoundRect(
                            color = accentColor,
                            topLeft = Offset(0f, trackTop),
                            size = Size(progress * canvasW, trackHeight),
                            cornerRadius = cornerR,
                        )
                    }

                    // ── Tick marks（每 10 单位，range 内）──
                    for (v in range.first..range.last step 10) {
                        val pct = (v - rMin) / (rMax - rMin)
                        val x = canvasW * pct
                        // 零点强调只在双极（0 是语义中心）；单极 0 在最左端按普通主刻度处理
                        val isZero = bipolar && v == 0
                        val isMajor = v % 20 == 0
                        val tickColor = when {
                            isZero -> accentColor
                            isMajor -> colors.textTertiary
                            else -> colors.iconDisabled
                        }
                        val tickLen = when {
                            isZero -> 16f
                            isMajor -> 12f
                            else -> 6f
                        }
                        val tickW = when {
                            isZero -> 2.5f
                            isMajor -> 1.5f
                            else -> 0.8f
                        }
                        drawLine(
                            tickColor,
                            Offset(x, centerY - tickLen / 2f),
                            Offset(x, centerY + tickLen / 2f),
                            tickW,
                        )
                    }
                }

                // ── Thumb（玻璃 thumb 启用时由玻璃水珠替代，不画白圈）──
                if (!glassThumb) {
                    val thumbRadius = 9.dp.toPx()
                    val thumbX = canvasW * progress
                    drawCircle(
                        color = colors.textPrimary,
                        radius = thumbRadius,
                        center = Offset(thumbX, centerY),
                    )
                    drawCircle(
                        color = accentColor,
                        radius = thumbRadius,
                        center = Offset(thumbX, centerY),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }

            // 玻璃 thumb 水珠：叠在 Canvas 之上（无指针处理，事件穿透到滑块）；
            // 按住时 22dp 圆形 → 46dp 药丸变形（RoundedCornerShape(50) 按宽高比自动取形）
            if (glassThumb && sliderPx.width > 0 && sliderPx.height > 0) {
                val thumbX = sliderPx.width * progress
                val centerY = sliderPx.height / 2f
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (thumbX - beadRadiusPx).roundToInt(),
                                (centerY - 11.dp.toPx()).roundToInt(),
                            )
                        }
                        .size(width = beadWidth, height = 22.dp)
                        .drawBackdrop(
                            backdrop = sliderContentBackdrop,
                            shape = { RoundedCornerShape(50) },
                            effects = {
                                val p = pressProgress
                                lens(
                                    5.dp.toPx() * (1f + 1f * p),
                                    9.dp.toPx() * (1f + 1f * p),
                                    chromaticAberration = true,
                                )
                            },
                            highlight = { Highlight.Default.copy(alpha = 0.4f + 0.6f * pressProgress) },
                            shadow = { Shadow(radius = 8.dp, alpha = 0.5f) },
                            onDrawSurface = { drawRect(Color.White.copy(alpha = 0.10f + 0.08f * pressProgress)) },
                        ),
                )
            }
        }
    }
}

