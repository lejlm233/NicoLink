package com.filmlens.nicolink.ui.screen

import android.Manifest
import android.graphics.BitmapFactory
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import com.filmlens.nicolink.camera.CameraObject
import com.filmlens.nicolink.camera.CameraSession
import com.filmlens.nicolink.camera.ExposureInfo
import com.filmlens.nicolink.camera.HistogramData
import com.filmlens.nicolink.camera.SavedCamera
import com.filmlens.nicolink.camera.TransferProgress
import com.filmlens.nicolink.ui.AppNav
import com.filmlens.nicolink.ui.Route
import com.filmlens.nicolink.ui.UiPrefs
import com.filmlens.nicolink.ui.shared.EmptyHint
import com.filmlens.nicolink.ui.shared.GlassIconButton
import com.filmlens.nicolink.ui.shared.GlassPage
import com.filmlens.nicolink.ui.shared.GlassPrimaryButton
import com.filmlens.nicolink.ui.shared.GlassTopBar
import com.filmlens.nicolink.ui.shared.PlaceholderCard
import com.filmlens.nicolink.ui.shared.SectionTitle
import com.filmlens.nicolink.ui.shared.liquid.LiquidButton
import com.filmlens.nicolink.ui.shared.liquid.LiquidPanel
import com.filmlens.nicolink.ui.theme.AppIcons
import com.filmlens.nicolink.ui.theme.LocalHapticEnabled
import com.filmlens.nicolink.ui.theme.LocalLazyFilmColors
import com.filmlens.nicolink.ui.theme.vibrateTap
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

// ───────────────────────── 主页「相机」Tab ─────────────────────────

/** 相机照片按日分组条目：Header=整行日期标题，Photo=单格照片 */
private sealed class CameraGridEntry {
    data class Header(val key: String, val label: String) : CameraGridEntry()
    data class Photo(val obj: CameraObject) : CameraGridEntry()
}

/** 从 PTP 时间字符串提取「日」分组 key（兼容 yyyyMMddTHHmmss 与 yyyy:MM:dd HH:mm:ss） */
private fun cameraDayKey(date: String): String {
    val d = date.trim()
    val digits = d.replace(Regex("[^0-9]"), "")
    return if (digits.length >= 8) digits.substring(0, 8) else "unknown"
}

/** 日分组 label（今天/昨天/M月d日） */
private fun cameraDayLabel(key: String): String {
    if (key.length != 8) return "未知日期"
    val y = key.substring(0, 4).toIntOrNull() ?: return "未知日期"
    val m = key.substring(4, 6).toIntOrNull() ?: return "未知日期"
    val d = key.substring(6, 8).toIntOrNull() ?: return "未知日期"
    val cal = java.util.Calendar.getInstance()
    val today = "%04d%02d%02d".format(
        cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH)
    )
    cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
    val yesterday = "%04d%02d%02d".format(
        cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH)
    )
    return when (key) {
        today -> "今天 · ${m}月${d}日"
        yesterday -> "昨天 · ${m}月${d}日"
        else -> "${y}年${m}月${d}日"
    }
}

/** 相机照片按日分组（objects 已按 captureDate DESC 排序） */
private fun buildCameraDayGrid(objects: List<CameraObject>): List<CameraGridEntry> {
    val out = mutableListOf<CameraGridEntry>()
    var curKey = ""
    objects.forEach { obj ->
        val key = cameraDayKey(obj.captureDate)
        if (key != curKey) {
            curKey = key
            out.add(CameraGridEntry.Header(key, cameraDayLabel(key)))
        }
        out.add(CameraGridEntry.Photo(obj))
    }
    return out
}

/** 机型缩略图（assets/camera_models，来自桌面「尼康相机图片」，已压到 ~20KB/张）；未知机型回退相机图标 */
@Composable
private fun CameraModelIcon(model: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = LocalLazyFilmColors.current
    val bitmap = remember(model) {
        val key = model.orEmpty().lowercase().replace("nikon", "").replace(" ", "").replace("-", "").trim()
        val assets = listOf("Z30", "Z6II", "Z7II", "Z8", "Z9", "Zf", "Zfc", "Z50II", "Z5II", "Z5IIC", "Z6III")
        val match = assets.firstOrNull { it.lowercase() == key }
        if (match != null) {
            try {
                context.assets.open("camera_models/$match.png").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            } catch (_: Exception) {
                null
            }
        } else null
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = model, contentScale = ContentScale.Fit, modifier = modifier)
    } else {
        Icon(AppIcons.CameraIris, contentDescription = model, tint = colors.brandLight1, modifier = modifier)
    }
}

@Composable
fun CameraTabContent(
    nav: AppNav,
    selectMode: Boolean = false,
    selectedHandles: Set<Int> = emptySet(),
    onSelectLongClick: (Int) -> Unit = {},
    onSelectToggle: (Int) -> Unit = {},
) {
    val colors = LocalLazyFilmColors.current
    val state = CameraSession.state
    val objects = CameraSession.objects

    LaunchedEffect(state.connected) {
        if (state.connected) CameraSession.refreshObjects()
    }

    // 进入相机 Tab 时自动寻找上次连接的相机（序列号匹配才自动接上）；受设置页「自动连接」开关控制
    LaunchedEffect(Unit) {
        if (UiPrefs.autoConnectCamera) CameraSession.autoReconnect()
    }

    // 相机页整体滚动状态（与本机页一致：滚动时顶部盖渐变保证顶栏文字可读）
    val camGridState = rememberLazyGridState()
    val camScrolled by remember {
        derivedStateOf {
            camGridState.firstVisibleItemIndex > 0 ||
                (camGridState.firstVisibleItemIndex == 0 && camGridState.firstVisibleItemScrollOffset > 0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!state.connected) {
            if (UiPrefs.autoConnectCamera && CameraSession.autoReconnecting) {
                // 自动连接进行中：中间显示动态加载，让用户知道正在自动连接相机
                Column(
                    modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 200.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = colors.brandPrimary, strokeWidth = 3.dp)
                    Spacer(Modifier.height(14.dp))
                    Text("正在自动连接相机…", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().statusBarsPadding()
                        .padding(top = 200.dp).padding(horizontal = 12.dp)
                ) {
                    EmptyHint("手机开热点 → 相机连入 → 这里显示相机内的照片")
                }
            }
        } else {
            // 全部内容放在同一个 LazyVerticalGrid：信息条/进度条作为跨 3 列的 header，
            // 与图片网格一起滚动（相机信息条可滚出屏幕），对齐本机页的整体滚动效果。
            Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                state = camGridState,
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 178.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 相机信息条（跨整行）
                item(span = { GridItemSpan(3) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CameraModelIcon(state.model, Modifier.size(52.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(state.model.ifBlank { "尼康相机" }, color = colors.textPrimary,
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BatteryIcon(state.battery, Modifier.size(16.dp, 9.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("${state.battery ?: "--"}%", color = colors.textTertiary, fontSize = 11.sp)
                                Spacer(Modifier.width(10.dp))
                                Icon(AppIcons.SdCard, contentDescription = "容量", tint = Color(0xFFC9A227), modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(state.freeBytes?.let { formatBytes(it) } ?: "--", color = colors.textTertiary, fontSize = 11.sp)
                                if (state.host.isNotBlank()) {
                                    Spacer(Modifier.width(10.dp))
                                    Text(state.host, color = colors.textTertiary, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                        if (CameraSession.objectsLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.brandPrimary)
                            Spacer(Modifier.width(10.dp))
                        }
                        Icon(
                            AppIcons.PhotoCamera, "监看", tint = colors.brandPrimary,
                            modifier = Modifier.size(22.dp).clickable { nav.push(Route.MONITOR) },
                        )
                        Icon(
                            AppIcons.Refresh, "刷新相机照片",
                            tint = colors.iconPrimary,
                            modifier = Modifier.size(24.dp).clickable { CameraSession.refreshObjects() },
                        )
                    }
                }
                // 自动传输进度条（跨整行）
                val auto = CameraSession.autoTransfer
                val autoProgress = CameraSession.transfer
                    ?.takeIf { it.filename == auto.currentName && !it.finished && it.error == null }?.fraction
                if (auto.active) {
                    item(span = { GridItemSpan(3) }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("自动传输 ${auto.currentName.ifBlank { "准备中…" }}" +
                                    if (auto.remaining > 1) " · 队列 ${auto.remaining} 张" else "",
                                    color = colors.textSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                if (autoProgress != null) Text("${(autoProgress * 100).toInt()}%", color = colors.textSecondary, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(progress = { autoProgress ?: 0f },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = colors.brandPrimary, trackColor = colors.bgCard)
                        }
                    }
                }
                // 自动显影进度条（跨整行）：引擎无进度回调，用不确定进度条
                val autoDev = CameraSession.autoDevelopProgress
                if (autoDev.active) {
                    item(span = { GridItemSpan(3) }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                            Text("自动显影 ${autoDev.currentName.ifBlank { "处理中…" }}" +
                                if (autoDev.remaining > 0) " · 队列 ${autoDev.remaining} 张" else "",
                                color = colors.textSecondary, fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = colors.brandLight1, trackColor = colors.bgCard,
                            )
                        }
                    }
                }
                // 空状态与日志（跨整行）
                if (objects.isEmpty() && !CameraSession.objectsLoading) {
                    item(span = { GridItemSpan(3) }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
                            Box(modifier = Modifier.padding(horizontal = 10.dp)) {
                                val err = CameraSession.objectsError
                                EmptyHint(when {
                                    err != null -> "照片读取失败：$err\n点右上角刷新重试"
                                    CameraSession.objectsDenied -> "相机处于拍摄模式，对象访问被锁\n请按相机背面的 ▶ 键切到回放模式，再点右上角刷新"
                                    else -> "没有读到相机里的照片\n以下是最近协议日志，可能包含原因"
                                })
                            }
                            Spacer(Modifier.height(10.dp))
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                                .clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(10.dp)) {
                                val lines = CameraSession.logs.takeLast(12)
                                if (lines.isEmpty()) Text("（暂无日志）", color = colors.textTertiary, fontSize = 10.sp)
                                lines.forEach { line ->
                                    Text(line, color = colors.textSecondary, fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 1.dp))
                                }
                            }
                        }
                    }
                }
                // 图片网格：按日分组（日期 Header 跨整行，无右侧时间轴）
                val dayEntries = buildCameraDayGrid(objects)
                items(
                    items = dayEntries,
                    key = { entry ->
                        when (entry) {
                            is CameraGridEntry.Header -> "hdr_${entry.key}"
                            is CameraGridEntry.Photo -> "${entry.obj.storageId}-${entry.obj.handle}"
                        }
                    },
                    span = { entry ->
                        when (entry) {
                            is CameraGridEntry.Header -> GridItemSpan(3)
                            is CameraGridEntry.Photo -> GridItemSpan(1)
                        }
                    }
                ) { entry ->
                    when (entry) {
                        is CameraGridEntry.Header -> Text(
                            text = entry.label,
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                        is CameraGridEntry.Photo -> {
                            val obj = entry.obj
                            CameraPhotoTile(
                                obj = obj,
                                onClick = { CameraSession.openCameraPreview(obj) },
                                progress = CameraSession.transfer?.takeIf { it.filename == obj.filename && !it.finished }?.fraction,
                                downloaded = obj.handle in CameraSession.downloadSet,
                                onLongClick = { onSelectLongClick(obj.handle) },
                                selectMode = selectMode,
                                isSelected = obj.handle in selectedHandles,
                                onToggle = { onSelectToggle(obj.handle) },
                            )
                        }
                    }
                }
            }
            // 顶部滚动渐变：照片上滑时盖渐变遮罩，保证玻璃顶栏文字在浅色照片上可读（对齐本机页）
            if (camScrolled) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(140.dp).align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    colors.bgPrimary,
                                    colors.bgPrimary.copy(alpha = 0.8f),
                                    colors.bgPrimary.copy(alpha = 0.3f),
                                    Color.Transparent,
                                )
                            )
                        )
                )
            }
            }
        }
    }
}

