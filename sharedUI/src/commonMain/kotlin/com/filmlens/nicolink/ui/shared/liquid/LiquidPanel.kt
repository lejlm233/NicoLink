package com.filmlens.nicolink.ui.shared.liquid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.filmlens.nicolink.ui.theme.LocalLazyFilmColors

/**
 * 液态玻璃面板容器：磨砂 + 边缘 lens 折射，内容浮于玻璃上。
 *
 * 结构与四角直角修复要点：
 * - 玻璃背景层独立（尺寸由容器 onSizeChanged 实测驱动，matchParentSize 会被 drawBackdrop
 *   的 LayoutModifier measure 吞掉高度，不可用），clip(shape) 在 drawBackdrop 外层——
 *   玻璃层与背景一起圆角裁剪，四角无直角；显式 highlight=null/shadow=null（默认 Shadow
 *   黑色 10% 画在轮廓外，曾被主人报告为四角黑色透明直角边的一部分）。
 * - 内容层不裁剪：容器不再整体 clip 内容，放大/溢出的子项（如胶卷卡片长按放大）不被
 *   容器裁切，可完整显示在容器边界之外。内容自身如需圆角由各调用点自理。
 * - 面板浅主题染色：onDrawSurface 叠品牌翡翠绿 brandPrimary 0.2，面板边界清晰。
 *
 * backdrop 由调用方决定：控件悬浮在记录层之上时直接传记录层（薄纱玻璃，透出真实背景）；
 * 悬浮在纯色工具栏上且需要自带底色时，由调用方用 rememberCombinedBackdrop 叠 CanvasBackdrop。
 */
@Composable
fun LiquidPanel(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24f.dp),
    glassVisible: Boolean = true,  // false 时隐藏玻璃背景层（如 HSL 拖动滑块时面板隐藏），内容保持占位
    content: @Composable BoxScope.() -> Unit
) {
    val isLightTheme = isLiquidGlassLightTheme()  // 跟随 App 主题（非系统暗色），亮色 App+暗色系统时不再误用暗色表面
    val colors = LocalLazyFilmColors.current
    val density = LocalDensity.current
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f)
        else Color(0xFF121212).copy(0.4f)
    var contentWidth by remember { mutableIntStateOf(0) }
    var contentHeight by remember { mutableIntStateOf(0) }

    Box(modifier) {
        if (glassVisible && contentWidth > 0 && contentHeight > 0) {
            // 玻璃背景层：尺寸 = 内容实测（不撑容器）。⚠️ 不能用容器自身尺寸（onSizeChanged
            // 容器）——容器尺寸含 animateContentSize 插值，背景跟随动画值会撑住容器、
            // 高度变形永远收敛不到目标（切 TOOLS 容器不会变小）；也不能用 matchParentSize
            // （被 drawBackdrop 的 LayoutModifier measure 吞高度）。背景=内容尺寸，
            // 动画中容器插值 > 内容时底部短暂露底（~300ms，可接受）。
            Box(
                modifier = Modifier
                    .width(with(density) { contentWidth.toDp() })
                    .height(with(density) { contentHeight.toDp() })
                    .clip(shape)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(24f.dp.toPx(), 24f.dp.toPx())
                        },
                        highlight = null,
                        shadow = null,
                        onDrawSurface = {
                            drawRect(containerColor)
                            // 面板浅主题染色（品牌翡翠绿 0.2）：纯磨砂玻璃在图片上边界不清
                            drawRect(colors.brandPrimary.copy(alpha = 0.2f))
                        }
                    ),
            ) {}
        }
        // 内容层：不裁剪（放大/溢出子项可完整显示在容器边界外）；尺寸驱动玻璃背景
        Box(
            Modifier.onSizeChanged {
                contentWidth = it.width
                contentHeight = it.height
            },
        ) {
            content()
        }
    }
}
