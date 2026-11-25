package com.filmlens.nicolink.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmlens.nicolink.data.MonthGridEntry
import com.filmlens.nicolink.data.PhotoItem
import com.filmlens.nicolink.data.PhotoRepository
import com.filmlens.nicolink.data.buildMonthGrid
import com.filmlens.nicolink.film.DevelopEdits
import com.filmlens.nicolink.film.FilmParams
import com.filmlens.nicolink.film.LocalFilmBridge
import com.filmlens.nicolink.film.defaultWire
import com.filmlens.nicolink.camera.CameraSession
import com.filmlens.nicolink.ui.AppNav
import com.filmlens.nicolink.ui.Route
import com.filmlens.nicolink.ui.UiPrefs
import com.filmlens.nicolink.ui.shared.EmptyHint
import com.filmlens.nicolink.ui.shared.GlassIconButton
import com.filmlens.nicolink.ui.shared.GlassPage
import com.filmlens.nicolink.ui.shared.GlassTopBar
import com.filmlens.nicolink.ui.shared.MonthTimelineOverlay
import com.filmlens.nicolink.ui.shared.PhotoTile
import com.filmlens.nicolink.ui.shared.PlaceholderCard
import com.filmlens.nicolink.ui.shared.SectionTitle
import com.filmlens.nicolink.ui.shared.liquid.LiquidBottomTab
import com.filmlens.nicolink.ui.shared.liquid.LiquidBottomTabs
import com.filmlens.nicolink.ui.shared.liquid.LiquidButton
import com.filmlens.nicolink.ui.shared.liquid.LiquidPanel
import com.filmlens.nicolink.ui.theme.AppIcons
import com.filmlens.nicolink.ui.theme.LocalLazyFilmColors
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ───────────────────────────────── 主页 ─────────────────────────────────

/** 主页标题字体：assets 内置 Pacifico（对齐 lazyfilm 的 logo 连笔字体，失败回退 Serif） */
fun loadPacifico(context: android.content.Context): FontFamily = try {
    val dir = java.io.File(context.cacheDir, "nicolink_fonts").apply { mkdirs() }
    val f = java.io.File(dir, "Pacifico-Regular.ttf")
    context.assets.open("rollplay_fonts/Pacifico-Regular.ttf").use { input ->
        f.outputStream().use { input.copyTo(it) }
    }
    FontFamily(android.graphics.Typeface.createFromFile(f))
} catch (_: Exception) {
    FontFamily.Serif
}