@Composable
private fun CameraPhotoTile(
    obj: CameraObject,
    onClick: () -> Unit,
    progress: Float? = null,
    downloaded: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    selectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggle: (() -> Unit)? = null,
) {
    val colors = LocalLazyFilmColors.current
    LaunchedEffect(obj.handle) { CameraSession.requestThumbnail(obj.handle) }
    val thumb = CameraSession.thumbs[obj.handle]
    Box(
        modifier = Modifier.height(110.dp).clip(RoundedCornerShape(10.dp))
            .background(colors.bgCard)
            .combinedClickable(
                onClick = { if (selectMode) onToggle?.invoke() else onClick() },
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = obj.filename,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                if (obj.isVideo) AppIcons.Video else AppIcons.PhotoCamera,
                contentDescription = null,
                tint = colors.textDisabled,
                modifier = Modifier.size(26.dp)
            )
        }
        // 批量选择态：蓝色遮罩 + 右上勾选圈
        if (selectMode) {
            if (isSelected) {
                Box(Modifier.fillMaxSize().background(Color(0xFF2196F3).copy(alpha = 0.25f)))
            }
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) Modifier.background(Color(0xFF2196F3))
                        else Modifier.border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(AppIcons.Check, "已选", tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        // 格式角标：对齐 lazyfilm——无底色、右下角 8dp、10sp Bold；
        // JPG/MP4 白色，RAW 琥珀黄 #E6A817，MOV 浅蓝 #4FC3F7
        val (badgeText, badgeColor) = when {
            obj.filename.endsWith(".mov", true) -> "MOV" to Color(0xFF4FC3F7)
            obj.filename.endsWith(".mp4", true) -> "MP4" to Color.White
            obj.isRaw -> "RAW" to Color(0xFFE6A817)
            else -> "JPG" to Color.White
        }
        // 与左下已下载图标同一高度：同样 18dp 高 + 6dp 内缩的框
        Box(
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).height(18.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(badgeText, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        // 已下载角标：左下角、check-circle 图标
        if (downloaded) {
            Icon(
                AppIcons.CheckCircle, "已下载",
                tint = colors.brandPrimary,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(18.dp),
            )
        }
        if (progress != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = colors.brandPrimary
                )
            }
        }
    }
}

/**
 * 相机照片大图浮层：与本机详情同款交互（HorizontalPager 左右滑动 + 捏合/双击缩放 +
 * 单击切换工具栏）。zoomable 消费掉手势，杜绝点击穿透到下层照片列表的 bug。
 * 下载按钮内嵌填充式进度条；已下载点击弹二次确认后重新下载（样式对齐显影删除确认）。
 */
