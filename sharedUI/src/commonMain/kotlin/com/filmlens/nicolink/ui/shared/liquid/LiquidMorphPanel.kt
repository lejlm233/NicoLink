package com.filmlens.nicolink.ui.shared.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.filmlens.nicolink.ui.theme.LocalLazyFilmColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** 旧内容切换幽灵：最后一帧快照层 + 快照自然高度（底对齐换算用）。 */
private class PanelGhost(
    val layer: GraphicsLayer,
    val fromHeightPx: Int,
)

/**
 * 液态玻璃整体变形面板：切换时「三段编排、段间小尾巴小幅重叠」（主人 09-09 定稿剧本，
 * 渐隐/渐显版；09-09 起段 1 尾 ~50ms 与段 2 头重叠、段 3 在玻璃距目标 ≤2dp 时启动）：
 *
 * 1. 退场段：旧内容快照渐隐至完全消失，玻璃保持旧高度不动；
 * 2. 变形段：玻璃 morph——高→低顶边从上往下缩、低→高向上伸，底边固定
 *    （spring 0.8/380，visibilityThreshold=1px 视觉到位即判完成），期间面板内部为空；
 * 3. 入场段：玻璃接近到位后，新内容渐显，完成后进入稳态。
 *    等高切换跳过变形段直接交叉过渡。
 *
 * 实现要点（前序实验教训全部规避）：
 * - 容器 layout 强制报告弹簧高度并对子内容解除高度约束：外部 Row1 的 height(172/136)
 *   切换帧瞬变会经约束把玻璃当场压矮（顶边瞬跳下移 = "内容强制下移再收缩"），
 *   玻璃高度只许由弹簧驱动；Row1 无背景，其尺寸跳变不可见。
 * - 新内容用 SubcomposeLayout 承载：始终组合+测量（为变形段提供目标高度），但入场段
 *   之前【不放置】——未 place 的节点物理上不可能被绘制。⚠️ 不能用「预挂载 + 绘制期
 *   alpha 门控」藏新内容：morph3b 真机验证门控拦不住挂载帧（主人观察到内容闪现一帧
 *   再消失），布局级不放置才是结构性免疫。
 * - 旧内容化身 = 最后一帧快照（8 层 rememberGraphicsLayer 池轮转，只读共享层、
 *   新幽灵整体替换旧幽灵）；record 无条件执行（稳态下池内像素始终新鲜；退场/变形段
 *   新内容未放置不绘制，池层保持旧内容像素属已知取舍，快速连点的幽灵会显示上上次内容）。
 * - 渐隐为渲染层变换（saveLayer + Paint.alpha），不触碰布局；玻璃层显式宽高 =
 *   SubcomposeLayout 上报同一个弹簧值。
 * - ⚠️ 编排协程 key 必须用切换代数 switchSeq，绝不能用 ghost——段 1 结束置 ghost=null
 *   会改 effect 自身的 key、协程被取消重启后首行 return，段 2/3 永不执行
 *   （morph3 首版「玻璃不动 + 内容消失」就是这个 bug）。
 * - 渐隐/渐显用 float 状态而非 Animatable：切换帧须在组合期同步置值
 *   （suspend snapTo 做不到），渐变由编排协程 animate() 回写。
 * - 稳态弹簧 effect 开头 if (ghost != null) return：两个 effect 抢同一 Animatable
 *   会互相取消、连锁取消编排协程。
 *
 * backdrop 由调用方决定（同 LiquidPanel）。
 */
