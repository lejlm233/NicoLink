package com.filmlens.nicolink.ui.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmlens.nicolink.data.MonthGrid
import com.filmlens.nicolink.data.MonthGridEntry
import com.filmlens.nicolink.ui.theme.LocalLazyFilmColors
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 右缘滚动时间轴（对齐 lazyfilm MonthTimelineOverlay）：
 * - 刻度按距今年限聚合（本年度按月、上一年按季、更早按年），组间共享 grid 索引；
 * - 未选中为小灰圆点；选中行圆点拉长为绿横线 + 文字展开；
 * - 按住时玻璃药丸从右缘弹性展开，成为唯一当前指示；
 * - 点击/拖动跳到该分组最早一张（单 Job 管理，防并发 scrollToItem 漂移）；
 * - 出现逻辑：滚动中/按住显示，停止 3s 后滑出。
 *
 * 注意：本组件含玻璃药丸（drawBackdrop），必须放在 GlassPage 的 overlay 兄弟浮层，
 * 绝不能进 content（被 layerBackdrop 记录层），否则 backdrop 递归环导致 RenderThread 栈溢出闪退。
 */
@Composable
fun MonthTimelineOverlay(
    monthGrid: MonthGrid,
    gridState: LazyGridState,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLazyFilmColors.current
    val scope = rememberCoroutineScope()

    // 刻度分组：从 MonthGrid 的标题行条目直接提取（label, startGrid, jumpGrid）。
    // 倒序列表，最新组在 index 0。startGrid=标题行网格下标（选中锚点），jumpGrid 同（跳过去标题可见）。
    val groups = remember(monthGrid) {
        monthGrid.entries.mapIndexedNotNull { idx, entry ->
            if (entry is MonthGridEntry.Header) Triple(entry.label, idx, idx) else null
        }
    }

    var timelinePressed by remember { mutableStateOf(false) }
    val isScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isScrolling, timelinePressed) {
        if (isScrolling || timelinePressed) {
            visible = true
        } else {
            delay(3000)
            visible = false
        }
    }

    // 当前分组：snapshotFlow 订阅 firstVisibleItemIndex，倒序取「最后一个 start≤i 的组」
    val curIdx = remember { mutableStateOf(0) }
    LaunchedEffect(groups) {
        snapshotFlow { gridState.firstVisibleItemIndex }
            .collect { i -> curIdx.value = groups.indexOfLast { it.second <= i }.coerceAtLeast(0) }
    }

    AnimatedVisibility(
        visible = visible && groups.isNotEmpty(),
        enter = fadeIn(tween(200)) + slideInHorizontally(tween(250)) { it },
        exit = fadeOut(tween(200)) + slideOutHorizontally(tween(250)) { it },
        modifier = modifier,
    ) {
        val n = groups.size
        val rowH = if (n <= 26) 24.dp else maxOf(20.dp, (624f / n).dp)
        val pillH = rowH - 2.dp

        val scrollJob = remember { mutableStateOf<Job?>(null) }
        fun jumpToGroup(y: Float, trackH: Float) {
            if (trackH <= 0f) return
            val gi = ((y / trackH) * n).toInt().coerceIn(0, n - 1)
            val target = groups[gi].third
            if (gridState.firstVisibleItemIndex != target) {
                scrollJob.value?.cancel()
                scrollJob.value = scope.launch { gridState.scrollToItem(target) }
            }
        }

        Box(modifier = Modifier.width(88.dp)) {
            Box(
                modifier = Modifier.align(Alignment.CenterEnd).height(rowH * n).width(88.dp),
            ) {
                // 手势窄条：只占右缘 44dp
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(44.dp)
                        .pointerInput(groups) {
                            detectTapGestures { offset -> jumpToGroup(offset.y, size.height.toFloat()) }
                        }
                        .pointerInput(groups) {
                            detectVerticalDragGestures(
                                onDragStart = { timelinePressed = true },
                                onDragEnd = { timelinePressed = false },
                                onDragCancel = { timelinePressed = false },
                            ) { change, _ ->
                                change.consume()
                                jumpToGroup(change.position.y, size.height.toFloat())
                            }
                        },
                )
                // 刻度层
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.align(Alignment.CenterEnd)) {
                    groups.forEach { (label, _, _) ->
                        val isCur by remember(groups) {
                            derivedStateOf { groups.getOrNull(curIdx.value)?.first == label }
                        }
                        Box(modifier = Modifier.height(rowH), contentAlignment = Alignment.CenterEnd) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AnimatedVisibility(
                                    visible = isCur && !timelinePressed,
                                    enter = fadeIn(tween(120)) + expandHorizontally(tween(200)),
                                    exit = fadeOut(tween(120)) + shrinkHorizontally(tween(120)),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Spacer(Modifier.width(4.dp))
                                    }
                                }
                                val dotLen by animateDpAsState(if (isCur) 20.dp else 3.dp, tween(200), label = "dotLen")
                                val dotThick by animateDpAsState(if (isCur) 2.5.dp else 3.dp, tween(200), label = "dotThick")
                                val dotColor by animateColorAsState(
                                    when {
                                        !isCur -> colors.textTertiary
                                        timelinePressed -> Color.Transparent
                                        else -> colors.brandLight1
                                    },
                                    tween(120), label = "dotColor",
                                )
                                Box(
                                    modifier = Modifier.width(dotLen).height(dotThick)
                                        .background(dotColor, RoundedCornerShape(50)),
                                )
                            }
                        }
                    }
                }
                // 玻璃药丸层：按住时从右缘弹性展开
                AnimatedVisibility(
                    visible = timelinePressed,
                    enter = fadeIn(tween(150)) + expandHorizontally(
                        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
                        expandFrom = Alignment.End,
                    ),
                    exit = fadeOut(tween(150)) + shrinkHorizontally(tween(180), shrinkTowards = Alignment.End),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    val curLabel by remember(groups) {
                        derivedStateOf { groups.getOrNull(curIdx.value)?.first.orEmpty() }
                    }
                    val pillY by animateDpAsState(
                        targetValue = rowH * curIdx.value + (rowH - pillH) / 2,
                        animationSpec = if (timelinePressed) snap() else tween(180),
                        label = "pillY",
                    )
                    Row(
                        modifier = Modifier.offset { IntOffset(0, pillY.roundToPx()) }.height(pillH)
                            .animateContentSize(tween(180))
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedCornerShape(50) },
                                effects = { lens(5.dp.toPx(), 9.dp.toPx(), chromaticAberration = true) },
                                highlight = { Highlight.Default.copy(alpha = 0.4f) },
                                shadow = { Shadow(radius = 8.dp, alpha = 0.5f) },
                                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.10f)) },
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(curLabel, color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.width(20.dp).height(2.5.dp).background(colors.brandLight1, RoundedCornerShape(50)))
                    }
                }
            }
        }
    }
}
