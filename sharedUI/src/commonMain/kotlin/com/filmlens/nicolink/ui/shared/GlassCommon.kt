package com.filmlens.nicolink.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmlens.nicolink.ui.shared.liquid.LiquidButton
import com.filmlens.nicolink.ui.shared.liquid.isLiquidGlassEnabled
import com.filmlens.nicolink.ui.theme.AppIcons
import com.filmlens.nicolink.ui.theme.LocalLazyFilmColors
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 玻璃页面骨架（对齐 lazyfilm 真实做法）：
 * - content 只放普通内容，挂在 layerBackdrop 上作为「被折射层」；
 * - 所有玻璃控件（Tab / 主按钮 / 拍照钮）必须放 overlay，作为兄弟浮层折射 content；
 *   绝不能放进 content——否则玻璃控件折射自己所在的记录层，形成 RenderNode 递归环，
 *   导致 libhwui computeTransformImpl 无限递归、RenderThread 栈溢出闪退。
 * - topBar 也是兄弟浮层（内部玻璃按钮安全）。
 *
 * 玻璃只用在这几处：Tab（LiquidBottomTabs）、按钮（GlassPrimaryButton /
 * GlassIconButton）、弹出面板（LiquidPanel）。卡片/照片格/列表用 Common.kt 普通组件。
 */
@Composable
fun GlassPage(
    topBar: @Composable (backdrop: Backdrop) -> Unit = {},
    content: @Composable () -> Unit,
    overlay: @Composable (backdrop: Backdrop) -> Unit = {}
) {
    val liquidBackdrop = rememberLayerBackdrop()
    val colors = LocalLazyFilmColors.current
    // 关闭液态玻璃时给控件传实色 backdrop：折射/模糊/透镜都作用在纯色上，视觉上退化为普通控件
    val flatBackdrop = rememberCanvasBackdrop { drawRect(colors.bgPrimary) }
    val activeBackdrop = if (isLiquidGlassEnabled()) liquidBackdrop else flatBackdrop
    Box(modifier = Modifier.fillMaxSize().background(LocalLazyFilmColors.current.bgPrimary)) {
        // 被折射层：只放普通内容，绝不含玻璃控件
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(liquidBackdrop)) {
            content()
        }
        // 顶栏浮层：玻璃按钮在此折射下面的内容
        topBar(activeBackdrop)
        // 玻璃浮层：Tab / 主按钮 / 拍照钮，作为兄弟折射 content（无递归环）
        overlay(activeBackdrop)
    }
}

/** 玻璃顶栏容器：普通布局 Row，标题 + 操作区；返回/设置按钮用 GlassIconButton（玻璃） */
@Composable
fun GlassTopBar(
    title: String,
    backdrop: Backdrop,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    /** 自定义标题样式（如主页 Pacifico logo）；为 null 时用默认粗体 Text */
    titleContent: (@Composable () -> Unit)? = null,
) {
    val colors = LocalLazyFilmColors.current
    Row(
        // edge-to-edge：顶栏整体避开状态栏，玻璃按钮因此落在状态栏下方
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            GlassIconButton(AppIcons.ArrowLeft, "返回", backdrop, onBack)
        }
        // 标题必须直接带 weight（Box 包一层 weight 会被 LiquidButton 的 drawBackdrop LayoutModifier
        // 干扰 Row 测量，导致标题被挤没、actions 跑到左边叠在一起）。
        if (titleContent != null) {
            Box(modifier = Modifier.weight(1f).padding(horizontal = if (onBack != null) 12.dp else 4.dp)) {
                titleContent()
            }
        } else {
            Text(
                title,
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(horizontal = if (onBack != null) 12.dp else 4.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** 玻璃圆形图标按钮（顶栏设置 / 返回）：液态玻璃折射下方内容 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    /** 叠在液态玻璃面板内时置 true：更厚更不透明（alpha 0.5→0.7）+ 更大模糊 */
    inPanel: Boolean = false,
    enabled: Boolean = true,
    /** 删除等危险操作：红色半透明背景 + 红色图标（对齐相机预览删除按钮） */
    danger: Boolean = false,
) {
    val colors = LocalLazyFilmColors.current
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier.size(44.dp),
        height = 44.dp,
        horizontalPadding = 0.dp,
        surfaceColor = if (danger) colors.brandDanger.copy(alpha = 0.22f)
            else colors.bgSurface.copy(alpha = if (inPanel) 0.7f else 0.5f),
        inPanel = inPanel,
        enabled = enabled,
    ) {
        Icon(
            icon, contentDescription = contentDescription,
            tint = tint ?: if (danger) colors.brandDanger else colors.textPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 玻璃主按钮：品牌色染色 + 折射，用于「开始连接 / 显影 / 导出」等主操作 */
@Composable
fun GlassPrimaryButton(
    text: String,
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    danger: Boolean = false,
    /** 叠在液态玻璃面板内时置 true：更厚更不透明（alpha 0.25→0.5）+ 更大模糊 */
    inPanel: Boolean = false
) {
    val colors = LocalLazyFilmColors.current
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        tint = if (danger) colors.brandDanger else colors.brandPrimary,
        surfaceColor = colors.bgSurface.copy(alpha = if (inPanel) 0.5f else 0.25f),
        height = 52.dp,
        inPanel = inPanel
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
        }
        Text(text, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