@Composable
fun CameraPhotoOverlay(initialObj: CameraObject, onClose: () -> Unit) {
    val colors = LocalLazyFilmColors.current
    val objects = CameraSession.objects.ifEmpty { listOf(initialObj) }
    val initialIndex = objects.indexOfFirst { it.handle == initialObj.handle }.coerceAtLeast(0)
    var chromeVisible by remember { mutableStateOf(true) }
    var showRedownload by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteToast by remember { mutableStateOf<String?>(null) }
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { objects.size })
    val current = objects.getOrNull(pagerState.currentPage) ?: initialObj

    // 翻页时预取当前页与相邻页缩略图
    LaunchedEffect(pagerState.currentPage) {
        val idx = pagerState.currentPage
        for (i in listOf(idx, idx + 1, idx - 1)) {
            objects.getOrNull(i)?.let { CameraSession.requestThumbnail(it.handle) }
        }
    }

    // 下载状态：transfer 按 filename 匹配；fillProgress 平滑推进（4MiB 分段间隔较大，直接画会一顿一顿）
    val transfer = CameraSession.transfer?.takeIf { it.filename == current.filename }
    val downloading = transfer != null && !transfer.finished && transfer.error == null
    val isDownloaded = current.handle in CameraSession.downloadSet || transfer?.finished == true
    val fillProgress = remember { Animatable(0f) }
    LaunchedEffect(transfer?.fraction) {
        if (downloading) fillProgress.animateTo(
            transfer?.fraction ?: 0f,
            animationSpec = tween(durationMillis = 450),
        )
    }
    LaunchedEffect(downloading, isDownloaded) {
        if (!downloading && !isDownloaded) fillProgress.snapTo(0f)
    }

    // 大图注册为 backdrop 层，供下方玻璃按钮/面板折射真实图片（而不是折射纯色画布导致实心）
    val photoBackdrop = rememberLayerBackdrop()
    // 图片层的兜底底色：无缩略图时折射到纯黑，依然是可用的 backdrop 源
    val panelBackdrop = rememberCanvasBackdrop { drawRect(Color(0xFF141414)) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 图片作为被折射层：layerBackdrop 注册坐标依赖，玻璃按钮才能透出下方图片
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().layerBackdrop(photoBackdrop)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val obj = objects[page]
            key(obj.handle) {
                val zoomState = rememberZoomState()
                val thumb = CameraSession.thumbs[obj.handle]
                Box(
                    modifier = Modifier.fillMaxSize().zoomable(
                        zoomState = zoomState,
                        // 单击切换工具栏显隐；双击缩放由 zoomable 默认逻辑处理（消费掉，不再穿透）
                        onTap = { chromeVisible = !chromeVisible },
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumb != null) {
                        Image(
                            bitmap = thumb,
                            contentDescription = obj.filename,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text("缩略图加载中…", color = colors.textTertiary, fontSize = 13.sp)
                    }
                }
            }
        }
        } // layerBackdrop(photoBackdrop) 包裹层结束

        // 顶栏：返回 + 文件信息 + 页码（点图显隐）
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 与本机详情同款：液态玻璃返回按钮
                GlassIconButton(AppIcons.ArrowLeft, "返回", photoBackdrop, onClick = onClose)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(current.filename, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        listOfNotNull(
                            if (current.size > 0) formatBytes(current.size) else null,
                            if (current.isVideo) "视频" else if (current.isRaw) "RAW" else "JPG",
                            current.captureDate.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "${pagerState.currentPage + 1} / ${objects.size}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                )
            }
        }

        // 底部：二次确认弹窗 + 下载按钮（点图显隐）
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { it / 2 },
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(animationSpec = tween(300)) { it / 2 },
        ) {
            Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                // 重新下载二次确认：锚在按钮上方（LiquidPanel + 确认/取消双圆钮）
                AnimatedVisibility(visible = showRedownload, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)) {
                    LiquidPanel(backdrop = panelBackdrop, shape = RoundedCornerShape(24.dp)) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text("重新下载这张照片？", color = Color.White, fontSize = 13.sp)
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlassIconButton(
                                    AppIcons.Download, "确认重新下载", panelBackdrop,
                                    onClick = {
                                        showRedownload = false
                                        CameraSession.download(current)
                                    },
                                    tint = colors.brandPrimary,
                                )
                                Spacer(Modifier.width(16.dp))
                                GlassIconButton(
                                    AppIcons.Close, "取消", panelBackdrop,
                                    onClick = { showRedownload = false },
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }
                // 删除二次确认（删除相机内照片，不可恢复）：锚在左下删除按钮上方（对齐详情页删除确认）
                AnimatedVisibility(visible = showDeleteConfirm, modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 88.dp)) {
                    LiquidPanel(backdrop = panelBackdrop, shape = RoundedCornerShape(24.dp)) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text("删除相机里的这张照片？", color = Color.White, fontSize = 13.sp)
                            Spacer(Modifier.height(3.dp))
                            Text("不可恢复，将直接从相机存储卡移除", color = colors.textTertiary, fontSize = 11.sp)
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlassIconButton(
                                    AppIcons.Delete, "确认删除", panelBackdrop,
                                    onClick = {
                                        showDeleteConfirm = false
                                        val obj = current
                                        CameraSession.deletePhoto(obj) { ok, msg ->
                                            deleteToast = if (ok) "已删除" else "删除失败：$msg"
                                            if (ok) onClose() // 删除成功关闭大图
                                        }
                                    },
                                    tint = colors.brandDanger,
                                    danger = true,
                                )
                                Spacer(Modifier.width(16.dp))
                                GlassIconButton(
                                    AppIcons.Close, "取消", panelBackdrop,
                                    onClick = { showDeleteConfirm = false },
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }
                if (deleteToast != null) {
                    Text(deleteToast!!, color = colors.textSecondary, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
                }
                if (transfer?.error != null) {
                    Text("下载失败：${transfer.error}", color = colors.brandDanger, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
                }
                // 液态玻璃按钮：删除=圆形，下载=药丸型（与连接相机按钮同款）
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
                    // 删除按钮（相机端删除）：圆形液态玻璃 + 危险色，二次确认
                    // 用 photoBackdrop 折射下方真实图片；surfaceColor 低透明度叠色（不用 tint，
                    // 因为 LiquidButton 内部 tint 会强制画 75% 不透明矩形导致按钮实心不透背景）
                    LiquidButton(
                        onClick = { showDeleteConfirm = true },
                        backdrop = photoBackdrop,
                        surfaceColor = colors.brandDanger.copy(alpha = 0.22f),
                        modifier = Modifier.size(48.dp),
                        height = 48.dp,
                        horizontalPadding = 0.dp,
                    ) {
                        Icon(AppIcons.Delete, "删除相机照片", tint = colors.brandDanger, modifier = Modifier.size(22.dp))
                    }
                    // 下载按钮：药丸液态玻璃（与连接相机按钮同款，折射照片）；下载中显示进度填充
                    LiquidButton(
                        onClick = {
                            if (!downloading) {
                                when {
                                    isDownloaded -> showRedownload = true
                                    else -> CameraSession.download(current)
                                }
                            }
                        },
                        backdrop = photoBackdrop,
                        surfaceColor = if (downloading || isDownloaded) {
                            colors.bgSurface.copy(alpha = 0.28f)
                        } else {
                            colors.brandPrimary.copy(alpha = 0.28f)
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        height = 48.dp,
                    ) {
                        if (downloading) {
                            Box(
                                modifier = Modifier.fillMaxWidth(fillProgress.value.coerceIn(0f, 1f))
                                    .fillMaxHeight().clip(RoundedCornerShape(50))
                                    .background(colors.brandPrimary.copy(alpha = 0.5f))
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isDownloaded) AppIcons.Check else AppIcons.Download,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    downloading -> "下载中 ${(fillProgress.value * 100).toInt()}%"
                                    isDownloaded -> "已下载"
                                    else -> "下载到本机"
                                },
                                color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────────── 相机页 ─────────────────────────────

@Composable
fun CameraScreen(nav: AppNav) {
    val colors = LocalLazyFilmColors.current
    val state = CameraSession.state
    val saved = CameraSession.savedCameras

    GlassPage(
        topBar = { backdrop ->
            GlassTopBar(title = "相机", backdrop = backdrop, onBack = { nav.pop() })
        },
        content = {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 64.dp)
                    .padding(bottom = 40.dp)
            ) {
                CameraListPane(nav)
            }
        }
    )
}

/** 相机列表内容：独立相机页与主页「连接相机」弹窗共用 */
@Composable
fun CameraListPane(nav: AppNav) {
    val colors = LocalLazyFilmColors.current
    val state = CameraSession.state
    val saved = CameraSession.savedCameras
    Column(modifier = Modifier.fillMaxWidth()) {
        if (state.connected || state.connecting) {
            SectionTitle("当前连接")
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.bgCard)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CameraModelIcon(state.model, Modifier.size(52.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            state.model.ifBlank { "尼康相机" },
                            color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium
                        )
                        // 主标题已是机型，小字只放电量/容量/IP
                        Text(
                            listOfNotNull(
                                state.battery?.let { "电量 $it%" },
                                state.freeBytes?.let { "剩余 ${formatBytes(it)}" },
                                state.host.takeIf { it.isNotBlank() },
                            ).joinToString(" · ").ifBlank { "已连接" },
                            color = colors.textTertiary, fontSize = 12.sp, maxLines = 1,
                        )
                    }
                    if (state.connected) {
                        // 断开连接：红色图标按钮，占据原来「监看」的位置
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape)
                                .background(colors.brandDanger.copy(alpha = 0.15f))
                                .clickable { CameraSession.disconnect() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                AppIcons.Close, contentDescription = "断开连接",
                                tint = colors.brandDanger, modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = colors.brandPrimary
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionTitle("已保存的相机")
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            // 添加相机放列表第一个
            PlaceholderCard(
                icon = AppIcons.Add,
                title = "添加相机",
                onClick = { nav.push(Route.CONNECT) }
            )
            Spacer(Modifier.height(8.dp))
            // 相机多时列表内部滚动，弹窗高度不再被撑爆
            Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                if (saved.isEmpty()) {
                    Text(
                        "还没有保存的相机。首次连接后会自动记住。",
                        color = colors.textTertiary, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                }
                saved.forEach { camera ->
                    SavedCameraCard(
                        camera = camera,
                        active = state.connected && state.host == camera.host,
                        connecting = state.connecting && state.host == camera.host,
                        onClick = { CameraSession.connect(camera.host) },
                        onForget = { CameraSession.forgetSaved(camera.id) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SavedCameraCard(
    camera: SavedCamera,
    active: Boolean,
    connecting: Boolean,
    onClick: () -> Unit,
    onForget: () -> Unit,
) {
    val colors = LocalLazyFilmColors.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.bgCard)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CameraModelIcon(camera.name, Modifier.size(56.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(camera.name, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                when {
                    connecting -> "连接中…"
                    active -> "已连接 · ${camera.host}"
                    else -> camera.host
                },
                color = if (active) colors.brandPrimary else colors.textTertiary,
                fontSize = 12.sp
            )
        }
        if (connecting) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.brandPrimary
            )
        } else {
            Text(
                "移除",
                color = colors.textTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(8.dp).clickable(onClick = onForget)
            )
        }
    }
}

// ───────────────────────────── 连接页 ─────────────────────────────

@Composable
fun ConnectScreen(nav: AppNav) {
    val colors = LocalLazyFilmColors.current
    val context = LocalContext.current
    val state = CameraSession.state
    val discovered = CameraSession.discovered
    val discovering = CameraSession.discovering

    var manualIp by remember { mutableStateOf("") }
    var connectMode by remember { mutableStateOf(UiPrefs.connectMode) }
    var localNetworkDenied by remember { mutableStateOf(false) }
    var localAddresses by remember { mutableStateOf(CameraSession.localAddresses()) }

    // 记录进入连接页时是否已连接：只有在本页「新连接成功」才自动跳回主页。
    // 关键：首次连接时相机尚未注册（registered=false / 受限会话），必须留在连接页
    // 让用户完成「首次注册」，否则自动跳主页会错过注册面板。
    val wasConnectedOnEnter = remember { state.connected }
    LaunchedEffect(state.connected, state.registered) {
        if (state.connected && !wasConnectedOnEnter && state.registered) nav.popToHome()
    }

    // Android 17+（API 37）本地网络权限；target 36 时本地网络由 INTERNET 隐式授权
    val localNetworkPermission = "android.permission.ACCESS_LOCAL_NETWORK"
    var permGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 37 ||
                context.checkSelfPermission(localNetworkPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permGranted = ok
        localNetworkDenied = !ok
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 37 && !permGranted) {
            permLauncher.launch(localNetworkPermission)
        }
        localAddresses = CameraSession.localAddresses()
    }

    val openHotspotSettings: () -> Unit = {
        val intents = listOf(
            Intent("android.settings.TETHER_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in intents) {
            try {
                context.startActivity(intent)
                break
            } catch (_: Exception) {
                // 试下一个
            }
        }
    }

    GlassPage(
        topBar = { backdrop ->
            GlassTopBar(title = "连接相机", backdrop = backdrop, onBack = { nav.pop() })
        },
        content = {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 64.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 130.dp)
            ) {
                if (localNetworkDenied) {
                    Text(
                        "本地网络权限被拒绝：系统会阻止访问手机热点下的相机。请到系统设置 → 应用 → NicoLink → 权限中允许「附近的设备 / 本地网络」。",
                        color = colors.brandDanger, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                }

                // 连接模式切换（AP / STA），选择持久化
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                    ModeOption(
                        label = "相机热点 (AP)",
                        sub = "相机开热点 · 直连",
                        selected = connectMode == "ap",
                        colors = colors,
                        modifier = Modifier.weight(1f),
                        onClick = { connectMode = "ap"; UiPrefs.connectMode = "ap"; com.filmlens.nicolink.camera.CameraSession.connectMode = "ap" },
                    )
                    Spacer(Modifier.width(8.dp))
                    ModeOption(
                        label = "手机热点 (STA)",
                        sub = "遥控 / 取景 / 浏览",
                        selected = connectMode == "sta",
                        colors = colors,
                        modifier = Modifier.weight(1f),
                        onClick = { connectMode = "sta"; UiPrefs.connectMode = "sta"; com.filmlens.nicolink.camera.CameraSession.connectMode = "sta" },
                    )
                }
                Spacer(Modifier.height(10.dp))

                if (connectMode == "ap") {
                    // AP 会话标准对象命令可读；STA 会话标准命令被锁，但已走尼康私有直读通道，
                    // 两种模式都能浏览/下载/取景/拍照，只是首次配置方式不同。
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.bgCard)
                            .padding(16.dp)
                    ) {
                        Text("AP 组网（相机开热点）", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        GuideStep(1, "相机：网络菜单 → 连接至智能设备 → Wi-Fi 连接 → 建立连接")
                        GuideStep(2, "记下相机屏幕上的 NIKON_XXXXXX 名称与 8 位密码")
                        GuideStep(3, "手机系统 Wi-Fi 连上该热点（提示无法上网时选「保持连接」）")
                        GuideStep(4, "回到本页点「搜索附近相机」→ 连接（相机地址通常为 192.168.1.1）")
                    }
                } else {
                    // STA 组网步骤
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.bgCard)
                            .padding(16.dp)
                    ) {
                        Text("STA 组网（首次配置一次即可）", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        GuideStep(1, "手机打开「个人热点」，带宽选 2.4GHz，记下名称与密码")
                        GuideStep(2, "相机：网络菜单 → 连接至智能设备 → Wi-Fi 连接 (STA mode) → 创建配置文件")
                        GuideStep(3, "选择手机热点 SSID → 输入密码 → 自动获取 IP")
                        GuideStep(4, "回到本页点「搜索附近相机」，选中后连接")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "开热点设置",
                            color = colors.brandPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable(onClick = openHotspotSettings)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "本机地址：" + localAddresses.joinToString().ifEmpty { "（无 IPv4，确认热点已开启）" },
                    color = colors.textTertiary, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                // 首次注册面板（放在「附近相机」上方，连上未注册时最显眼）：
                // 把本机 GUID 绑进相机的 Wi-Fi 配置文件（SnapBridge 式配对）
                if (CameraSession.registering ||
                    CameraSession.registerNote != null ||
                    (state.connected && !state.registered)
                ) {
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.bgCard)
                            .padding(16.dp)
                    ) {
                        Text(
                            "首次注册",
                            color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        if (CameraSession.registering) {
                            Text(
                                CameraSession.registerNote ?: "正在注册…",
                                color = colors.textSecondary, fontSize = 13.sp
                            )
                            CameraSession.pairingCode?.let { code ->
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    code,
                                    color = colors.brandPrimary, fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "配对码：与相机屏幕显示的数字核对一致后，在相机上按 OK 确认",
                                    color = colors.textTertiary, fontSize = 11.sp
                                )
                            }
                        } else {
                            CameraSession.registerNote?.let { note ->
                                Text(
                                    note,
                                    color = colors.brandDanger, fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            if (state.connected && !state.registered) {
                                Text(
                                    "相机把每个 Wi-Fi 配置文件绑定给一台主机。未注册时相机停在等待画面且有超时——" +
                                        "超时后会自动关闭 Wi-Fi、放弃本次配置文件，所以连上后请尽快注册。" +
                                        "注册成功后相机会重启 Wi-Fi（热点里相机掉线重连一次属正常现象）。",
                                    color = colors.textSecondary, fontSize = 12.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                GuideStep(1, "保持相机停在 Wi-Fi 连接画面，本页显示已连接")
                                GuideStep(2, "点下方按钮，在相机屏幕上核对配对码并按 OK")
                                GuideStep(3, "注册后相机重启 Wi-Fi，App 会自动重连")
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "注册到相机（首次）",
                                    color = colors.textInverse, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.brandPrimary)
                                        .clickable { CameraSession.registerCamera() }
                                        .padding(horizontal = 20.dp, vertical = 13.dp)
                                )
                            }
                        }
                    }
                }

                // 已发现
                SectionTitle("附近相机")
                if (discovering) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.brandPrimary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("正在扫描 mDNS 与 15740 端口…", color = colors.textSecondary, fontSize = 13.sp)
                    }
                }
                if (discovered.isEmpty() && !discovering) {
                    EmptyHint("未发现相机")
                }
                discovered.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.bgCard)
                            .clickable {
                                CameraSession.connect(item.host, item.port)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CameraModelIcon(item.name.ifBlank { item.host }, Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.name.ifBlank { item.host },
                                color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium
                            )
                            Text(
                                "${item.host}:${item.port} · ${if (item.via == "mdns") "Bonjour" else "子网扫描"}",
                                color = colors.textTertiary, fontSize = 11.sp
                            )
                        }
                        if (state.connecting && state.host == item.host) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.brandPrimary
                            )
                        } else {
                            Text("连接", color = colors.brandPrimary, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                SectionTitle("手动输入地址")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f).height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.bgCard)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it.trim() },
                            singleLine = true,
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                            cursorBrush = SolidColor(colors.brandPrimary),
                        ) { inner ->
                            if (manualIp.isEmpty()) {
                                Text("192.168.43.100", color = colors.textTertiary, fontSize = 14.sp)
                            }
                            inner()
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "连接",
                        color = colors.textInverse, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (manualIp.isBlank()) colors.bgControl else colors.brandPrimary
                            )
                            .clickable(enabled = manualIp.isNotBlank()) {
                                CameraSession.connect(manualIp)
                            }
                            .padding(horizontal = 20.dp, vertical = 13.dp)
                    )
                }

                if (state.status != "未连接" && state.status.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        state.status,
                        color = when {
                            state.status.startsWith("连接失败") -> colors.brandDanger
                            state.status.startsWith("已连接") -> colors.brandPrimary
                            else -> colors.textSecondary
                        },
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        },
        overlay = { backdrop ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                GlassPrimaryButton(
                    text = if (discovering) "搜索中…" else "搜索附近相机",
                    backdrop = backdrop,
                    icon = AppIcons.AutoAwesome,
                    modifier = Modifier.padding(bottom = 24.dp), // 与主页「连接相机」按钮同一高度
                    onClick = { if (!discovering) CameraSession.discover(mode = connectMode) }
                )
            }
        }
    )
}