@Composable
fun HomeScreen(
    nav: AppNav,
    onExitApp: () -> Unit = {},
    onPhotosImported: (Int) -> Unit = {},
) {
    val colors = LocalLazyFilmColors.current
    var tab by remember { mutableStateOf(UiPrefs.lastTab) } // 0=本机 1=相机；记住上次所在 Tab
    var showCameraPopup by remember { mutableStateOf(false) } // 相机列表弹窗（替代独立相机页）
    val camConnected = CameraSession.state.connected
    // 弹窗内点已保存相机连接成功后自动关闭
    LaunchedEffect(camConnected) {
        if (camConnected && showCameraPopup) showCameraPopup = false
    }

    // Tab 切换即持久化：从其他页面回来或重开 App 都落在上次的 Tab
    LaunchedEffect(tab) { UiPrefs.lastTab = tab }

    // 本机照片：只读 App 专属目录（无需任何存储权限，参考 lazyfilm）
    val context = LocalContext.current
    val bridge = LocalFilmBridge.current
    val scope = rememberCoroutineScope()
    // ── 批量选择：本机用展示名，相机用句柄；两套互斥，选择态由集合非空派生 ──
    var selectedLocal by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedCamera by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showBatchExportPanel by remember { mutableStateOf(false) }
    var batchExportOriginal by remember { mutableStateOf(true) }
    var batchExportFormat by remember { mutableStateOf("jpg") }
    var batchExportQuality by remember { mutableIntStateOf(UiPrefs.exportQuality) }
    // 批量处理中的模态进度（删除/导出期间屏蔽一切操作）
    var batchJobActive by remember { mutableStateOf(false) }
    var batchJobLabel by remember { mutableStateOf("") }
    var batchDone by remember { mutableIntStateOf(0) }
    var batchTotal by remember { mutableIntStateOf(0) }
    var batchToast by remember { mutableStateOf<String?>(null) }
    val isSelectionActive = selectedLocal.isNotEmpty() || selectedCamera.isNotEmpty()
    val batchCount = if (selectedLocal.isNotEmpty()) selectedLocal.size else selectedCamera.size

    fun clearBatchSelection() {
        selectedLocal = emptySet()
        selectedCamera = emptySet()
        showBatchDeleteConfirm = false
        showBatchExportPanel = false
    }
    fun enterLocalSelection(name: String) {
        if (isSelectionActive) return
        selectedCamera = emptySet()
        selectedLocal = setOf(name)
    }
    fun toggleLocalSelection(name: String) {
        selectedLocal = if (name in selectedLocal) selectedLocal - name else selectedLocal + name
    }
    fun enterCameraSelection(handle: Int) {
        if (isSelectionActive) return
        selectedLocal = emptySet()
        selectedCamera = setOf(handle)
    }
    fun toggleCameraSelection(handle: Int) {
        selectedCamera = if (handle in selectedCamera) selectedCamera - handle else selectedCamera + handle
    }

    LaunchedEffect(batchToast) {
        if (batchToast != null) {
            delay(2400)
            batchToast = null
        }
    }

    // 照片列表从 AppNav 持久缓存读取：页面重建（相机 Tab 切回 / 详情返回）不再同步扫盘造成卡顿
    val photos = nav.photosCache
    // M2：相机下载完成后刷新的计数（下载写入 App 目录后本机流要重读）
    val downloadedTick = CameraSession.downloadedTick
    // 月/季/年分组网格：照片流与右缘时间轴共用（索引换算给时间轴跳转用）
    val monthGrid = remember(photos) { buildMonthGrid(photos) }
    // 时间轴与照片流共享滚动状态（提升到 AppNav 持久持有，进详情返回后不丢失滚动位置）
    val gridState = nav.homeGridState
    // 照片流是否已滚动（滚动时在顶部盖渐变遮罩，保证左上角顶栏文字在浅色照片上可读）
    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 ||
                (gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset > 0)
        }
    }
    // 相机下载 或 详情页删除照片后都重读 App 目录（后台线程，不阻塞 UI）
    // 首次进入靠 key 初始值触发一次；之后从相机 Tab 切回 / 详情返回 key 不变 → 不重复扫盘
    val photosVersion = nav.photosVersion
    LaunchedEffect(downloadedTick, photosVersion) {
        val loaded = withContext(Dispatchers.IO) { PhotoRepository.loadPhotos(context) }
        nav.updatePhotosCache(loaded)
    }

    // 系统图片选择器（Photo Picker，无需 READ_MEDIA_IMAGES 权限），支持批量导入到 App 专属目录
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val n = PhotoRepository.importPhotos(context, uris)
            if (n > 0) {
                nav.bumpPhotos() // 触发上面的 LaunchedEffect 重读本机照片流
                onPhotosImported(n) // 热力图：导入照片计入当天
            }
        }
    }

    // ── 批量操作 ──
    fun performBatchDelete() {
        showBatchDeleteConfirm = false
        if (selectedLocal.isNotEmpty()) {
            val targets = photos.filter { it.displayName in selectedLocal }
            batchJobActive = true
            batchJobLabel = "正在删除本机照片…"
            batchDone = 0
            batchTotal = targets.size
            scope.launch(Dispatchers.IO) {
                targets.forEachIndexed { i, photo ->
                    PhotoRepository.deletePhoto(context, photo)
                    withContext(Dispatchers.Main) { batchDone = i + 1 }
                }
                withContext(Dispatchers.Main) {
                    batchJobActive = false
                    clearBatchSelection()
                    nav.bumpPhotos()
                    batchToast = "已删除 ${targets.size} 张"
                }
            }
        } else if (selectedCamera.isNotEmpty()) {
            val objs = CameraSession.objects.filter { it.handle in selectedCamera }
            batchJobActive = true
            batchJobLabel = "正在删除相机照片…"
            batchDone = 0
            batchTotal = objs.size
            CameraSession.deletePhotos(
                objs = objs,
                onProgress = { done -> batchDone = done },
            ) { _, _, msg ->
                batchJobActive = false
                clearBatchSelection()
                batchToast = msg
            }
        }
    }

    fun performBatchExport() {
        val targets = photos.filter { it.displayName in selectedLocal }
        if (targets.isEmpty()) return
        showBatchExportPanel = false
        batchJobActive = true
        batchJobLabel = if (batchExportOriginal) "正在导出原图…" else "正在处理并导出…"
        batchDone = 0
        batchTotal = targets.size
        scope.launch(Dispatchers.IO) {
            var ok = 0
            var failed = 0
            targets.forEachIndexed { i, photo ->
                try {
                    // 视频无法走显影引擎，始终按原图导出
                    if (batchExportOriginal || photo.isVideo) {
                        bridge?.exportOriginal(photo.uri.toString(), photo.displayName)
                    } else {
                        val edit = DevelopEdits.load(context, photo.displayName)
                        bridge?.export(
                            photo.uri.toString(),
                            edit?.filmId ?: "kodak_portra_400",
                            edit?.paperId ?: "kodak_portra_endura",
                            FilmParams(wire = edit?.wire ?: defaultWire()),
                            batchExportFormat,
                            batchExportQuality,
                        )
                    }
                    ok++
                } catch (t: Throwable) {
                    failed++
                    android.util.Log.e("NicoLinkExport", "导出失败 ${photo.displayName}", t)
                }
                withContext(Dispatchers.Main) { batchDone = i + 1 }
            }
            withContext(Dispatchers.Main) {
                batchJobActive = false
                clearBatchSelection()
                batchToast = if (failed > 0) "已导出 $ok 张，失败 $failed 张" else "已导出 $ok 张到相册"
            }
        }
    }

    fun performBatchDownload() {
        val objs = CameraSession.objects.filter { it.handle in selectedCamera }
        CameraSession.downloadPhotos(objs)
        clearBatchSelection()
        batchToast = "已开始下载 ${objs.size} 张"
    }

    Box(modifier = Modifier.fillMaxSize()) {
    GlassPage(
        topBar = { backdrop ->
            val pacifico = remember(context) { loadPacifico(context) }
            GlassTopBar(
                title = "NicoLink",
                backdrop = backdrop,
                titleContent = {
                    androidx.compose.material3.Text(
                        text = "NicoLink",
                        fontFamily = pacifico,
                        fontSize = 24.sp,
                        color = colors.textPrimary,
                        // Pacifico 是静态连笔字体，横向缩放拉宽（不用 letterSpacing 会断连笔）；左锚点避免溢出
                        modifier = Modifier.graphicsLayer {
                            scaleX = 1.15f
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        },
                    )
                },
                actions = {
                    GlassIconButton(
                        AppIcons.Cog, "设置", backdrop,
                        onClick = { nav.push(Route.SETTINGS) },
                        enabled = !isSelectionActive,
                        tint = if (isSelectionActive) colors.textDisabled else colors.textPrimary,
                    )
                }
            )
        },
        // content 只放普通内容（被 layerBackdrop 记录、供玻璃折射），不含任何玻璃控件
        content = {
            if (tab == 0) {
                if (photos.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 200.dp)) {
                        EmptyHint("本机暂无照片（请从相机导入）")
                    }
                } else {
                    // 照片流 + 顶部滚动渐变（普通绘制，不调用 backdrop，无递归环风险）
                    Box(modifier = Modifier.fillMaxSize()) {
                    // 照片流从屏幕顶部开始绘制，用 contentPadding(top) 让位给玻璃顶栏/tab；
                    // 滚动时照片从 tab/顶栏下方穿透显示，玻璃折射到真实照片（对齐 lazyfilm）。
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        state = gridState,
                        // top 200dp：给玻璃顶栏/tab 留更宽松留白，首排照片不被遮挡（用户反馈 168dp 太近）
                        contentPadding = PaddingValues(
                            start = 10.dp, end = 10.dp, top = 176.dp, bottom = 100.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = monthGrid.entries,
                            key = { entry ->
                                when (entry) {
                                    is MonthGridEntry.Header -> "header_${entry.key}"
                                    is MonthGridEntry.Photo -> entry.photo.uri.toString()
                                }
                            },
                            span = { entry ->
                                when (entry) {
                                    is MonthGridEntry.Header -> GridItemSpan(3)
                                    is MonthGridEntry.Photo -> GridItemSpan(1)
                                }
                            }
                        ) { entry ->
                            when (entry) {
                                is MonthGridEntry.Header -> Text(
                                    text = entry.label,
                                    color = colors.textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)
                                )
                                is MonthGridEntry.Photo -> PhotoTile(
                                    photo = entry.photo,
                                    onClick = { nav.openPhoto(entry.photo, photos) },
                                    thumbVersion = nav.photosVersion,
                                    onLongClick = { enterLocalSelection(entry.photo.displayName) },
                                    selectMode = selectedLocal.isNotEmpty(),
                                    isSelected = entry.photo.displayName in selectedLocal,
                                    onToggle = { toggleLocalSelection(entry.photo.displayName) },
                                )
                            }
                        }
                    }
                        // 顶部滚动渐变：照片上滑时盖一层从 bgPrimary 到透明的遮罩，
                        // 保证左上角顶栏文字在浅色照片上仍清晰（对齐 lazyfilm）。
                        // 普通绘制、不调用 backdrop，放在 content 内不会触发递归环。
                        if (isScrolled) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(140.dp)
                                    .align(Alignment.TopCenter)
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
            } else {
                // M2：相机照片流（未连接时引导去连接）
                CameraTabContent(
                    nav = nav,
                    selectMode = selectedCamera.isNotEmpty(),
                    selectedHandles = selectedCamera,
                    onSelectLongClick = { enterCameraSelection(it) },
                    onSelectToggle = { toggleCameraSelection(it) },
                )
            }
        },
        // overlay 放玻璃控件（兄弟浮层，折射 content，无递归环）
        overlay = { backdrop ->
            // 对齐 lazyfilm 最终修复版：rememberUpdatedState + CanvasBackdrop 底色兜底 + 半宽无外部 height
            val currentTab by rememberUpdatedState(tab)
            val pageBgBackdrop = rememberCanvasBackdrop { drawRect(colors.bgPrimary) }
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶部居中的 glass tab
                Box(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 68.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LiquidBottomTabs(
                        selectedTabIndex = { tab },
                        onTabSelected = { index -> if (!isSelectionActive && index != currentTab) tab = index },
                        backdrop = rememberCombinedBackdrop(pageBgBackdrop, backdrop),
                        tabsCount = 2,
                        accentColor = colors.brandPrimary,
                        enabled = !isSelectionActive,
                        modifier = Modifier.fillMaxWidth(0.5f).padding(horizontal = 20.dp)
                    ) {
                        val tabs = listOf("本机", "相机")
                        tabs.forEachIndexed { index, text ->
                            LiquidBottomTab(
                                onClick = { if (!isSelectionActive && index != currentTab) tab = index },
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Text(text, color = colors.textPrimary, fontSize = 14.sp)
                            }
                        }
                    }
                }
                // 右缘时间轴（仅本机 Tab）：玻璃药丸是浮层，折射 content，不进被记录层
                if (tab == 0 && photos.isNotEmpty() && !isSelectionActive) {
                    MonthTimelineOverlay(
                        monthGrid = monthGrid,
                        gridState = gridState,
                        backdrop = backdrop,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                    )
                }
                // 相机 Tab：连接相机按钮（液态玻璃，放底部）。
                // 已连接时动画收缩成右下角圆形图标钮（长条胶囊 ↔ 圆形），断开还原长条；
                // 图标用 Link 链条（与设置齿轮区分）。
                if (tab == 1 && !isSelectionActive) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize().navigationBarsPadding()
                    ) {
                        val connected = CameraSession.state.connected
                        val fullWidth = maxWidth - 32.dp
                        // 连接后收成正圆：宽高都 52.dp（与下方 .height(52.dp) 一致），避免 56×52 的椭圆
                        val targetWidth = if (connected) 52.dp else fullWidth
                        // 右缘固定在 maxWidth-16dp，宽度收缩 + 左缘右移 → 长条滑向右下收成圆形
                        val targetStart = if (connected) (maxWidth - 68.dp) else 16.dp
                        val btnWidth by animateDpAsState(
                            targetValue = targetWidth,
                            animationSpec = tween(durationMillis = 380),
                            label = "connectWidth",
                        )
                        val startPad by animateDpAsState(
                            targetValue = targetStart,
                            animationSpec = tween(durationMillis = 380),
                            label = "connectStart",
                        )
                        LiquidButton(
                            onClick = { showCameraPopup = true },
                            backdrop = backdrop,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = startPad, bottom = 24.dp)
                                .width(btnWidth)
                                .height(52.dp),
                            tint = colors.brandPrimary,
                            surfaceColor = colors.bgSurface.copy(alpha = 0.25f),
                            horizontalPadding = 0.dp,
                        ) {
                            Crossfade(
                                targetState = connected,
                                animationSpec = tween(durationMillis = 380),
                                label = "connectContent",
                            ) { isConn ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                ) {
                                    Icon(
                                        AppIcons.Link,
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    if (!isConn) {
                                        Text(
                                            "连接相机",
                                            color = colors.textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // 本机 tab 右下角导入按钮（位置与相机连接按钮收起后一致：52dp 圆形，right≈16dp，bottom 24dp）
                if (tab == 0 && !isSelectionActive) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize().navigationBarsPadding()
                    ) {
                        LiquidButton(
                            onClick = {
                                importLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                    )
                                )
                            },
                            backdrop = backdrop,
                            modifier = Modifier.align(Alignment.BottomStart)
                                .padding(start = maxWidth - 68.dp, bottom = 24.dp)
                                .width(52.dp).height(52.dp),
                            tint = colors.brandPrimary,
                            surfaceColor = colors.bgSurface.copy(alpha = 0.25f),
                            horizontalPadding = 0.dp,
                        ) {
                            Icon(
                                AppIcons.Add,
                                contentDescription = "导入图片",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                // 批量选择栏：底部滑入，替代原来的主页悬浮按钮（对齐 lazyfilm）
                if (isSelectionActive) {
                    val selectionBackdrop = rememberCombinedBackdrop(pageBgBackdrop, backdrop)
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .navigationBarsPadding().padding(bottom = 24.dp),
                    ) {
                        LiquidPanel(backdrop = selectionBackdrop, shape = RoundedCornerShape(50)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.height(52.dp).padding(horizontal = 8.dp),
                            ) {
                                GlassIconButton(
                                    AppIcons.Close, "取消", selectionBackdrop,
                                    onClick = { clearBatchSelection() }, inPanel = true,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "已选 $batchCount 张", color = colors.textPrimary,
                                    fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                )
                                GlassIconButton(
                                    AppIcons.Delete, "删除", selectionBackdrop,
                                    onClick = { showBatchDeleteConfirm = true },
                                    tint = colors.brandDanger, inPanel = true, danger = true,
                                )
                                Spacer(Modifier.width(6.dp))
                                GlassIconButton(
                                    AppIcons.Download,
                                    if (selectedLocal.isNotEmpty()) "导出" else "下载",
                                    selectionBackdrop,
                                    onClick = {
                                        if (selectedLocal.isNotEmpty()) showBatchExportPanel = true else performBatchDownload()
                                    },
                                    inPanel = true,
                                )
                            }
                        }
                    }
                }
                // 批量删除二次确认（玻璃胶囊，锚在选择栏上方）
                if (showBatchDeleteConfirm) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .navigationBarsPadding().padding(bottom = 92.dp),
                    ) {
                        LiquidPanel(
                            backdrop = rememberCombinedBackdrop(pageBgBackdrop, backdrop),
                            shape = RoundedCornerShape(22.dp),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text("删除选中的 $batchCount 张？", color = colors.textPrimary, fontSize = 13.sp)
                                Text(
                                    if (selectedLocal.isNotEmpty()) "本机照片将被永久删除，不可恢复" else "相机内照片将被永久删除，不可恢复",
                                    color = colors.textTertiary, fontSize = 11.sp,
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    GlassIconButton(
                                        AppIcons.Delete, "确认删除", backdrop,
                                        onClick = { performBatchDelete() },
                                        tint = colors.brandDanger, inPanel = true, danger = true,
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    GlassIconButton(
                                        AppIcons.Close, "取消", backdrop,
                                        onClick = { showBatchDeleteConfirm = false }, inPanel = true,
                                    )
                                }
                            }
                        }
                    }
                }
                // 批量导出设置弹窗：与照片详情「导出设置」完全一致（格式/质量/原图导出/取消/确认）
                if (showBatchExportPanel) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
                            .clickable { showBatchExportPanel = false },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        LiquidPanel(
                            backdrop = rememberCombinedBackdrop(pageBgBackdrop, backdrop),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.padding(horizontal = 12.dp)
                                .navigationBarsPadding().padding(bottom = 12.dp).clickable { },
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                                    .widthIn(min = 300.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        "导出设置 · $batchCount 张", color = colors.textPrimary,
                                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "✕", color = colors.textSecondary, fontSize = 16.sp,
                                        modifier = Modifier.clip(CircleShape)
                                            .clickable { showBatchExportPanel = false }.padding(4.dp),
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Text("格式", color = colors.textSecondary, fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("jpg", "tiff", "dng").forEach { fmt ->
                                        val selected = batchExportFormat == fmt
                                        LiquidButton(
                                            onClick = { batchExportFormat = fmt },
                                            backdrop = backdrop, height = 40.dp, inPanel = true,
                                            tint = if (selected) colors.brandPrimary else Color.Unspecified,
                                            surfaceColor = if (selected) Color.Unspecified else colors.bgSurface.copy(alpha = 0.5f),
                                        ) {
                                            Text(fmt.uppercase(), color = colors.textPrimary, fontSize = 13.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                        }
                                    }
                                }
                                if (batchExportFormat == "jpg") {
                                    Spacer(Modifier.height(12.dp))
                                    Text("JPG 质量（默认 95）", color = colors.textSecondary, fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth())
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(80, 90, 95, 100).forEach { q ->
                                            val selected = batchExportQuality == q
                                            LiquidButton(
                                                onClick = { batchExportQuality = q },
                                                backdrop = backdrop, height = 40.dp, inPanel = true,
                                                tint = if (selected) colors.brandPrimary else Color.Unspecified,
                                                surfaceColor = if (selected) Color.Unspecified else colors.bgSurface.copy(alpha = 0.5f),
                                            ) {
                                                Text("$q", color = colors.textPrimary, fontSize = 13.sp,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // 原图导出：不做任何修改，直接复制 App 目录原图到相册
                                    LiquidButton(
                                        onClick = {
                                            batchExportOriginal = true
                                            performBatchExport()
                                        },
                                        backdrop = backdrop, height = 44.dp, inPanel = true, horizontalPadding = 8.dp,
                                    ) {
                                        Text("原图导出", color = colors.textPrimary, fontSize = 13.sp)
                                    }
                                    LiquidButton(
                                        onClick = {
                                            batchExportOriginal = false
                                            performBatchExport()
                                        },
                                        backdrop = backdrop, tint = colors.brandPrimary, height = 44.dp,
                                        inPanel = true, horizontalPadding = 8.dp,
                                    ) {
                                        Text("确认导出", color = colors.textPrimary, fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                // 批量处理中模态进度：删除/导出期间屏蔽一切操作
                if (batchJobActive) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f))
                            .clickable { },
                        contentAlignment = Alignment.Center,
                    ) {
                        LiquidPanel(
                            backdrop = rememberCombinedBackdrop(pageBgBackdrop, backdrop),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(horizontal = 32.dp).clickable { },
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CircularProgressIndicator(color = colors.brandPrimary)
                                Spacer(Modifier.height(14.dp))
                                Text(batchJobLabel, color = colors.textPrimary, fontSize = 14.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("$batchDone / $batchTotal", color = colors.textSecondary, fontSize = 12.sp)
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { if (batchTotal > 0) batchDone.toFloat() / batchTotal else 0f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = colors.brandPrimary, trackColor = colors.bgCard,
                                )
                            }
                        }
                    }
                }
                // 批量操作结果轻提示
                batchToast?.let { msg ->
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .navigationBarsPadding().padding(bottom = 160.dp),
                    ) {
                        LiquidPanel(
                            backdrop = rememberCombinedBackdrop(pageBgBackdrop, backdrop),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text(msg, color = colors.textPrimary, fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                        }
                    }
                }
                // 相机列表弹窗：底部弹出的液态玻璃面板（用真实 backdrop 折射主页内容）
                if (showCameraPopup) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                            .clickable { showCameraPopup = false },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        LiquidPanel(
                            backdrop = rememberCanvasBackdrop { drawRect(colors.bgCard) },
                            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                            modifier = Modifier.fillMaxWidth().height(520.dp).navigationBarsPadding().clickable { },
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "连接相机", color = colors.textPrimary,
                                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "关闭", color = colors.brandLight1, fontSize = 13.sp,
                                        modifier = Modifier.clickable { showCameraPopup = false }.padding(8.dp),
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                CameraListPane(nav)
                            }
                        }
                    }
                }
            }
        }
    )
    val cameraPreview = CameraSession.cameraPreview
    if (cameraPreview != null) {
        CameraPhotoOverlay(
            initialObj = cameraPreview,
            onClose = { CameraSession.closeCameraPreview() }
        )
    }
    }
}
