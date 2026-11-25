package com.filmlens.nicolink.ui.shared.liquid

import androidx.compose.foundation.background
import com.filmlens.nicolink.ui.shared.liquid.inspectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.min
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.filmlens.nicolink.ui.shared.liquid.DampedDragAnimation
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,  // 未指定时回退官方默认蓝
    bipolar: Boolean = false,  // 双极模式：填充从中心(0值)向两侧延伸（曝光/高光等 ± 参数）
    onDragStart: () -> Unit = {},  // 拖动开始/结束（接旧版 setDragging → 降采样+防抖机制）
    onDragEnd: () -> Unit = {},
) {
    val isLightTheme = isLiquidGlassLightTheme()  // 跟随 App 主题（非系统暗色），亮色 App+暗色系统时不再误用暗色表面
    val resolvedAccent = if (accentColor != Color.Unspecified) accentColor
        else if (isLightTheme) Color(0xFF0088FF)
        else Color(0xFF0091FF)
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.2f)
        else Color(0xFF787880).copy(0.36f)

    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        // ⚠️ onValueChange 同样必须经 rememberUpdatedState——DampedDragAnimation(remember)
        // 与 pointerInput(key 稳定) 的闭包只捕获首次组合的 onValueChange，而 AdjustContent 的
        // onValueChange 是 when(selectedTab) 动态映射（首次=曝光），捕获过时回调会导致
        // 「点暗角/拖滑块实际改的是曝光」的参数错乱
        val onValueChangeState by rememberUpdatedState(onValueChange)
        var didDrag by remember { mutableStateOf(false) }
        // 拖动中标志：拖动时 value() 的变化全是自己 onValueChange 的回显（Int 量化滞后于
        // 连续 targetValue），若被 snapshotFlow 回拉 animateTo，水珠会落后手指并累积
        // （「拖到 60 自己慢慢退回 45-50」）。拖动中彻底禁用反向同步，只响应外部真实变化。
        var isDragging by remember { mutableStateOf(false) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = {
                    if (didDrag) {
                        onValueChange(targetValue)
                    }
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) {
                        didDrag = dragAmount.x != 0f
                    }
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / trackWidth)
                    onValueChangeState(
                        if (isLtr) (targetValue + delta).coerceIn(valueRange)
                        else (targetValue - delta).coerceIn(valueRange)
                    )
                }
            )
        }
        // ⚠️ value() lambda 必须经 rememberUpdatedState 读取——LaunchedEffect(key=稳定)
        // 的闭包只捕获首次组合的 value lambda，外部状态（如切换参数按钮）变化后
        // 旧闭包仍返回旧值，snapshotFlow 永远不触发 → 滑块不跟随参数切换
        val currentValueLambda by rememberUpdatedState(value)
        // 按轨道绝对位置更新值（x → 归一化 → valueRange）
        val updateValueByPosition: (Float, Float, Boolean) -> Unit = { x, width, ltr ->
            val progress = (x / width).coerceIn(0f, 1f)
            val target =
                (if (ltr) valueRange.start + (valueRange.endInclusive - valueRange.start) * progress
                else valueRange.endInclusive - (valueRange.endInclusive - valueRange.start) * progress)
                    .coerceIn(valueRange)
            dampedDragAnimation.updateValue(target)
            onValueChangeState(target)
        }

        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentValueLambda() }
                .collectLatest { value ->
                    // ⚠️ 拖动中禁用回拉（见 isDragging 注释）；非拖动时差值超过
                    // visibilityThreshold 才同步（切参数按钮/外部加载 → 滑块跳到对应值）
                    if (!isDragging && abs(dampedDragAnimation.targetValue - value) > visibilityThreshold) {
                        dampedDragAnimation.updateValue(value)
                    }
                }
        }

        // 轨道容器（40dp 触控高度，垂直居中）：layerBackdrop 记录轨道层供水珠折射采样。
        // 手势=官方原版：tap 跳值（animateToValue 动画滑到点击处）+ 拖水珠改值；
        // ⚠️ 轨道不能加拖动手势——会与水珠手势抢事件（真机上互相取消 →「偶尔能拖/松手失效」）。
        // 官方 tap 手势原本在 6dp 轨道上难点中，此处放到 40dp 触控层（行为不变、点击区扩大）。
        Box(
            Modifier
                .fillMaxWidth()
                .height(40f.dp)
                .layerBackdrop(trackBackdrop),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(animationScope, valueRange) {
                        // 旧版滑块同款手势（inspectDragGestures，Initial pass 捕获最可靠）：
                        // 按下即跳值到手指位置（「点哪滑到哪」）+ 绝对位置拖动；
                        // consume move 后官方水珠手势自动取消释放，单一驱动不冲突。
                        inspectDragGestures(
                            onDragStart = { down ->
                                isDragging = true
                                onDragStart()
                                updateValueByPosition(down.position.x, trackWidth.toFloat(), isLtr)
                            },
                            onDragEnd = {
                                isDragging = false
                                onDragEnd()
                            },
                            onDragCancel = {
                                isDragging = false
                                onDragEnd()
                            },
                        ) { change, _ ->
                            change.consume()
                            updateValueByPosition(change.position.x, trackWidth.toFloat(), isLtr)
                        }
                    }
            )

            Box(
                Modifier
                    .clip(Capsule())
                    .background(trackColor)
                    .height(6f.dp)
                    .fillMaxWidth()
            )

            Box(
                Modifier
                    .clip(Capsule())
                    .background(resolvedAccent)
                    .height(6f.dp)
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val maxWidth = constraints.maxWidth
                        val width = if (bipolar) {
                            // 双极：填充从中心向 value 侧延伸
                            val progress = dampedDragAnimation.progress
                            (maxWidth * abs(progress - 0.5f)).toInt()
                        } else {
                            (maxWidth * dampedDragAnimation.progress).toInt()
                        }
                        layout(width, placeable.height) {
                            if (bipolar) {
                                val center = maxWidth / 2f
                                val left = min(center, center + (dampedDragAnimation.progress - 0.5f) * maxWidth)
                                placeable.place(left.toInt(), 0)
                            } else {
                                placeable.place(0, 0)
                            }
                        }
                    }
            )
        }

        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                            .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) * if (isLtr) 1f else -1f
                }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 1f, progress)
                            val scaleY = lerp(0f, 1f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4f.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40f.dp, 24f.dp)
        )
    }
}