@Composable
private fun ModeOption(
    label: String,
    sub: String,
    selected: Boolean,
    colors: com.filmlens.nicolink.ui.theme.LazyFilmColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.brandPrimary else colors.bgCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            color = if (selected) colors.textInverse else colors.textPrimary,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold
        )
        Text(
            sub,
            color = if (selected) colors.textInverse.copy(alpha = 0.75f) else colors.textTertiary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun GuideStep(index: Int, text: String) {
    val colors = LocalLazyFilmColors.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(18.dp).clip(RoundedCornerShape(9.dp))
                .background(colors.brandDark2),
            contentAlignment = Alignment.Center
        ) {
            Text("$index", color = colors.brandLight1, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

// ───────────────────────────── 监看页 ─────────────────────────────

/**
 * 构图网格叠加（移植 lazyfilm GridOverlay）：0=关 / 1=九宫格 3×3 / 2=黄金比例+黄金螺旋。
 * 无 click 修饰符，事件穿透到手势层。
 */
@Composable
private fun GridOverlay(gridMode: Int, modifier: Modifier = Modifier) {
    if (gridMode == 0) return
    val lineColor = Color.White.copy(alpha = 0.35f)
    Canvas(modifier.fillMaxSize()) {
        val stroke = 1.dp.toPx()
        val fractions = if (gridMode == 1) listOf(1f / 3f, 2f / 3f) else listOf(0.382f, 0.618f)
        fractions.forEach { f ->
            drawLine(lineColor, Offset(size.width * f, 0f), Offset(size.width * f, size.height), stroke)
            drawLine(lineColor, Offset(0f, size.height * f), Offset(size.width, size.height * f), stroke)
        }
        if (gridMode == 2) {
            // 黄金螺旋（对齐 lazyfilm 正确算法）：先取画面内最大黄金矩形（居中、方向随画面），
            // 再递归切分——黄金矩形每切一刀剩余仍是黄金矩形，左→顶→右→底轮转恒对齐，
            // 1/4 圆弧在分割点相切连续、向收敛点内旋。
            val phi = 1.618f
            val rectW: Float
            val rectH: Float
            if (size.width >= size.height) {
                if (size.width / size.height >= phi) { rectH = size.height; rectW = phi * rectH }
                else { rectW = size.width; rectH = rectW / phi }
            } else {
                if (size.height / size.width >= phi) { rectW = size.width; rectH = phi * rectW }
                else { rectH = size.height; rectW = rectH / phi }
            }
            val minSide = 8.dp.toPx()
            var l = (size.width - rectW) / 2f
            var t = (size.height - rectH) / 2f
            var r = l + rectW
            var b = t + rectH
            var dir = if (rectW >= rectH) 0 else 1
            var step = 0
            val arcStyle = Stroke(width = stroke, cap = StrokeCap.Round)
            while (step < 12) {
                val s = minOf(r - l, b - t)
                if (s < minSide) break
                when (dir % 4) {
                    0 -> drawArc(lineColor, 180f, 90f, false, Offset(l, b - s), androidx.compose.ui.geometry.Size(2 * s, 2 * s), style = arcStyle)
                    1 -> drawArc(lineColor, 270f, 90f, false, Offset(l - s, t), androidx.compose.ui.geometry.Size(2 * s, 2 * s), style = arcStyle)
                    2 -> drawArc(lineColor, 0f, 90f, false, Offset(r - 2 * s, t - s), androidx.compose.ui.geometry.Size(2 * s, 2 * s), style = arcStyle)
                    3 -> drawArc(lineColor, 90f, 90f, false, Offset(l, b - 2 * s), androidx.compose.ui.geometry.Size(2 * s, 2 * s), style = arcStyle)
                }
                when (dir % 4) {
                    0 -> l += s; 1 -> t += s; 2 -> r -= s; 3 -> b -= s
                }
                dir++; step++
            }
        }
    }
}

/**
 * 电子水平仪叠加：用相机自身倾斜传感器（0xD067 AngleLevel = 横滚）画随相机倾斜的水平线。
 * @param rollDeg 横滚角（度，已解码为 -180~180）；null 表示无数据
 * 水平基准：把读数归一化到 -180~180，0° 即水平。线随 roll 反向旋转（相机顺时针倾斜→线逆时针补偿）。
 */
@Composable
private fun LevelOverlay(rollDeg: Float?, modifier: Modifier = Modifier) {
    if (rollDeg == null) return
    val colors = LocalLazyFilmColors.current
    // 水平判定：roll 在 ±1.5° 内视为已水平，线由绿变品牌黄并加粗提示
    val isLevel = kotlin.math.abs(rollDeg) <= 1.5f
    // 617 机身水平标线：常显黄铜黄半透明；水平时切亮黄加粗（NicoLink 原有逻辑）
    val levelColor = if (isLevel) colors.brandContrast else Color(0xFFE8C63A).copy(alpha = 0.5f)
    Canvas(modifier.fillMaxSize()) {
        val roll = rollDeg
        val lineW = if (isLevel) 3.dp.toPx() else 1.dp.toPx()
        val lineHalf = size.minDimension * 0.32f
        val cy = size.height / 2f
        val cx = size.width / 2f
        // 中心水平线（随 roll 旋转）：rotate 画布后画一条水平线
        rotate(degrees = -roll, pivot = Offset(cx, cy)) {
            drawLine(levelColor.copy(alpha = 0.9f), Offset(cx - lineHalf, cy), Offset(cx + lineHalf, cy), lineW)
            // 两端小刻度
            drawLine(levelColor.copy(alpha = 0.7f), Offset(cx - lineHalf, cy - 10.dp.toPx()), Offset(cx - lineHalf, cy + 10.dp.toPx()), lineW)
            drawLine(levelColor.copy(alpha = 0.7f), Offset(cx + lineHalf, cy - 10.dp.toPx()), Offset(cx + lineHalf, cy + 10.dp.toPx()), lineW)
            // 617 新增：1/4 与 3/4 处短刻度 ±5dp + 中心圆环 r=8dp
            listOf(cx - lineHalf / 2f, cx + lineHalf / 2f).forEach { x ->
                drawLine(levelColor.copy(alpha = 0.7f), Offset(x, cy - 5.dp.toPx()), Offset(x, cy + 5.dp.toPx()), lineW)
            }
            drawCircle(levelColor.copy(alpha = 0.8f), radius = 8.dp.toPx(), center = Offset(cx, cy), style = Stroke(1.dp.toPx()))
        }
    }
}

/** 实时直方图叠加：把 256 桶亮度绘成 64 柱，峰值归一化，首末桶顶格提示死黑/溢出。 */
@Composable
private fun HistogramOverlay(hist: HistogramData, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val bars = 64
        val step = 256 / bars
        val peak = maxOf(1, hist.peak)
        val barW = size.width / bars
        for (i in 0 until bars) {
            var sum = 0
            for (j in 0 until step) sum += hist.bins[i * step + j]
            val ratio = (sum.toFloat() / peak).coerceIn(0f, 1f)
            val bh = ratio * size.height
            val x = i * barW
            // 死黑/高光溢出柱顶格用亮色提示
            val color = if ((i == 0 && hist.bins[0] > peak * 0.5f) ||
                (i == bars - 1 && hist.bins[255] > peak * 0.5f)) {
                Color.White.copy(alpha = 0.9f)
            } else {
                Color.White.copy(alpha = 0.55f)
            }
            drawRect(color, Offset(x, size.height - bh), androidx.compose.ui.geometry.Size(barW * 0.8f, bh))
        }
    }
}

/** 参数条上的单个参数胶囊（只读显示，点击可扩展性预留） */
@Composable
private fun ParamPill(label: String, value: String?, modifier: Modifier = Modifier) {
    // 617 LCD 刻度窗：黑底绿字 + Monospace 宽字距
    Box(
        modifier.clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF0F130D))
            .border(1.dp, Color(0xFF6B6860), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color(0xFF7E9B73), fontSize = 7.sp, letterSpacing = 1.sp)
            Text(
                value ?: "--", color = Color(0xFF9BFF8A), fontSize = 15.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp,
            )
        }
    }
}