@Composable
fun LiquidMorphPanel(
    backdrop: Backdrop,
    contentKey: Any?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24f.dp),
    content: @Composable () -> Unit,
) {
    val isLightTheme = isLiquidGlassLightTheme()  // 跟随 App 主题（同 LiquidPanel）
    val colors = LocalLazyFilmColors.current
    val density = LocalDensity.current
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f)
        else Color(0xFF121212).copy(0.4f)

    // ── 唯一动画源：面板高度弹簧（px）。首次直接落位，不从 0 生长 ──
    var targetHeightPx by remember { mutableIntStateOf(0) }
    val panelH = remember { Animatable(0f) }
    var panelStarted by remember { mutableStateOf(false) }

    // ── 切换幽灵：快照池轮转 ──
    var ghost by remember { mutableStateOf<PanelGhost?>(null) }
    val capturePool = List(8) { rememberGraphicsLayer() }
    var captureIndex by remember { mutableLongStateOf(0L) }
    val currentCapture = capturePool[(captureIndex % 8L).toInt()]

    // 退场（旧快照）/ 入场（新内容）的透明度：组合期同步置值 + 协程 animate 回写
    var ghostAlpha by remember { mutableFloatStateOf(1f) }
    var contentAlpha by remember { mutableFloatStateOf(1f) }
    val ghostPaint = remember { Paint() }
    val contentPaint = remember { Paint() }

    // 新内容是否放置（= 可见）。切换帧卸下（组合+测量但不画），入场段才放回
    var contentPlaced by remember { mutableStateOf(true) }

    // 切换代数：每次切换 ++，编排协程的 key（勿用 ghost 当 key，见类注释）
    var switchSeq by remember { mutableLongStateOf(0L) }

    // ── 切换检测（组合期，同帧生效）：旧内容最后一帧快照移交幽灵，
    //    新内容卸下放置（仍组合+测量，为变形段提供目标高度）──
    var lastContentKey by remember { mutableStateOf(contentKey) }
    if (contentKey != lastContentKey) {
        if (targetHeightPx > 0) {
            ghost = PanelGhost(currentCapture, targetHeightPx)
            ghostAlpha = 1f
            contentAlpha = 0f
            contentPlaced = false
            captureIndex++
            switchSeq++
        }
        lastContentKey = contentKey
    }

    // 稳态弹簧（首次落位 + 无切换时的高度自适应）。过渡中必须让位（防抢占连锁取消）
    LaunchedEffect(targetHeightPx) {
        if (targetHeightPx <= 0) return@LaunchedEffect
        if (ghost != null) return@LaunchedEffect
        if (!panelStarted) {
            panelStarted = true
            panelH.snapTo(targetHeightPx.toFloat())
        } else {
            // visibilityThreshold=1px：弹簧默认要收敛到 0.01px 才算结束，尾部肉眼不可见
            // 却把 animateTo 拖很久（段 3 起跑过晚的根因）；差 1px 即视为到位
            panelH.animateTo(
                targetHeightPx.toFloat(),
                spring(
                    dampingRatio = 0.8f,
                    stiffness = 380f,
                    visibilityThreshold = 1f,
                ),
            )
        }
    }

    // ── 三段编排（key = switchSeq，段间小尾巴小幅重叠——主人 09-09 要求，不要很多）──
    LaunchedEffect(switchSeq) {
        if (switchSeq == 0L) return@LaunchedEffect
        val g = ghost ?: return@LaunchedEffect
        val enterThreshold = with(density) { 2.dp.toPx() }
        // 段 1：旧内容快照渐隐；150ms 处段 2 启动，留 ~50ms 尾巴与玻璃变形重叠
        val ghostJob = launch {
            animate(1f, 0f, animationSpec = tween(200)) { v, _ -> ghostAlpha = v }
        }
        delay(150)
        // 段 2：玻璃变形。切换帧布局已完成新内容测量（协程晚于布局启动），快查即可；
        // 疑似等高时 50ms 兜底等待，超时=等高 → 跳过变形段
        val target = if (targetHeightPx != g.fromHeightPx) {
            targetHeightPx
        } else {
            withTimeoutOrNull(50) {
                snapshotFlow { targetHeightPx }.first { it != g.fromHeightPx }
            } ?: g.fromHeightPx
        }
        if (panelH.value != target.toFloat()) {
            // 后台 morph（不等它结束），段 3 用接近度衔接尾巴
            launch {
                panelH.animateTo(
                    target.toFloat(),
                    spring(
                        dampingRatio = 0.8f,
                        stiffness = 380f,
                        visibilityThreshold = 1f,
                    ),
                )
            }
        }
        // 段 3：玻璃接近目标（等高则立即）就放回新内容渐显——与段 2 尾巴重叠
        snapshotFlow { abs(panelH.value - target) }.first { it <= enterThreshold }
        contentPlaced = true
        animate(0f, 1f, animationSpec = tween(180)) { v, _ -> contentAlpha = v }
        contentAlpha = 1f
        ghostJob.join()
        ghost = null
    }

    val panelHeightDp = with(density) { panelH.value.roundToInt().toDp() }
    Box(
        modifier.layout { measurable, constraints ->
            // 容器布局策略：对子内容解除高度约束测量（玻璃 height=弹簧值不被外部压矮），
            // 报告尺寸遵守传入约束（Row1 height(172/136) 切换帧瞬变会把报告区压矮），
            // 但内容按弹簧高度【底对齐】放置、顶部向上溢出绘制——玻璃底边恒定钉在
            // 链底部，顶边=底-弹簧值，高度只由弹簧驱动（morph3e 用"无视约束报告"修
            // 不掉：Row1 的 BottomCenter place 使链节点收缩时顶对齐内部把玻璃整体
            // 下拽，才是"内容强制下移"的真根因之一）。
            val placeable = measurable.measure(
                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            )
            val h = panelHeightDp.roundToPx()
            val reportedH = h.coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(placeable.width, reportedH) {
                placeable.placeRelative(0, reportedH - placeable.height)
            }
        },
    ) {
        // 玻璃层：显式宽高 = 弹簧值（与容器同源），clip 在 drawBackdrop 外层（四角直角修复同款）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeightDp)
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
                        // 面板浅主题染色（品牌翡翠绿 0.2），同 LiquidPanel
                        drawRect(colors.brandPrimary.copy(alpha = 0.2f))
                    },
                ),
        ) {}

        // 内容层：子内容组合+测量（自然高度，供变形段取目标），入场段前不放置——
        // 未 place 的节点物理上不可能被绘制，切换瞬间新内容闪现被结构性排除
        SubcomposeLayout(
            Modifier
                .fillMaxWidth()
                .drawWithContent {
                    val g = ghost
                    if (g == null) {
                        drawContent()
                    } else {
                        // 段 1：旧内容快照渐隐（底对齐：快照画在面板底部）
                        if (ghostAlpha > 0f) {
                            val snapshotTop = size.height - g.fromHeightPx
                            translate(0f, snapshotTop) {
                                drawIntoCanvas { canvas ->
                                    ghostPaint.alpha = ghostAlpha
                                    canvas.saveLayer(
                                        Rect(0f, 0f, size.width, g.fromHeightPx.toFloat()),
                                        ghostPaint,
                                    )
                                    drawLayer(g.layer)
                                    canvas.restore()
                                }
                            }
                        }
                        // 退场/变形段新内容未放置，drawContent 无内容可画；仍须调用
                        // 保持绘制树语义
                        drawContent()
                    }
                },
        ) { constraints ->
            val placeable = subcompose(contentKey) {
                // 内容包裹层：尺寸 = 自然尺寸（弹簧目标）。record 无条件执行（快照池始终
                // 记录当前活内容完整像素），显示按 contentAlpha 分支走 saveLayer
                Box(
                    Modifier
                        .onSizeChanged { targetHeightPx = it.height }
                        .drawWithContent {
                            currentCapture.record { this@drawWithContent.drawContent() }
                            val a = contentAlpha
                            if (a >= 1f) {
                                drawLayer(currentCapture)
                            } else if (a > 0f) {
                                drawIntoCanvas { canvas ->
                                    contentPaint.alpha = a
                                    canvas.saveLayer(
                                        Rect(0f, 0f, size.width, size.height),
                                        contentPaint,
                                    )
                                    drawLayer(currentCapture)
                                    canvas.restore()
                                }
                            }
                            // a == 0f：不绘制（放置后首帧与渐显前不可见）
                        },
                ) {
                    content()
                }
            }.first().measure(
                // 自然高度测量：约束不随动画逐帧变化，内容重排物理上不可能发生
                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            )
            val h = panelHeightDp.roundToPx()
            layout(placeable.width, h) {
                if (contentPlaced) {
                    placeable.placeRelative(0, h - placeable.height)
                }
            }
        }
    }
}