@Composable
fun MonitorScreen(nav: AppNav) {
    val colors = LocalLazyFilmColors.current
    val state = CameraSession.state
    val frame = CameraSession.liveFrame
    val histogram = CameraSession.liveHistogram
    val exposure = CameraSession.exposureInfo
    val transfer = CameraSession.transfer
    val autoTransfer = CameraSession.autoTransfer
    val autoDevelop = CameraSession.autoDevelopProgress
    var hadConnection by remember { mutableStateOf(state.connected) }
    var gridMode by remember { mutableIntStateOf(0) } // 0=关 1=九宫格 2=黄金
    var showLevel by remember { mutableStateOf(false) } // 电子水平仪（相机传感器）
    var showTimerPicker by remember { mutableStateOf(false) } // 定时选项面板
    var showIntervalPicker by remember { mutableStateOf(false) } // 间隔选项面板
    // P2 拍摄增强状态
    val captureMode = CameraSession.captureMode
    val timerSeconds = CameraSession.timerSeconds
    val intervalSeconds = CameraSession.intervalSeconds
    val intervalTaken = CameraSession.intervalTaken
    val countdown = CameraSession.captureCountdown
    val videoRecording = CameraSession.videoRecording
    val displayRemote = CameraSession.displayRemote
    val captureMessage = CameraSession.captureMessage
    val focusPoint = CameraSession.focusPoint
    val bulbSeconds = CameraSession.bulbSeconds
    val bulbActive = CameraSession.bulbActive
    // 滑块草稿值：拖动时只更新本地，松手才提交，避免中途反复启停
    var timerDraft by remember { mutableStateOf(timerSeconds.toFloat()) }
    var intervalDraft by remember { mutableStateOf(intervalSeconds.toFloat()) }
    var bulbInput by remember { mutableStateOf(bulbSeconds.toString()) }
    LaunchedEffect(timerSeconds) { timerDraft = timerSeconds.toFloat() }
    LaunchedEffect(intervalSeconds) { intervalDraft = intervalSeconds.toFloat() }
    LaunchedEffect(bulbSeconds) { bulbInput = bulbSeconds.toString() }
    val activeTint = colors.brandLight1
    val hapticView = LocalView.current
    val hapticEnabled = LocalHapticEnabled.current
    val hapticTap: () -> Unit = { vibrateTap(hapticView, hapticEnabled) }

    LaunchedEffect(captureMessage) {
        if (captureMessage != null) {
            delay(4000)
            CameraSession.clearCaptureMessage()
        }
    }

    LaunchedEffect(state.connected) {
        if (state.connected) hadConnection = true else if (hadConnection) nav.pop()
    }

    DisposableEffect(Unit) {
        if (!state.restricted) CameraSession.startLiveView()
        onDispose { CameraSession.stopLiveView() }
    }

    GlassPage(
        topBar = { backdrop ->
            // C 方向：轻量顶栏，只留返回 + 小字铭牌（不再用大块钛银铭牌）
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(AppIcons.ArrowLeft, "返回", backdrop, onClick = { nav.pop() })
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "NICO LINK · MONITOR", fontSize = 10.sp,
                        color = colors.brandLight1, letterSpacing = 2.sp, fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.size(44.dp))
            }
        },
        content = {
            // 竖排：取景窗（仅画面+网格）在下移的位置，其余信息全部放下方空余区
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Spacer(Modifier.height(56.dp)) // 顶栏返回按钮占位
                // 传输/显影状态窗：固定在取景器上方，常驻占位；有任务显示进度，空闲显示文字
                val t = transfer
                val activeTransfer = t?.takeIf { !it.finished && it.error == null }
                val isAuto = autoTransfer.active
                val progress: Float? = if (isAuto) {
                    if (activeTransfer?.filename == autoTransfer.currentName) activeTransfer.fraction else 0f
                } else {
                    activeTransfer?.fraction
                }
                val hasTask = isAuto || activeTransfer != null || t?.error != null || autoDevelop.active
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(104.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC0A0908))
                        .border(1.dp, Color(0xFF8C6D2F), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (isAuto || activeTransfer != null) {
                        val label = if (isAuto) {
                            "自动传输 ${autoTransfer.currentName.ifBlank { "准备中…" }}" +
                                if (autoTransfer.remaining > 1) " · 队列 ${autoTransfer.remaining} 张" else ""
                        } else {
                            "传输 ${activeTransfer!!.filename}"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = Color(0xFFE8DCC0), fontSize = 11.sp,
                                fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f))
                            Text("${((progress ?: 0f) * 100).toInt()}%", color = Color(0xFF9BFF8A),
                                fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress ?: 0f },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = colors.brandPrimary, trackColor = Color(0xFF3A3220),
                        )
                    } else if (t?.error != null) {
                        Text("传输失败：${t.error}", color = colors.brandDanger, fontSize = 11.sp)
                    }
                    if (autoDevelop.active) {
                        if (isAuto || activeTransfer != null) Spacer(Modifier.height(6.dp))
                        Text(
                            "自动显影 ${autoDevelop.currentName.ifBlank { "处理中…" }}" +
                                if (autoDevelop.remaining > 0) " · 队列 ${autoDevelop.remaining} 张" else "",
                            color = Color(0xFFE8DCC0), fontSize = 11.sp, fontFamily = FontFamily.Serif,
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = colors.brandLight1, trackColor = Color(0xFF3A3220),
                        )
                    }
                    if (!hasTask) {
                        Text("相机就绪 · 传输空闲", color = Color(0xFF8A877F), fontSize = 11.sp,
                            fontFamily = FontFamily.Serif, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                // 取景窗：严格按实际帧比例（动态读取 ImageBitmap 宽高），Fit 不裁切 → 网格与画面像素对齐
                val frameAspect = if (frame != null && frame.height > 0) {
                    frame.width.toFloat() / frame.height.toFloat()
                } else 640f / 424f
                // 617 机身化：取景窗嵌在凹槽金属 bezel 里（渐变框 + 1dp 描边 + 7dp 内衬 + 拉丝）
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF4A4845), Color(0xFF232220), Color(0xFF3A3835))
                            )
                        )
                        .border(1.dp, Color(0xFF5C5A56), RoundedCornerShape(10.dp))
                        .padding(7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    XpanBrushedMetalOverlay(Modifier.matchParentSize())
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .aspectRatio(frameAspect)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.viewfinderBackground),
                        contentAlignment = Alignment.Center
                    ) {
                    if (frame != null) {
                        Image(
                            bitmap = frame, contentDescription = "实时取景", contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val nx = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val ny = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                    CameraSession.touchFocus(nx, ny)
                                }
                            },
                        )
                        ViewfinderGlassOverlay(Modifier.fillMaxSize())
                        GridOverlay(gridMode)
                        FocusOverlay(focusPoint, Modifier.fillMaxSize())
                        if (showLevel) {
                            // AngleLevel 归一化到 -180~180：>180 减 360
                            val raw = CameraSession.levelRaw.angleLevel
                            val roll = raw?.let { if (it > 180 * 65536) it / 65536f - 360f else it / 65536f }
                            LevelOverlay(roll, Modifier.fillMaxSize())
                        }
                        // 定时拍摄倒计时（画面中央大数字）
                        if (countdown > 0) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "$countdown", color = Color.White,
                                    fontSize = 92.sp, fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        // 录像指示
                        if (videoRecording) {
                            Row(
                                modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(colors.brandDanger))
                                Spacer(Modifier.width(5.dp))
                                Text("REC", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (state.restricted) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
                            Text("相机在等待首次注册", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "未注册的配置文件只开放注册指令，取景不可用。\n请返回连接页点「注册到相机（首次）」，在相机屏幕上确认配对码后再进本页。",
                                color = colors.textSecondary, fontSize = 12.sp
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (CameraSession.liveActive) "正在等待取景帧…" else "启动实时取景…",
                                color = colors.textTertiary, fontSize = 13.sp
                            )
                            CameraSession.liveError?.let { err ->
                                Spacer(Modifier.height(8.dp))
                                Text(err, color = colors.brandDanger, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = colors.brandPrimary)
                        }
                    }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // C 方向：直方图 + 状态面板紧贴横屏取景帧（黄铜窗框）
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xB30A0908))
                        .padding(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 直方图高度 = 状态窗(24dp)×2 + 间隙(4dp)
                        Box(
                            Modifier.width(100.dp).height(52.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF141210))
                                .border(1.dp, Color(0xFF8C6D2F), RoundedCornerShape(4.dp))
                        ) { HistogramOverlay(histogram, Modifier.padding(3.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            StatusChip { Text(state.model.ifBlank { "Nikon Z30" }, color = Color(0xFFE8DCC0), fontSize = 12.sp, fontFamily = FontFamily.Serif) }
                            Spacer(Modifier.height(4.dp))
                            StatusChip {
                                BatteryIcon(state.battery)
                                Spacer(Modifier.width(5.dp))
                                Text("${state.battery ?: "--"}%", color = Color(0xFFE8DCC0), fontSize = 12.sp, fontFamily = FontFamily.Serif)
                                Spacer(Modifier.width(10.dp))
                                Icon(AppIcons.SdCard, contentDescription = "容量", tint = Color(0xFFC9A227), modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(state.freeBytes?.let { formatBytes(it) } ?: "--", color = Color(0xFFE8DCC0), fontSize = 12.sp, fontFamily = FontFamily.Serif)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // C 方向：参数单独一排（LCD 窗）
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ParamPill("模式", exposure.program, Modifier.weight(1f))
                    ParamPill("光圈", exposure.aperture, Modifier.weight(1f))
                    ParamPill("快门", exposure.shutter, Modifier.weight(1f))
                    ParamPill("ISO", exposure.iso, Modifier.weight(1f))
                    ParamPill("白平衡", exposure.whiteBalance, Modifier.weight(1f))
                }
                Spacer(Modifier.height(58.dp)) // 给底部控制留位（空档让给上方进度窗）
            }
        },
        overlay = { backdrop ->
            Box(modifier = Modifier.fillMaxSize()) {
                // 底部信息/控件合并为一个纵向列：参数条、提示、功能按钮、快门不再各自 bottom 锚定造成重叠
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .navigationBarsPadding().padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // P2 拍摄增强提示（定时/间隔/录像/画面模式）—— LCD 小窗框住
                    captureMessage?.let { msg ->
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF0F130D))
                                .border(1.dp, Color(0xFF6B6860), RoundedCornerShape(4.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(msg, color = Color(0xFF9BFF8A), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    // 功能行（用户指定顺序）：网格 / 水平仪 / 画面联动 / 定时拍摄 / 间隔拍摄 / 模式切换
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassIconButton(
                            if (gridMode == 1) AppIcons.Grid else if (gridMode == 2) AppIcons.GridGolden else AppIcons.Grid,
                            "网格", backdrop, onClick = { hapticTap(); gridMode = (gridMode + 1) % 3 },
                            tint = if (gridMode != 0) activeTint else Color.White,
                        )
                        GlassIconButton(
                            AppIcons.GridGolden, "水平仪", backdrop,
                            onClick = { hapticTap(); showLevel = !showLevel },
                            tint = if (showLevel) activeTint else Color.White,
                        )
                        GlassIconButton(
                            AppIcons.Link, "画面 ${if (displayRemote) "遥控" else "联动"}", backdrop,
                            onClick = { hapticTap(); CameraSession.toggleDisplayMode() },
                            tint = if (!displayRemote) activeTint else Color.White,
                        )
                        GlassIconButton(
                            AppIcons.Timer,
                            if (captureMode == "bulb") "B门 ${bulbSeconds}s" else "定时 ${timerSeconds}s",
                            backdrop,
                            onClick = {
                                hapticTap()
                                showIntervalPicker = false
                                showTimerPicker = !showTimerPicker
                            },
                            tint = if ((if (captureMode == "bulb") bulbSeconds > 0 else timerSeconds > 0) || showTimerPicker) activeTint else Color.White,
                        )
                        GlassIconButton(
                            AppIcons.CameraBurst, "间隔 ${intervalSeconds}s", backdrop,
                            onClick = {
                                hapticTap()
                                showTimerPicker = false
                                showIntervalPicker = !showIntervalPicker
                            },
                            tint = if (intervalSeconds > 0 || showIntervalPicker) activeTint else Color.White,
                        )
                        GlassIconButton(
                            when (captureMode) {
                                "video" -> AppIcons.Video
                                "bulb" -> AppIcons.CameraIris
                                else -> AppIcons.PhotoCamera
                            },
                            "模式切换", backdrop,
                            onClick = {
                                hapticTap()
                                val next = when (captureMode) {
                                    "photo" -> "video"
                                    "video" -> "bulb"
                                    else -> "photo"
                                }
                                CameraSession.switchCaptureMode(next)
                            },
                            tint = if (captureMode != "photo") activeTint else Color.White,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val shutterDanger = captureMode != "video" || !videoRecording
                    LiquidButton(
                        onClick = {
                            vibrateTap(hapticView, hapticEnabled)
                            CameraSession.capture()
                        },
                        backdrop = backdrop, modifier = Modifier.size(76.dp), height = 76.dp, horizontalPadding = 0.dp,
                        tint = Color.Unspecified,
                        surfaceColor = Color.Unspecified,
                    ) {
                        // 617 铜质快门：双层径向渐变 + 3dp 铜边 + 内圈
                        Box(
                            modifier = Modifier.size(72.dp).clip(CircleShape)
                                .border(3.dp, Color(0xFF8A5F32), CircleShape)
                                .background(Brush.radialGradient(listOf(Color(0xFFD9A96B), Color(0xFF9A6B38)))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                    .background(Brush.radialGradient(listOf(Color(0xFFE7BC85), Color(0xFFB07C44)))),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    when (captureMode) {
                                        "video" -> if (videoRecording) AppIcons.Stop else AppIcons.Video
                                        "bulb" -> AppIcons.CameraIris
                                        else -> AppIcons.PhotoCamera
                                    },
                                    contentDescription = when (captureMode) {
                                        "video" -> "录像"
                                        "bulb" -> "B 门"
                                        else -> "拍照"
                                    },
                                    tint = if (shutterDanger) Color(0xFF4E4C47) else colors.brandDark1,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // 快门状态文字也装进 LCD 小窗
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF0F130D))
                            .border(1.dp, Color(0xFF6B6860), RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            when {
                                bulbActive -> "B 门开曝中…"
                                captureMode == "bulb" -> "B门开曝 ${bulbSeconds}s"
                                captureMode == "video" && videoRecording -> "录像中 · 点按结束"
                                captureMode == "video" -> "开始录像"
                                intervalSeconds > 0 -> "间隔 ${intervalSeconds}s · 已拍 $intervalTaken 张"
                                timerSeconds > 0 -> "定时 ${timerSeconds}s 后拍照"
                                else -> "拍照"
                            },
                            color = Color(0xFF9BFF8A), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                // 定时/间隔选项：悬浮面板（与删除二次确认同款 LiquidPanel），锚在功能行上方
                AnimatedVisibility(
                    visible = showTimerPicker || showIntervalPicker,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 182.dp),
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    LiquidPanel(backdrop = backdrop, shape = RoundedCornerShape(24.dp)) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            // 实色背景：避免底层参数/快门文字从玻璃面板透出
                            modifier = Modifier.background(Color(0xFF1C1B19)).padding(horizontal = 18.dp, vertical = 14.dp),
                        ) {
                            val isBulb = showTimerPicker && captureMode == "bulb"
                            Text(
                                when {
                                    !showTimerPicker -> "间隔拍摄"
                                    isBulb -> "B 门时长（秒）"
                                    else -> "定时拍摄"
                                },
                                color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(10.dp))
                            if (isBulb) {
                                // B 门：输入框自定义秒数
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BasicTextField(
                                        value = bulbInput,
                                        onValueChange = { v -> bulbInput = v.filter { it.isDigit() }.take(3) },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = Color(0xFF9BFF8A), fontSize = 16.sp,
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        modifier = Modifier.width(90.dp)
                                            .background(Color(0xFF0F130D))
                                            .border(1.dp, Color(0xFF6B6860), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("秒", color = colors.textSecondary, fontSize = 12.sp)
                                    Spacer(Modifier.width(14.dp))
                                    Text(
                                        "确定", color = colors.brandLight1, fontSize = 12.sp,
                                        modifier = Modifier.clickable {
                                            bulbInput.toIntOrNull()?.coerceIn(1, 300)?.let {
                                                CameraSession.chooseBulbSeconds(it)
                                            }
                                            showTimerPicker = false
                                        }.padding(8.dp),
                                    )
                                }
                            } else {
                                // 定时 / 间隔：0（关闭）~30 秒滑块
                                val draft = if (showTimerPicker) timerDraft else intervalDraft
                                Text(
                                    if (draft.roundToInt() == 0) "0 秒（关闭）" else "${draft.roundToInt()} 秒",
                                    color = Color(0xFF9BFF8A), fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                                )
                                Spacer(Modifier.height(6.dp))
                                Slider(
                                    value = draft,
                                    onValueChange = { v ->
                                        if (showTimerPicker) timerDraft = v else intervalDraft = v
                                    },
                                    onValueChangeFinished = {
                                        val v = (if (showTimerPicker) timerDraft else intervalDraft).roundToInt()
                                        if (showTimerPicker) CameraSession.setTimer(v) else CameraSession.setInterval(v)
                                        showTimerPicker = false
                                        showIntervalPicker = false
                                    },
                                    valueRange = 0f..30f,
                                    steps = 29,
                                    modifier = Modifier.width(220.dp),
                                )
                                Text("0 = 关闭 · 最大 30 秒", color = colors.textTertiary, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    )
}

/** 617 拉丝金属：固定种子 0x617L 预生成 120 条，无逐帧闪烁（lazyfilm 3572–3605） */
private data class XpanStreak(val y: Float, val x: Float, val len: Float, val alpha: Float, val light: Boolean)

private val XPAN_STREAKS: List<XpanStreak> by lazy {
    val r = Random(0x617L)
    List(120) {
        XpanStreak(
            y = r.nextFloat(),
            x = r.nextFloat(),
            len = 0.04f + r.nextFloat() * 0.14f,
            alpha = 0.02f + r.nextFloat() * 0.04f,
            light = r.nextFloat() < 0.45f,
        )
    }
}

@Composable
private fun XpanBrushedMetalOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        XPAN_STREAKS.forEach { s ->
            val color = if (s.light) Color.White.copy(s.alpha) else Color.Black.copy(s.alpha)
            drawLine(
                color,
                Offset(s.x * size.width, s.y * size.height),
                Offset((s.x + s.len).coerceAtMost(1f) * size.width, s.y * size.height),
                1.5.dp.toPx(),
            )
        }
    }
}

/** 617 取景屏玻璃高光 + 四边暗角（lazyfilm 3374–3398），放在帧图上、网格之下 */
@Composable
private fun ViewfinderGlassOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(0.05f), Color.Transparent)), Offset(0f, 0f), Size(w, h * 0.10f))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.34f))), Offset(0f, h * 0.90f), Size(w, h * 0.10f))
        drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(0.20f), Color.Transparent)), Offset(0f, 0f), Size(w * 0.05f, h))
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(0.20f))), Offset(w * 0.95f, 0f), Size(w * 0.05f, h))
        drawRect(Color.White.copy(0.08f), Offset.Zero, Size(w, h), style = Stroke(1.dp.toPx()))
    }
}

/** 617 四角括号对焦框（60dp、角长 14dp、1.5dp Round）：弹簧弹入 1.5→1，黄铜水平黄 */
@Composable
private fun FocusOverlay(point: Pair<Float, Float>?, modifier: Modifier = Modifier) {
    if (point == null) return
    val anim = remember { Animatable(1f) }
    LaunchedEffect(point) {
        anim.snapTo(1.5f)
        anim.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }
    Canvas(modifier) {
        val cx = point.first * size.width
        val cy = point.second * size.height
        val half = 30.dp.toPx() * anim.value
        val corner = 14.dp.toPx() * anim.value
        val color = Color(0xFFE8C63A)
        val strokeW = 1.5.dp.toPx()
        drawLine(color, Offset(cx - half, cy - half + corner), Offset(cx - half, cy - half), strokeW, cap = StrokeCap.Round)
        drawLine(color, Offset(cx - half, cy - half), Offset(cx - half + corner, cy - half), strokeW, cap = StrokeCap.Round)
        drawLine(color, Offset(cx + half - corner, cy - half), Offset(cx + half, cy - half), strokeW, cap = StrokeCap.Round)
        drawLine(color, Offset(cx + half, cy - half), Offset(cx + half, cy - half + corner), strokeW, cap = StrokeCap.Round)
        drawLine(color, Offset(cx - half, cy + half - corner), Offset(cx - half, cy + half), strokeW, cap = StrokeCap.Round)
        drawLine(color, Offset(cx - half, cy + half), Offset(cx - half + corner, cy + half), strokeW, cap = StrokeCap.Round)
        drawLine(color, Offset(cx + half - corner, cy + half), Offset(cx + half, cy + half), strokeW, cap = StrokeCap.Round)
        drawLine(color, Offset(cx + half, cy + half - corner), Offset(cx + half, cy + half), strokeW, cap = StrokeCap.Round)
    }
}

/** 状态小窗：固定 24dp 高 + 黄铜边框，供状态面板右侧两行使用 */
@Composable
private fun StatusChip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.height(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF0A0908))
            .border(1.dp, Color(0xFF8C6D2F), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/** 电量图标：按电量显示不同填充与颜色（>35 绿 / 16-35 黄 / <=15 红），无数据灰色 */
@Composable
private fun BatteryIcon(level: Int?, modifier: Modifier = Modifier) {
    val palette = LocalLazyFilmColors.current
    val tint = when {
        level == null -> Color(0xFF8A877F)
        level <= 15 -> palette.brandDanger
        level <= 35 -> palette.brandContrast
        else -> Color(0xFF9BFF8A)
    }
    Canvas(modifier.size(18.dp, 10.dp)) {
        val bodyW = size.width - 3.dp.toPx()
        drawRect(tint.copy(alpha = 0.9f), Offset(0f, 0f), Size(bodyW, size.height), style = Stroke(1.dp.toPx()))
        val frac = (level ?: 0).coerceIn(0, 100) / 100f
        drawRect(tint, Offset(1.dp.toPx(), 1.dp.toPx()), Size((bodyW - 2.dp.toPx()) * frac, size.height - 2.dp.toPx()))
        drawRect(tint, Offset(bodyW, size.height * 0.3f), Size(2.dp.toPx(), size.height * 0.4f))
    }
}

@Composable
private fun InfoChip(text: String) {
    val colors = LocalLazyFilmColors.current
    // 617 黄铜计数小窗：暗底 + 黄铜边 + 奶油衬线字
    Box(
        modifier = Modifier.clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF0A0908))
            .border(1.dp, Color(0xFF8C6D2F), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = Color(0xFFE8DCC0), fontSize = 12.sp, fontFamily = FontFamily.Serif)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format("%.1f GB", bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> String.format("%.0f MB", bytes / 1024.0 / 1024)
    bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
