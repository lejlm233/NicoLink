package com.filmlens.nicolink.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.FileProvider
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.filmlens.nicolink.data.PhotoRepository
import com.filmlens.nicolink.data.cloud.AccountRepository
import com.filmlens.nicolink.data.cloud.HeatmapManager
import com.filmlens.nicolink.film.FilmParams
import com.filmlens.nicolink.film.FilmPresets
import com.filmlens.nicolink.film.DevelopEdits
import com.filmlens.nicolink.film.EditState
import com.filmlens.nicolink.film.FILM_NONE
import com.filmlens.nicolink.film.PAPER_NONE
import com.filmlens.nicolink.film.Preset
import com.filmlens.nicolink.film.PresetShare
import com.filmlens.nicolink.film.LocalFilmBridge
import com.filmlens.nicolink.film.StockOption
import com.filmlens.nicolink.film.METER_CHOICES
import com.filmlens.nicolink.film.METER_VALUES
import com.filmlens.nicolink.film.PARAM_TABS
import com.filmlens.nicolink.film.ParamKind
import com.filmlens.nicolink.film.ParamSpec
import com.filmlens.nicolink.film.ParamTab
import com.filmlens.nicolink.film.defaultWire
import com.filmlens.nicolink.film.isAdvanced
import com.filmlens.nicolink.ui.AppNav
import com.filmlens.nicolink.ui.DevelopSnapshot
import com.filmlens.nicolink.ui.LocalAnimatedVisibilityScope
import com.filmlens.nicolink.ui.LocalSharedTransitionScope
import com.filmlens.nicolink.ui.Route
import com.filmlens.nicolink.ui.shared.GlassIconButton
import com.filmlens.nicolink.ui.shared.GlassPage
import com.filmlens.nicolink.ui.shared.GlassThumbSlider
import com.filmlens.nicolink.ui.shared.GlassPrimaryButton
import com.filmlens.nicolink.ui.shared.GlassTopBar
import com.filmlens.nicolink.ui.shared.PlaceholderCard
import com.filmlens.nicolink.ui.shared.SectionTitle
import com.filmlens.nicolink.ui.shared.liquid.LiquidBottomTab
import com.filmlens.nicolink.ui.shared.liquid.LiquidBottomTabs
import com.filmlens.nicolink.ui.shared.liquid.LiquidButton
import com.filmlens.nicolink.ui.UiPrefs
import com.filmlens.nicolink.ui.shared.liquid.LiquidMorphPanel
import com.filmlens.nicolink.ui.shared.liquid.LiquidPanel
import com.filmlens.nicolink.ui.shared.liquid.LiquidToggle
import com.filmlens.nicolink.ui.theme.AppIcons
import com.filmlens.nicolink.ui.theme.LocalLazyFilmColors
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

// ───────────────────────────── 预览页 ─────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PreviewScreen(nav: AppNav) {
    val colors = LocalLazyFilmColors.current
    val context = LocalContext.current
    val bridge = LocalFilmBridge.current
    val scope = rememberCoroutineScope()
    val photos = nav.previewPhotos
    val initial = nav.previewIndex
    // 英雄视图：与列表缩略图共享同一 key（photo.uri），返回时缩回原位
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    // 透明玻璃浮层是否显示：点图片切换（仿 Google Photos）
    var chromeVisible by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }       // 查看参数面板
    var showDeleteConfirm by remember { mutableStateOf(false) } // 删除二次确认胶囊
    var showExportPanel by remember { mutableStateOf(false) } // 导出配置面板
    var showPlayer by remember { mutableStateOf(false) }      // App 内视频播放器
    var videoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            videoPlayer?.release()
            videoPlayer = null
        }
    }
    var exportFormat by remember { mutableStateOf(UiPrefs.exportFormat) } // 记住上次格式
    var exportQuality by remember { mutableStateOf(UiPrefs.exportQuality) } // 记住上次质量
    var toast by remember { mutableStateOf<String?>(null) }   // 操作结果轻提示
    var busy by remember { mutableStateOf(false) }

    // HorizontalPager 每页一个 Zoomable；未缩放时单指横向拖动让给 pager 切图
    val pagerState = rememberPagerState(initialPage = initial, pageCount = { photos.size })

    // 滑动停止时把当前索引同步回 nav（供返回后状态一致）
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && photos.isNotEmpty()) {
            nav.syncPreviewIndex(pagerState.currentPage)
        }
    }

    // toast 3 秒自动消失
    LaunchedEffect(toast) {
        if (toast != null) { delay(3000); toast = null }
    }

    val currentPhoto = photos.getOrNull(pagerState.currentPage)
    // 顶部信息（文件名/大小/格式/日期），与相机详情同款
    val currentInfo = remember(currentPhoto?.displayName) {
        currentPhoto?.let { PhotoRepository.photoInfo(context, it) }
    }

    // 导出：复用显影页最近一次调节快照参数，没有则用默认参数（逻辑与显影页导出一致）
    val doExport: (String, Int) -> Unit = { format, quality ->
        val photo = currentPhoto
        if (photo == null || bridge == null) {
            toast = "导出失败：引擎未就绪或未选照片"
        } else {
            scope.launch {
                busy = true
                // 按当前照片取显影参数，避免 A 图参数串到 B 图导出
                val edit = DevelopEdits.load(context, photo.displayName)
                val filmId = edit?.filmId ?: "kodak_portra_400"
                val paperId = edit?.paperId ?: "kodak_portra_endura"
                val params = FilmParams(wire = edit?.wire ?: defaultWire())
                // 记住本次参数
                UiPrefs.exportFormat = format
                UiPrefs.exportQuality = quality
                val result = withContext(Dispatchers.IO) {
                    try { "已导出：" + bridge.export(photo.uri.toString(), filmId, paperId, params, format, quality) }
                    catch (t: Throwable) { "导出失败：${t.message}" }
                }
                toast = result; busy = false
            }
        }
    }

    // 原图导出：不做任何修改，直接把 App 目录的原始图片复制到相册 DCIM/NicoLink
    val doExportOriginal: () -> Unit = {
        val photo = currentPhoto
        if (photo == null || bridge == null) { toast = "导出失败：引擎未就绪或未选照片" }
        else {
            scope.launch {
                busy = true
                val result = withContext(Dispatchers.IO) {
                    try { "已导出原图：" + bridge.exportOriginal(photo.uri.toString(), photo.displayName) }
                    catch (t: Throwable) { "导出失败：${t.message}" }
                }
                toast = result; busy = false
            }
        }
    }

    // 分享：当前显示的图（有调节用调节后，无调节 RAW 先经引擎转 JPG），用 FileProvider 授权
    val doShare: () -> Unit = {
        val photo = currentPhoto
        if (photo == null) { toast = "未选照片" }
        else {
            scope.launch {
                busy = true
                val shareFile = withContext(Dispatchers.IO) {
                    try {
                        val thumb = PhotoRepository.editThumbFile(context, photo.displayName)
                        when {
                            photo.isVideo -> File(PhotoRepository.appPictureDir(context), photo.displayName)
                            thumb.exists() -> thumb // 调节后的派生 JPG
                            photo.isRaw && bridge != null -> {
                                // RAW 无调节缩略图：引擎渲染 PNG → 转 JPG（其他 app 才能识别）
                                val edit = DevelopEdits.load(context, photo.displayName)
                                val png = bridge.render(
                                    photo.uri.toString(),
                                    edit?.filmId ?: "kodak_portra_400",
                                    edit?.paperId ?: "kodak_portra_endura",
                                    FilmParams(wire = edit?.wire ?: defaultWire())
                                )
                                if (png != null) {
                                    val bmp = BitmapFactory.decodeByteArray(png, 0, png.size)
                                    val out = File(context.cacheDir, "share_${photo.displayName.substringBeforeLast('.')}.jpg")
                                    out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                                    out
                                } else null
                            }
                            else -> File(PhotoRepository.appPictureDir(context), photo.displayName) // 普通 JPG 原图
                        }
                    } catch (_: Throwable) { null }
                }
                if (shareFile != null && shareFile.exists()) {
                    try {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = if (photo.isVideo) photo.mimeType else "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, if (photo.isVideo) "分享视频" else "分享照片"))
                    } catch (t: Throwable) { toast = "分享失败：${t.message}" }
                } else toast = "分享失败：无法准备图片"
                busy = false
            }
        }
    }

    // 视频：App 内 Media3 播放（Apache-2.0，不碰 GPL 显影引擎）
    val openVideo: () -> Unit = {
        val photo = currentPhoto
        if (photo == null || !photo.isVideo) {
            toast = "这不是视频"
        } else {
            if (videoPlayer == null) videoPlayer = ExoPlayer.Builder(context).build()
            showPlayer = true
        }
    }

    // 备用：用系统播放器打开（FileProvider 授权 App 专属目录里的 .mov）
    val openVideoSystem: () -> Unit = {
        val photo = currentPhoto
        if (photo != null && photo.isVideo) {
            try {
                val file = File(PhotoRepository.appPictureDir(context), photo.displayName)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, photo.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "播放视频"))
            } catch (t: Throwable) {
                toast = "无法播放视频：${t.message}"
            }
        }
    }

    // 删除：删原图 + 调节缓存 + 刷新主页 + 返回
    val doDelete: () -> Unit = {
        val photo = currentPhoto
        if (photo != null) {
            val ok = PhotoRepository.deletePhoto(context, photo)
            nav.bumpPhotos()
            showDeleteConfirm = false
            toast = if (ok) "已删除照片" else "删除失败"
            nav.pop()
        }
    }

    GlassPage(
        // 预览页不套 GlassTopBar：浮层按钮在 overlay 里自定义（透明玻璃）
        topBar = {},
        content = {
            if (photos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有选中的照片", color = colors.textTertiary, fontSize = 14.sp)
                }
            } else {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    // 每张照片独立缩放状态；key 保证重建时状态隔离
                    key(photos[page].uri) {
                        val zoomState = rememberZoomState()
                        // 按 displayName 实时查缩略图：不依赖 nav.previewPhotos 的旧快照，
                        // 显影页保存后这里一定能拿到最新的编辑图（三页一致）。
                        val liveThumb = remember(photos[page].displayName, nav.photosVersion) {
                            PhotoRepository.editThumbFile(context, photos[page].displayName)
                                .let { if (it.exists()) Uri.fromFile(it) else null }
                        }
                        val videoThumb = remember(photos[page].displayName, nav.photosVersion) {
                            if (photos[page].isVideo) PhotoRepository.videoThumbnail(context, photos[page].displayName) else null
                        }
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (photos[page].isVideo) {
                                // 视频：显示首帧 + 播放角标，点按调系统播放器
                                if (videoThumb != null) {
                                    Image(
                                        bitmap = videoThumb,
                                        contentDescription = photos[page].displayName,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().clickable(onClick = openVideo),
                                    )
                                } else {
                                    Icon(
                                        AppIcons.Video, contentDescription = "视频",
                                        tint = colors.textDisabled, modifier = Modifier.size(64.dp).clickable(onClick = openVideo),
                                    )
                                }
                                Box(
                                    modifier = Modifier.size(64.dp).clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = openVideo),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(AppIcons.Play, contentDescription = "播放视频", tint = Color.White, modifier = Modifier.size(32.dp))
                                }
                            } else {
                                // 英雄视图：仅当前页与列表缩略图共享元素，避免多页同时注册相同 key
                                val detailSharedModifier =
                                    if (sharedTransitionScope != null && animatedVisibilityScope != null && page == initial) {
                                        with(sharedTransitionScope) {
                                            Modifier.sharedBounds(
                                                sharedContentState = rememberSharedContentState(key = "photo_${photos[page].uri}"),
                                                animatedVisibilityScope = animatedVisibilityScope,
                                                // sharedBounds + 交叉淡入淡出：容器 bounds 动画，两端图片按各自 ContentScale
                                                // 绘制后交叉过渡，消除 sharedElement 在 Crop↔Fit 跳变导致的返回顿挫
                                                enter = fadeIn(tween(220)),
                                                exit = fadeOut(tween(180)),
                                                boundsTransform = { _, _ ->
                                                    tween<androidx.compose.ui.geometry.Rect>(280, easing = FastOutSlowInEasing)
                                                },
                                            )
                                        }
                                    } else Modifier
                                // 渐进式解码：底层按 1080p 快速解码全图，放大时叠加上层局部原图块（看哪解哪）
                                var layoutSize by remember { mutableStateOf(Size.Zero) }
                                // 共享元素挂在外层稳定容器上：避免 zoomable 的 graphicsLayer 缩放/位移
                                // 干扰 sharedElement bounds，保证返回时英雄视图能正确缩回列表缩略图
                                Box(modifier = Modifier.fillMaxSize()
                                    .onSizeChanged { layoutSize = Size(it.width.toFloat(), it.height.toFloat()) }
                                    .then(detailSharedModifier)) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(liveThumb ?: photos[page].uri)
                                            // 默认按 1080p 快速解码全图：首屏快、省内存；放大细节交给 HiResTile 局部解码
                                            .size(1080, 1080)
                                            // 缩略图 Uri 恒定，按版本号做 cache key 避免 Coil 命中旧图
                                            .memoryCacheKey("edit_${photos[page].displayName}_v${nav.photosVersion}")
                                            .build(),
                                        contentDescription = photos[page].displayName,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().zoomable(
                                            zoomState = zoomState,
                                            // 单击切换浮层显隐；双击仍由默认逻辑缩放
                                            onTap = {
                                                // 隐藏浮层时先关掉子弹窗，避免下次显示又冒出来
                                                if (chromeVisible) {
                                                    showDeleteConfirm = false
                                                    showInfo = false
                                                    showExportPanel = false
                                                }
                                                chromeVisible = !chromeVisible
                                            }
                                        )
                                    )
                                    // 放大时叠加原图局部块；编辑图（liveThumb）尺寸与原图不同，仅原图路径启用
                                    HiResTile(
                                        uri = photos[page].uri,
                                        origWidth = photos[page].width,
                                        origHeight = photos[page].height,
                                        zoomState = zoomState,
                                        layoutSize = layoutSize,
                                        enabled = liveThumb == null && photos[page].width > 0 && photos[page].height > 0,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        // 玻璃控件只在 overlay 兄弟浮层，折射全屏图片，无递归环
        overlay = { backdrop ->
            if (photos.isNotEmpty()) {
                // 顶部：返回 + 文件名/大小/格式/日期 + 右侧页码（与相机详情对齐，带同样淡入淡出）
                AnimatedVisibility(
                    visible = chromeVisible && !showExportPanel,
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassIconButton(AppIcons.ArrowLeft, "返回", backdrop, onClick = { nav.pop() })
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                currentPhoto?.displayName ?: "",
                                color = colors.textPrimary, fontSize = 14.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1,
                            )
                            Text(
                                listOfNotNull(
                                    currentInfo?.sizeText,
                                    currentInfo?.format,
                                    currentInfo?.dateText,
                                ).joinToString(" · "),
                                color = colors.textSecondary, fontSize = 11.sp, maxLines = 1,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${pagerState.currentPage + 1} / ${photos.size}",
                            color = colors.textPrimary, fontSize = 14.sp,
                        )
                    }
                }
                // 底部操作行（整体上移，不贴屏幕底）：左胶囊 3 圆钮 + 导出 + 右大显影按钮
                AnimatedVisibility(
                    visible = chromeVisible && !showExportPanel,
                    enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { it / 2 },
                    exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(animationSpec = tween(300)) { it / 2 }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        // 删除二次确认胶囊：对准左下的删除按钮，而非屏幕居中
                        AnimatedVisibility(
                            visible = showDeleteConfirm,
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 104.dp)
                        ) {
                            LiquidPanel(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Text("删除这张照片？", color = colors.textPrimary, fontSize = 13.sp)
                                    Spacer(Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        GlassIconButton(AppIcons.Delete, "确认删除", backdrop, onClick = doDelete,
                                            tint = colors.brandDanger, inPanel = true, danger = true)
                                        Spacer(Modifier.width(16.dp))
                                        GlassIconButton(AppIcons.Close, "取消", backdrop, onClick = { showDeleteConfirm = false },
                                            inPanel = true)
                                    }
                                }
                            }
                        }
                        // 查看参数面板：锚定到左胶囊第 2 钮（参数）上方，而非屏幕居中
                        AnimatedVisibility(
                            visible = showInfo && currentPhoto != null,
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 64.dp, bottom = 104.dp)
                        ) {
                            LiquidPanel(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                val info = currentPhoto?.let { PhotoRepository.photoInfo(context, it) }
                                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                                    Text("照片参数", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    if (info != null) {
                                        Text("文件名：${info.name}", color = colors.textSecondary, fontSize = 12.sp)
                                        Text("格式：${info.format}    尺寸：${info.dimensions}", color = colors.textSecondary, fontSize = 12.sp)
                                        Text("大小：${info.sizeText}    日期：${info.dateText}", color = colors.textSecondary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(bottom = 20.dp)
                        ) {
                            // 主操作行
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 左侧胶囊：删除 / 参数 / 分享 / 导出（纯圆形图标，胶囊形状，高度对齐显影按钮）
                                LiquidPanel(backdrop = backdrop, shape = RoundedCornerShape(50)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.height(58.dp).padding(horizontal = 8.dp, vertical = 7.dp)
                                    ) {
                                        GlassIconButton(AppIcons.Delete, "删除", backdrop,
                                            onClick = { showDeleteConfirm = true; showInfo = false },
                                            tint = colors.brandDanger, danger = true)
                                        Spacer(Modifier.width(4.dp))
                                        GlassIconButton(AppIcons.Info, "参数", backdrop,
                                            onClick = { showInfo = !showInfo; showDeleteConfirm = false })
                                        Spacer(Modifier.width(4.dp))
                                        GlassIconButton(AppIcons.Send, "分享", backdrop, onClick = doShare)
                                        Spacer(Modifier.width(4.dp))
                                        GlassIconButton(AppIcons.Download, "导出", backdrop,
                                            onClick = {
                                                if (currentPhoto?.isVideo == true) {
                                                    // 视频无法走显影引擎：直接原图复制到相册
                                                    doExportOriginal()
                                                } else if (!busy) {
                                                    showExportPanel = true
                                                    showInfo = false
                                                    showDeleteConfirm = false
                                                }
                                            })
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                // 显影按钮（最大，品牌色）
                                LiquidButton(
                                    onClick = {
                                        if (currentPhoto?.isVideo == true) toast = "视频不支持显影" else nav.push(Route.DEVELOP)
                                    },
                                    backdrop = backdrop,
                                    tint = colors.brandPrimary,
                                    surfaceColor = colors.bgSurface.copy(alpha = 0.25f),
                                    height = 58.dp,
                                    horizontalPadding = 20.dp,
                                    modifier = Modifier.height(58.dp)
                                ) {
                                    Icon(AppIcons.AutoAwesome, contentDescription = null, tint = colors.textPrimary,
                                        modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("显影", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                        maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
                // 导出配置面板（底部弹出、距底 10dp；弹出时主浮层已隐藏）
                AnimatedVisibility(
                    visible = showExportPanel,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = 10.dp), contentAlignment = Alignment.BottomCenter) {
                        LiquidPanel(backdrop = backdrop, shape = RoundedCornerShape(24.dp)) {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).widthIn(min = 320.dp)
                                    .animateContentSize(tween(240)),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text("导出设置", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.weight(1f))
                                    Text("✕", color = colors.textSecondary, fontSize = 16.sp,
                                        modifier = Modifier.clip(CircleShape).clickable(onClick = { showExportPanel = false }).padding(4.dp))
                                }
                                Spacer(Modifier.height(12.dp))
                                Text("格式", color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("jpg", "tiff", "dng").forEach { fmt ->
                                        val selected = exportFormat == fmt
                                        LiquidButton(
                                            onClick = { exportFormat = fmt },
                                            backdrop = backdrop,
                                            height = 40.dp,
                                            inPanel = true,
                                            tint = if (selected) colors.brandPrimary else Color.Unspecified,
                                            surfaceColor = if (selected) Color.Unspecified else colors.bgSurface.copy(alpha = 0.5f)
                                        ) {
                                            Text(fmt.uppercase(), color = colors.textPrimary, fontSize = 13.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                        }
                                    }
                                }
                                if (exportFormat == "jpg") {
                                    Spacer(Modifier.height(12.dp))
                                    Text("JPG 质量（默认 95）", color = colors.textSecondary, fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth())
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(80, 90, 95, 100).forEach { q ->
                                            val selected = exportQuality == q
                                            LiquidButton(
                                                onClick = { exportQuality = q },
                                                backdrop = backdrop,
                                                height = 40.dp,
                                                inPanel = true,
                                                tint = if (selected) colors.brandPrimary else Color.Unspecified,
                                                surfaceColor = if (selected) Color.Unspecified else colors.bgSurface.copy(alpha = 0.5f)
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
                                        onClick = { showExportPanel = false; doExportOriginal() },
                                        backdrop = backdrop, height = 44.dp, inPanel = true, horizontalPadding = 8.dp,
                                    ) {
                                        Text("原图导出", color = colors.textPrimary, fontSize = 13.sp)
                                    }
                                    LiquidButton(
                                        onClick = { showExportPanel = false; doExport(exportFormat, exportQuality) },
                                        backdrop = backdrop, tint = colors.brandPrimary, height = 44.dp,
                                        inPanel = true, horizontalPadding = 8.dp,
                                    ) {
                                        Text("确认导出", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                // 轻提示
                AnimatedVisibility(visible = toast != null, enter = fadeIn(), exit = fadeOut()) {
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = 220.dp), contentAlignment = Alignment.BottomCenter) {
                        LiquidPanel(backdrop = backdrop, shape = RoundedCornerShape(22.dp)) {
                            Text(toast ?: "", color = colors.textPrimary, fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                        }
                    }
                }
                // App 内视频播放器（Media3 / ExoPlayer，Apache-2.0，与 GPL 显影引擎隔离）
                if (showPlayer && currentPhoto?.isVideo == true && videoPlayer != null) {
                    val player = videoPlayer!!
                    val videoFile = File(PhotoRepository.appPictureDir(context), currentPhoto.displayName)
                    LaunchedEffect(player, videoFile.absolutePath) {
                        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(videoFile)))
                        player.prepare()
                        player.playWhenReady = true
                    }
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    this.player = player
                                    useController = true
                                    controllerShowTimeoutMs = 3000
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlassIconButton(AppIcons.Close, "关闭播放", backdrop, onClick = {
                                player.pause()
                                showPlayer = false
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(currentPhoto.displayName, color = Color.White, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "系统播放", color = colors.brandLight1, fontSize = 12.sp,
                                modifier = Modifier.clickable { openVideoSystem() }.padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    )
}

// ───────────────────────────── 显影页 ─────────────────────────────

@Composable
fun DevelopScreen(nav: AppNav) {
    val colors = LocalLazyFilmColors.current
    val bridge = LocalFilmBridge.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val photoUri = nav.selectedPhoto?.uri
    val photoName = nav.selectedPhoto?.displayName
    // 重进显影页时恢复该照片上次保存的参数（按 displayName，跨照片/跨重启不串）
    val savedEdit = remember(photoName) { photoName?.let { DevelopEdits.load(context, it) } }

    var films by remember { mutableStateOf<List<StockOption>>(emptyList()) }
    var papers by remember { mutableStateOf<List<StockOption>>(emptyList()) }
    // 胶片槽位：「原图」用 FILM_NONE 表示，与"是否渲染原图"解耦——
    // 调参数时胶片保持在原图不动，但照常渲染让参数生效。
    var filmId by remember { mutableStateOf(savedEdit?.filmId ?: FILM_NONE) }
    // 未显影的照片默认「无相纸」；有编辑状态的恢复其保存的相纸（含用户选过的真实相纸）
    var paperId by remember { mutableStateOf(savedEdit?.paperId ?: PAPER_NONE) }

    // 5 tab：胶片/冲印/光学/构图(含原尺度)/边框
    var tab by remember { mutableIntStateOf(0) }
    var subTab by remember { mutableIntStateOf(0) } // 光学 tab 内六个子页
    var proMode by remember { mutableStateOf(false) } // 专业模式：开启才显示全部专业参数
    var showPaperPicker by remember { mutableStateOf(false) } // 胶片 tab 内相纸弹层
    var showMeterPicker by remember { mutableStateOf(false) } // 测光弹层
    var showFilmBrand by remember { mutableStateOf("all") } // 品牌分类筛选
    var chromeVisible by remember { mutableStateOf(true) } // 点图片显隐所有面板
    val zoomState = rememberZoomState()

    // 面板开合动画：底部留白 + 图片缩放平滑过渡（对齐 lazyfilm 图片位移动画）
    val bottomInset by animateDpAsState(
        targetValue = if (chromeVisible) 300.dp else 0.dp,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "developBottomInset"
    )
    val imageScale by animateFloatAsState(
        targetValue = if (chromeVisible) 0.86f else 1f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "developImageScale"
    )

    // wire 参数状态（数据驱动，键=wire 名）：先铺默认值保证键齐全，再用已保存的编辑状态覆盖
    val wireState = remember {
        mutableStateMapOf<String, Any>().apply {
            putAll(defaultWire())
            if (savedEdit != null) putAll(savedEdit.wire)
        }
    }
    var rendered by remember { mutableStateOf<ByteArray?>(null) }
    var rendering by remember { mutableStateOf(false) }
    // 分级预览：拖动时用小边长快速反馈，松手后用完整边长精修
    var previewLongEdge by remember { mutableStateOf<Int?>(null) }
    // 恢复原图标记：true 时预览直接显示原图、跳过渲染与缓存回写；
    // 任何参数调节（markEdited）会置回 false，重新进入显影渲染流程。
    // 未显影的照片（无编辑状态）默认处于「原图」状态：胶片卡高亮原图、不渲染、相纸=无相纸。
    var showOriginalImage by remember { mutableStateOf(savedEdit == null) }
    // 是否真的动过参数：只有实际编辑才落盘参数/缩略图，避免「只进不改」也把默认胶片种进该照片
    var hasEdited by remember { mutableStateOf(false) }
    val markEdited: () -> Unit = { showOriginalImage = false; hasEdited = true }
    // 返回拦截中：正在强制最终渲染+落盘。此时普通渲染的回写应让位，避免低清图覆盖最终缩略图
    var backFlushing by remember { mutableStateOf(false) }

    // 调色预设：跨照片复用参数。存 SharedPreferences，含 wire 全量 + 胶片/相纸
    var showPresetPanel by remember { mutableStateOf(false) }
    var presetDeleteName by remember { mutableStateOf<String?>(null) } // 删除预设二次确认
    fun closePresetPanel() {
        showPresetPanel = false
        presetDeleteName = null
        chromeVisible = true
    }
    var presetNameDraft by remember { mutableStateOf("") }
    var presets by remember { mutableStateOf(FilmPresets.load(context)) }
    var importDraft by remember { mutableStateOf("") } // 预设分享码粘贴输入
    var presetToast by remember { mutableStateOf<String?>(null) } // 预设分享/导入结果提示
    LaunchedEffect(presetToast) {
        if (presetToast != null) { kotlinx.coroutines.delay(3000); presetToast = null }
    }
    var showRestoreConfirm by remember { mutableStateOf(false) } // 恢复原图二次确认

    // 构图-裁切（提案式）：拖动只改 cropPending 不渲染，按"应用"才写 wire 触发渲染
    var cropPending by remember { mutableStateOf(com.filmlens.nicolink.film.CropRect()) }
    var cropRatioIdx by remember { mutableIntStateOf(0) }
    var showGuideLines by remember { mutableStateOf(true) }
    // 预览图 intrinsic 尺寸（onSuccess 拿到），用于把裁切框对齐到 Fit 显示矩形
    var imgIntrinsic by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var boxSize by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // 选裁切比例：必须从【原图】整幅重算，不能用已裁切的预览图 imgIntrinsic，否则越裁越小
    // （权威 cropToRatio 每次从 originalAspect 重算，幂等）
    val onCropRatio: (Int) -> Unit = { idx ->
        cropRatioIdx = idx
        val ratio = com.filmlens.nicolink.film.CROP_RATIOS[idx].ratio
        val have = photoName?.let { PhotoRepository.originalAspect(context, it) }
            ?: imgIntrinsic?.let { it.first.toFloat() / it.second } ?: 1.5f
        if (ratio <= 0f || have <= 0f) {
            cropPending = com.filmlens.nicolink.film.CropRect()
        } else if (ratio > have) {
            // 目标更宽 → 居中裁高度
            val ch = have / ratio
            cropPending = com.filmlens.nicolink.film.CropRect(0f, (1f - ch) / 2f, 1f, ch)
        } else {
            // 目标更高 → 居中裁宽度
            val cw = ratio / have
            cropPending = com.filmlens.nicolink.film.CropRect((1f - cw) / 2f, 0f, cw, 1f)
        }
    }

    /**
     * 构图/尺幅统一 setter（权威 applyGate + commitPendingCrop 的合一）：
     * 片门(gate)、物理尺幅(mm)、片边开关(overscan_active) 是同一份状态，必须一起写。
     * 只写 gate 不重裁 → 画面比例≠gate 比例 → 引擎拒渲染（"构图/边框冲突"根因）。
     * @param recrop true=从原图整幅重裁到 wantRatio（尺幅卡片）；false=沿用提案框（裁切应用）
     */
    // 从原图整幅重裁到目标比例（幂等，权威 cropToRatio 每次从 originalAspect 重算，绝不叠加）
    val recropToRatio: (Float) -> Unit = { wantRatio ->
        val have = photoName?.let { PhotoRepository.originalAspect(context, it) } ?: 1.5f
        if (have > 0f && wantRatio > 0f) {
            cropPending = if (wantRatio > have) {
                val ch = have / wantRatio
                com.filmlens.nicolink.film.CropRect(0f, (1f - ch) / 2f, 1f, ch)
            } else {
                val cw = wantRatio / have
                com.filmlens.nicolink.film.CropRect((1f - cw) / 2f, 0f, cw, 1f)
            }
            wireState["geometry_crop_x"] = cropPending.x
            wireState["geometry_crop_y"] = cropPending.y
            wireState["geometry_crop_w"] = cropPending.w
            wireState["geometry_crop_h"] = cropPending.h
        }
    }
    // gate 的横幅比例（CROP_RATIOS 横幅条目的 ratio 即 gateRatio）；竖幅原图取倒数
    val gateRatioOf: (String) -> Float = { gate ->
        com.filmlens.nicolink.film.CROP_RATIOS
            .firstOrNull { it.gate == gate && it.ratio > 0f }?.ratio ?: 1.5f
    }
    // 保证片边开启时画面比例与 gate 一致，否则引擎拒渲染（spk_render failed -5 → 回退原图）。
    // 手动开片边 / 旋转 / 套预设后调用：权威 refreshEdgeFormats 的自愈等价。
    // 用权威同样的 5% 容差 + max(ca,1/ca) 归一化（L25516-25532），已匹配就不动，避免重置用户手动裁切。
    val ensureCropMatchesGate: () -> Unit = {
        if (wireState["overscan_active"] as? Boolean == true) {
            val gate = wireState["overscan_format"] as? String ?: "135"
            val have = photoName?.let { PhotoRepository.originalAspect(context, it) } ?: 1.5f
            val gateR = gateRatioOf(gate)
            val gateNorm = kotlin.math.max(gateR, 1f / gateR)
            // 当前裁切框的实际画面比例（cropPending 是相对原图的归一化矩形）
            val frameAspect = if (cropPending.h > 0f) (cropPending.w / cropPending.h) * have else have
            val frameNorm = kotlin.math.max(frameAspect, 1f / frameAspect)
            val error = kotlin.math.abs(frameNorm / gateNorm - 1f)
            if (error > 0.05f) {
                val want = if (have < 1f) 1f / gateR else gateR
                recropToRatio(want)
            }
        }
    }
    val applyGate: (gate: String, mm: Float, wantRatio: Float, recrop: Boolean) -> Unit = { gate, mm, wantRatio, recrop ->
        if (gate.isEmpty()) {
            // 「原始」：裁切复位全幅、关片边、gate 回默认（与权威"原始"卡片一致）
            cropPending = com.filmlens.nicolink.film.CropRect()
            wireState["geometry_crop_x"] = 0f
            wireState["geometry_crop_y"] = 0f
            wireState["geometry_crop_w"] = 1f
            wireState["geometry_crop_h"] = 1f
            wireState["overscan_active"] = false
            wireState["overscan_format"] = "135"
            wireState["film_format_mm"] = 36f
        } else {
            wireState["overscan_format"] = gate
            wireState["film_format_mm"] = mm
            // 权威：选尺幅卡片(applyGate)即请求片边；裁切应用(commitPendingCrop)只同步 gate/mm，
            // 不改片边开关——用户只裁切不应突然冒出边框。gate 已同步，之后手动开边框也不会冲突。
            if (recrop) wireState["overscan_active"] = true
            if (recrop && wantRatio > 0f) recropToRatio(wantRatio)
        }
        markEdited()
    }
    // 应用裁切：写提案框 + 同步片门（卡片即片门，否则引擎拒渲染），触发渲染
    val onCropApply: () -> Unit = {
        val r = com.filmlens.nicolink.film.CROP_RATIOS[cropRatioIdx]
        val c = cropPending
        wireState["geometry_crop_x"] = c.x.coerceIn(0f, 1f - 0.01f)
        wireState["geometry_crop_y"] = c.y.coerceIn(0f, 1f - 0.01f)
        wireState["geometry_crop_w"] = c.w.coerceIn(0.01f, 1f)
        wireState["geometry_crop_h"] = c.h.coerceIn(0.01f, 1f)
        applyGate(r.gate, r.filmMm, r.ratio, false)
    }
    // 90° 旋转：左-1/右+1/归零0；引擎只接受 [0,3]，用 mod 4 归一化（左转 -1 → 3，等价顺时针270°）
    val onQuarter: (Int) -> Unit = { which ->
        val cur = (wireState["geometry_quarter_turns"] as? Number)?.toInt() ?: 0
        wireState["geometry_quarter_turns"] = when (which) {
            0 -> ((cur - 1) % 4 + 4) % 4  // 左转：-1 → 3
            1 -> (cur + 1) % 4              // 右转：3 → 0
            else -> 0
        }
        // 权威 commitCompose → refreshEdgeFormats（L21486）：90° 旋转改变画面形状，
        // 片边开时 gate 可能不再匹配 → 自愈重裁，否则引擎拒渲染（高危 bug）。
        ensureCropMatchesGate()
        markEdited()
    }
    // 尺幅卡片选中态从 wire 派生（与裁切卡片共享同一份 overscan_format，天然联动）
    val currentGate = wireState["overscan_format"] as? String ?: "135"
    val fmtCardIdx = com.filmlens.nicolink.film.FORMAT_CARDS
        .indexOfFirst { it.id == currentGate }.coerceAtLeast(0)
    // 尺幅-物理尺幅：点片门卡片 → 自动开片边 + 从原图重裁到 gate 形状（权威 applyGate）
    val onFormatPick: (Int) -> Unit = { idx ->
        val card = com.filmlens.nicolink.film.FORMAT_CARDS[idx]
        // gate 的横幅比例（CROP_RATIOS 横幅条目的 ratio 即 gateRatio）；竖幅原图取倒数
        val gateRatio = com.filmlens.nicolink.film.CROP_RATIOS
            .firstOrNull { it.gate == card.id && it.ratio > 0f }?.ratio ?: 1.5f
        val have = photoName?.let { PhotoRepository.originalAspect(context, it) } ?: 1.5f
        val want = if (have < 1f) 1f / gateRatio else gateRatio
        applyGate(card.id, card.mm, want, true)
    }

    // wire 快照 → FilmParams（每次重组取最新）
    val params = FilmParams(
        wire = wireState.toMap(),
        autoExposure = (wireState["auto_exposure_method"] as? String)?.isNotEmpty() ?: true,
        exposureEv = (wireState["exposure_compensation_ev"] as? Number)?.toFloat() ?: 0f,
        grain = (wireState["grain_active"] as? Boolean) ?: true,
        halation = (wireState["halation_active"] as? Boolean) ?: true,
        previewLongEdge = previewLongEdge,
    )

    LaunchedEffect(bridge) {
        if (bridge == null) return@LaunchedEffect
        val f = withContext(Dispatchers.IO) { bridge.films() }
        val p = withContext(Dispatchers.IO) { bridge.papers() }
        films = f
        papers = p
        // 「原图」「无相纸」是哨兵值，不在目录里，别被替换成第一张真实项
        if (filmId != FILM_NONE && f.isNotEmpty() && f.none { it.id == filmId }) filmId = f.first().id
        if (paperId != PAPER_NONE && p.isNotEmpty() && p.none { it.id == paperId }) paperId = p.first().id
    }

    // 参数/胶片/相纸/预览级别变化 → 后台重新渲染（单飞：新请求取消旧请求，避免拖参数时堆积）
    // 会话复用后不再重解 RAW，只 setParams + 重渲染，配合短 debounce 和拖动低分辨率实现「不卡」
    LaunchedEffect(photoUri, filmId, paperId, wireState.toMap(), previewLongEdge, showOriginalImage, hasEdited) {
        // 恢复原图后保持显示原图：不渲染、不回写缓存（门控而非一次性标志，避免多次重启竞态）
        if (showOriginalImage) return@LaunchedEffect
        val b = bridge
        val uri = photoUri
        if (b == null || uri == null) return@LaunchedEffect
        delay(120)
        rendering = true
        val bytes = withContext(Dispatchers.IO) {
            try {
                b.render(uri.toString(), filmId, paperId, params)
            } catch (t: Throwable) {
                null
            }
        }
        rendered = bytes
        rendering = false
        // 仅当用户真的调过参数（hasEdited）才落盘参数 + 生成编辑缩略图；
        // 单纯进入页面只渲染预览、不写入，避免把默认 Kodak Portra 400 种进每张打开过的照片。
        // 【关键】每次成功渲染都落盘（含拖动中的低清预览）——用户快速返回时最终渲染可能来不及跑完，
        // 至少缩略图是"调节后的图"而非旧图；只有最终渲染(previewLongEdge==null)才 bump 刷新三页。
        if (bytes != null && hasEdited && !backFlushing) {
            val name = nav.selectedPhoto?.displayName
            nav.saveDevelop(DevelopSnapshot(filmId, paperId, params))
            if (name != null) {
                withContext(Dispatchers.IO) {
                    DevelopEdits.save(context, name, EditState(filmId, paperId, params.wire, System.currentTimeMillis()))
                    b.saveEditThumb(uri.toString(), name, bytes)
                }
                if (previewLongEdge == null) {
                    // 重读相册（按 displayName 现查缩略图），刷新主页 + 详情页，三页显示同一张调节后的图
                    val refreshed = withContext(Dispatchers.IO) { PhotoRepository.loadPhotos(context) }
                    nav.bumpPhotos()
                    nav.refreshPreviewPhotos(refreshed)
                }
            }
        }
    }

    // ── 返回拦截：有未落盘编辑时，先强制跑一次最终渲染+落盘再返回，避免"返回后还是旧图" ──
    // 纯禁用返回有卡死陷阱（渲染失败/原生挂起时用户被困），故带 5s 超时兜底放行。
    val persistEdit: suspend () -> Boolean = {
        var ok = false
        val b = bridge
        val uri = photoUri
        val name = nav.selectedPhoto?.displayName
        if (b != null && uri != null && name != null && hasEdited && !showOriginalImage) {
            try {
                // 强制最终分辨率（previewLongEdge=null → 引擎 1200），不用拖动中的低清图
                val finalParams = params.copy(previewLongEdge = null)
                val bytes = withContext(Dispatchers.IO) {
                    b.render(uri.toString(), filmId, paperId, finalParams)
                }
                if (bytes != null) {
                    nav.saveDevelop(DevelopSnapshot(filmId, paperId, finalParams))
                    withContext(Dispatchers.IO) {
                        DevelopEdits.save(context, name, EditState(filmId, paperId, finalParams.wire, System.currentTimeMillis()))
                        b.saveEditThumb(uri.toString(), name, bytes)
                    }
                    val refreshed = withContext(Dispatchers.IO) { PhotoRepository.loadPhotos(context) }
                    nav.bumpPhotos()
                    nav.refreshPreviewPhotos(refreshed)
                    ok = true
                }
            } catch (_: Throwable) {
                // 渲染/落盘失败也放行，绝不把用户困在页面里
            }
        } else {
            ok = true // 没有未落盘编辑，无需渲染
        }
        ok
    }
    val requestBack: () -> Unit = {
        if (hasEdited && !showOriginalImage) {
            backFlushing = true
            scope.launch {
                try {
                    withTimeoutOrNull(5000L) { persistEdit() }
                } catch (_: Throwable) {
                } finally {
                    backFlushing = false
                    nav.pop()
                }
            }
        } else {
            nav.pop()
        }
    }
    // 全局系统返回键（App.kt BackHandler）与页面顶部按钮共用同一入口
    val currentRequestBack by rememberUpdatedState(requestBack)
    val currentDirty by rememberUpdatedState(hasEdited && !showOriginalImage)
    DisposableEffect(nav) {
        nav.developBackInterceptor = {
            if (currentDirty) {
                currentRequestBack()
                true // 已接管返回
            } else false
        }
        onDispose { nav.developBackInterceptor = null }
    }

    // 非破坏性：恢复原图 = 参数/胶片/相纸全回默认，预览保持显示原图，清渲染结果、删编辑缩略图、释放会话
    val restoreOriginal: () -> Unit = {
        wireState.clear()
        wireState.putAll(defaultWire())
        filmId = FILM_NONE     // 恢复原图 = 胶片回到「原图」，不被自动选成 Kodak Portra 400
        paperId = PAPER_NONE   // 恢复原图 = 无相纸
        previewLongEdge = null
        rendered = null
        cropRatioIdx = 0      // 构图裁切比例重置（尺幅卡片选中态由 wire 派生，随 wire 复位）
        val b = bridge
        val uri = photoUri
        val name = nav.selectedPhoto?.displayName
        if (b != null && uri != null) {
            b.release(uri.toString())
            if (name != null) {
                b.clearEditThumb(name)
                DevelopEdits.delete(context, name)
            }
        }
        nav.clearDevelop() // 清调节快照，详情页导出/分享回落到默认参数
        if (name != null) {
            scope.launch {
                val refreshed = withContext(Dispatchers.IO) { PhotoRepository.loadPhotos(context) }
                nav.bumpPhotos()
                nav.refreshPreviewPhotos(refreshed)
            }
        }
        showOriginalImage = true
        showRestoreConfirm = false
    }

    // 保存当前全部参数为预设（手动命名）
    val savePreset: () -> Unit = {
        val name = presetNameDraft.trim()
        if (name.isNotEmpty()) {
            val preset = Preset(name, filmId, paperId, wireState.toMap(), System.currentTimeMillis())
            presets = FilmPresets.add(context, preset)
            presetNameDraft = ""
        }
    }
    // 分享预设：编码成 NLK1 串 → 复制到剪贴板 + 系统分享 Intent（两条路同时）
    val clipboardManager = LocalClipboardManager.current
    val sharePreset: (Preset) -> Unit = { p ->
        try {
            val code = PresetShare.encode(p)
            clipboardManager.setText(AnnotatedString(code))
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, code)
                putExtra(Intent.EXTRA_SUBJECT, "NicoLink 预设：${p.name}")
            }
            context.startActivity(Intent.createChooser(send, "分享预设"))
            presetToast = "预设「${p.name}」已复制并可调分享"
        } catch (t: Throwable) {
            presetToast = "分享失败：${t.message}"
        }
    }
    // 导入预设：解码 → 校验胶片/相纸在当前 catalog（缺则中止）→ 落盘 → 刷新列表
    val importPreset: () -> Unit = {
        val code = importDraft.trim()
        if (code.isEmpty()) {
            presetToast = "请先粘贴预设码"
        } else {
            val p = PresetShare.decode(code)
            when {
                p == null -> presetToast = "预设码无效或版本不支持"
                films.none { it.id == p.filmId } -> presetToast = "无法导入：缺少胶片 ${p.filmId}"
                p.paperId != PAPER_NONE && bridge?.papers()?.none { it.id == p.paperId } == true ->
                    presetToast = "无法导入：缺少相纸 ${p.paperId}"
                else -> {
                    presets = FilmPresets.add(context, p.copy(createdAt = System.currentTimeMillis()))
                    importDraft = ""
                    presetToast = "已导入预设「${p.name}」"
                }
            }
        }
    }
    // 应用预设：全量覆盖参数 + 胶片/相纸，重置辅助状态并触发渲染
    val applyPreset: (Preset) -> Unit = { p ->
        wireState.clear()
        wireState.putAll(p.wire)
        filmId = p.filmId
        paperId = p.paperId
        previewLongEdge = null
        cropRatioIdx = 0
        cropPending = com.filmlens.nicolink.film.CropRect()
        // 权威 applyForeignLook（L25349-25400）：预设来自不同比例的照片时，
        // 片边开 + gate 不匹配会拒渲染。自愈重裁到预设 gate 的比例。
        ensureCropMatchesGate()
        showPresetPanel = false
        markEdited() // showOriginalImage=false，触发重新渲染
    }
    // 删除预设
    val deletePreset: (String) -> Unit = { name -> presets = FilmPresets.delete(context, name) }

    val tabLabels = PARAM_TABS.map { it.label } // ["胶片","冲印","光学","构图","边框"]（尺度已并入构图）

    GlassPage(
        topBar = { backdrop ->
            if (chromeVisible) {
                // 自定义顶栏：标题 + 预设 + 恢复原图。不用 GlassTopBar 是为避开 LiquidButton 的
                // drawBackdrop LayoutModifier 干扰 Row 测量（标题被挤没、按钮叠左边）。
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassIconButton(AppIcons.ArrowLeft, "返回", backdrop, onClick = requestBack)
                    Text("显影", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    // 专业模式开关：关=简洁版（隐藏专业参数），开=显示全部参数
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (proMode) colors.brandPrimary else colors.bgSurface.copy(alpha = 0.6f))
                            .then(if (proMode) Modifier else Modifier.border(1.dp, colors.textTertiary.copy(0.4f), RoundedCornerShape(16.dp)))
                            .clickable { proMode = !proMode }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text("专业", color = if (proMode) colors.textInverse else colors.textSecondary,
                            fontSize = 12.sp, fontWeight = if (proMode) FontWeight.Bold else FontWeight.Normal)
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassIconButton(AppIcons.Bookmark, "预设", backdrop, onClick = {
                        // 不能设 chromeVisible=false：预设面板嵌在 AnimatedVisibility(chromeVisible) 内，
                        // 隐藏 chromeVisible 会让整个 overlay（含本面板+遮罩）退出 → 面板闪退、遮罩消失导致穿透。
                        // 面板自带全屏遮罩（见 showPresetPanel）已能拦截底层 tab 点击，无需收起 chrome。
                        showMeterPicker = false
                        showPaperPicker = false
                        showPresetPanel = true
                    })
                    Spacer(Modifier.width(8.dp))
                    // 恢复原图按钮：底下叠加载指示器（渲染时显示，按钮盖在其上）
                    Box(contentAlignment = Alignment.Center) {
                        if (rendering || backFlushing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                color = colors.brandPrimary,
                                strokeWidth = 3.dp,
                            )
                        }
                        GlassIconButton(AppIcons.Undo, "恢复原图", backdrop, onClick = { showRestoreConfirm = true })
                    }
                    if (backFlushing) {
                        Text("正在保存…", color = colors.textSecondary, fontSize = 11.sp)
                    }
                }
            }
        },
        // content 只放普通图片预览（被折射层），玻璃控件一律不进这里
        content = {
            Box(modifier = Modifier.fillMaxSize().statusBarsPadding()
                .padding(bottom = bottomInset)
                .onSizeChanged { boxSize = it.width to it.height }) {
                // 恢复原图后固定显示原始照片；否则优先本次渲染结果，其次实时查编辑缩略图文件
                // （不依赖 nav 快照，自动显影/后台渲染保存后只要 bumpPhotos 即可三页一致）。
                val liveThumb = remember(photoName, nav.photosVersion) {
                    photoName?.let { PhotoRepository.editThumbFile(context, it).let { f -> if (f.exists()) Uri.fromFile(f) else null } }
                }
                val model: Any? = if (showOriginalImage) photoUri
                    else (rendered ?: liveThumb ?: photoUri)
                // 缩略图 Uri 恒定，Coil 默认按 Uri 命中内存缓存 → 改完参数返回仍是旧图。
                // 用版本号做 cache key，bumpPhotos 后强制重新解码（ByteArray 渲染结果无需包装）。
                val imageModel: Any? = when (model) {
                    is Uri -> ImageRequest.Builder(context).data(model)
                        .memoryCacheKey("edit_${photoName}_v${nav.photosVersion}").build()
                    else -> model
                }
                if (imageModel != null) {
                    // 图片随面板开合平滑缩放 + 位移（面板展开时缩小上移让位，收起时放大回中）
                    AsyncImage(
                        model = imageModel,
                        contentDescription = "显影预览",
                        contentScale = ContentScale.Fit,
                        onSuccess = { res ->
                            val s = res.painter.intrinsicSize
                            if (s.width > 0f && s.height > 0f) {
                                imgIntrinsic = s.width.toInt() to s.height.toInt()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer {
                                scaleX = imageScale
                                scaleY = imageScale
                            }
                            .zoomable(
                                zoomState,
                                onTap = { chromeVisible = !chromeVisible }
                            )
                    )
                    // 构图-裁切页：在预览图上叠加可拖拽裁切框 + 参考线（提案式，应用前不渲染）
                    if (tab == 3 && subTab == 0 && chromeVisible && !showOriginalImage) {
                        CropOverlay(
                            crop = cropPending,
                            onCropChange = { cropPending = it },
                            imgSize = imgIntrinsic,
                            containerSize = boxSize,
                            imageScale = imageScale,
                            showGuide = showGuideLines,
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("没有选中的照片", color = colors.textTertiary, fontSize = 14.sp)
                    }
                }
            }
        },
        // 玻璃控件全在 overlay 兄弟浮层，折射 content 图片，无递归环
        overlay = { backdrop ->
            AnimatedVisibility(
                visible = chromeVisible,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 面板：LiquidMorphPanel 高度随内容弹簧动画（对齐 lazyfilm）
                        LiquidMorphPanel(
                            backdrop = backdrop,
                            contentKey = if (tab == 2) "optics_$subTab" else "tab_$tab",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            // 限高 320dp，超出内部滚动（面板内容多时不撑满屏幕）
                            Box(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                                when (tab) {
                                    0 -> FilmTabPanel(
                                        films, filmId, { id ->
                                            filmId = id
                                            // 对齐权威 pickFilmCard:4007-4008：按胶片声明的 targetPrint 切相纸，
                                            // 且必须在相纸目录内才生效（正片无 targetPrint → 相纸保持不变）。
                                            val targetPrint = films.firstOrNull { it.id == id }?.targetPrint.orEmpty()
                                            if (targetPrint.isNotEmpty() && papers.any { it.id == targetPrint }) {
                                                paperId = targetPrint
                                            }
                                            markEdited()
                                        },
                                        papers, paperId, { paperId = it; markEdited() },
                                        wireState,
                                        showMeterPicker, { showMeterPicker = it },
                                        showPaperPicker, { showPaperPicker = it },
                                        showFilmBrand, { showFilmBrand = it },
                                        backdrop,
                                        onEdit = markEdited,
                                        onRestore = restoreOriginal,
                                        isShowingOriginal = filmId == FILM_NONE,
                                    )
                                    else -> ParamTabPanel(
                                        tabSpec = PARAM_TABS[tab],
                                        subTab = if (tab == 2 || tab == 3 || tab == 4) subTab else 0,
                                        onSubTab = { subTab = it },
                                        wireState = wireState,
                                        backdrop = backdrop,
                                        onDragStart = { previewLongEdge = 800; markEdited() },
                                        onDragEnd = { previewLongEdge = null },
                                        onEdit = markEdited,
                                        cropPending = cropPending,
                                        cropRatioIdx = cropRatioIdx,
                                        onCropRatio = onCropRatio,
                                        onCropApply = onCropApply,
                                        onQuarter = onQuarter,
                                        showGuideLines = showGuideLines,
                                        onGuideChange = { showGuideLines = it },
                                        fmtCardIdx = fmtCardIdx,
                                        overscanOn = (wireState["overscan_active"] as? Boolean) ?: false,
                                        onFormatPick = onFormatPick,
                                        // 手动开片边：权威 toggle 时校验画面比例，无 gate 匹配则拒绝（高危 bug）
                                        onOverscanToggle = { on ->
                                            wireState["overscan_active"] = on
                                            if (on) ensureCropMatchesGate()
                                            markEdited()
                                        },
                                        proMode = proMode,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        val pageBg = rememberCanvasBackdrop { drawRect(colors.bgPrimary) }
                        val currentTab by rememberUpdatedState(tab)
                        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), contentAlignment = Alignment.Center) {
                            // 「原图」（未选胶片）时，冲印(1)/光学(2) 与胶片强相关的 tab 禁用
                            val isOriginal = filmId == FILM_NONE
                            val disabledTabs = if (isOriginal) setOf(1, 2) else emptySet()
                            LiquidBottomTabs(
                                selectedTabIndex = { tab },
                                onTabSelected = { i -> if (i != currentTab) { tab = i; subTab = 0 } },
                                backdrop = rememberCombinedBackdrop(pageBg, backdrop),
                                tabsCount = tabLabels.size,
                                accentColor = colors.brandPrimary,
                                disabledTabIndices = disabledTabs,
                                modifier = Modifier.fillMaxWidth(0.96f)
                            ) {
                                tabLabels.forEachIndexed { index, label ->
                                    LiquidBottomTab(
                                        enabled = index !in disabledTabs,
                                        onClick = { if (index != currentTab && index !in disabledTabs) { tab = index; subTab = 0 } },
                                        modifier = Modifier.fillMaxHeight()
                                    ) {
                                        Text(label, color = colors.textPrimary, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 恢复原图二次确认：锚定顶栏撤销按钮下方（参考详情页删除确认弹窗）
                    AnimatedVisibility(
                        visible = showRestoreConfirm,
                        modifier = Modifier.align(Alignment.TopEnd)
                            .statusBarsPadding().padding(top = 56.dp, end = 12.dp)
                    ) {
                        LiquidPanel(backdrop = backdrop, shape = RoundedCornerShape(24.dp)) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text("恢复原图？", color = colors.textPrimary, fontSize = 13.sp)
                                Spacer(Modifier.height(3.dp))
                                Text("将放弃全部调节与胶片/相纸参数", color = colors.textTertiary, fontSize = 11.sp)
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    GlassIconButton(AppIcons.Undo, "确认恢复", backdrop,
                                        onClick = { showRestoreConfirm = false; restoreOriginal() },
                                        tint = colors.brandDanger, inPanel = true)
                                    Spacer(Modifier.width(16.dp))
                                    GlassIconButton(AppIcons.Close, "取消", backdrop,
                                        onClick = { showRestoreConfirm = false }, inPanel = true)
                                }
                            }
                        }
                    }

                    // 调色预设弹窗：与「导出设置」同款玻璃面板（无全屏遮罩 + 底部滑入 + LiquidButton）
                    AnimatedVisibility(
                        visible = showPresetPanel,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().imePadding().padding(bottom = 10.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            // 遮罩挡住底层 tab/面板，防止穿透点击。
                            // 用 clickable（Main pass 消费）：点 LiquidPanel 内组件（输入框/按钮）时子组件先消费，
                            // 遮罩不触发；点遮罩空白处才关闭。之前用 awaitFirstDown(requireUnconsumed=false)
                            // 会在 Initial pass 连输入框已消费的 down 也拦截，导致点输入框就关闭面板。
                            Box(
                                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                                    // 只吞掉点击、不关闭面板（关闭只靠右上角 ✕）。点空白关闭体验差，
                                    // 且 detectTapGestures 在 Main pass 消费，不影响 LiquidPanel 内组件。
                                    .pointerInput(Unit) { detectTapGestures { /* 吞掉，不关闭 */ } }
                            )
                            LiquidPanel(backdrop = backdrop, shape = RoundedCornerShape(24.dp)) {
                                Column(
                                    // 垫一层高不透明面板色：显影页背后是密集胶片面板，玻璃采样不到它，
                                    // 不垫底会导致底层卡片文字透过面板重叠。颜色与「导出设置」弹窗一致（bgElevated 深绿）。
                                    modifier = Modifier.clip(RoundedCornerShape(24.dp))
                                        .background(colors.bgElevated.copy(alpha = 0.94f))
                                        .padding(horizontal = 20.dp, vertical = 16.dp)
                                        .widthIn(min = 320.dp).animateContentSize(tween(240))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text("调色预设", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.weight(1f))
                                        Text("✕", color = colors.textSecondary, fontSize = 16.sp,
                                            modifier = Modifier.clip(CircleShape).clickable(onClick = { closePresetPanel() }).padding(4.dp))
                                    }
                                    if (presetToast != null) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(presetToast ?: "", color = colors.brandLight1, fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    // 新建：输入框 + 保存按钮（LiquidButton 胶囊，对齐导出设置）
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp))
                                            .background(colors.bgSurface.copy(alpha = 0.5f)).padding(horizontal = 14.dp),
                                            contentAlignment = Alignment.CenterStart) {
                                            if (presetNameDraft.isEmpty()) {
                                                Text("预设名称", color = colors.textTertiary, fontSize = 14.sp)
                                            }
                                            BasicTextField(
                                                value = presetNameDraft,
                                                onValueChange = { presetNameDraft = it },
                                                singleLine = true,
                                                textStyle = androidx.compose.ui.text.TextStyle(color = colors.textPrimary, fontSize = 14.sp)
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        LiquidButton(
                                            onClick = savePreset,
                                            backdrop = backdrop,
                                            tint = colors.brandPrimary,
                                            height = 46.dp,
                                            inPanel = true
                                        ) { Text("保存", color = colors.textPrimary, fontSize = 13.sp) }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    // 导入：粘贴别人分享的 NLK1 预设码
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                                            .background(colors.bgSurface.copy(alpha = 0.5f)).padding(horizontal = 14.dp),
                                            contentAlignment = Alignment.CenterStart) {
                                            if (importDraft.isEmpty()) {
                                                Text("粘贴预设码 NLK1...", color = colors.textTertiary, fontSize = 13.sp)
                                            }
                                            BasicTextField(
                                                value = importDraft,
                                                onValueChange = { importDraft = it },
                                                singleLine = true,
                                                textStyle = androidx.compose.ui.text.TextStyle(color = colors.textPrimary, fontSize = 13.sp)
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        LiquidButton(
                                            onClick = importPreset,
                                            backdrop = backdrop,
                                            height = 44.dp,
                                            inPanel = true
                                        ) { Text("导入", color = colors.textPrimary, fontSize = 13.sp) }
                                    }
                                    Spacer(Modifier.height(14.dp))
                                    Text("已保存的预设（${presets.size}）", color = colors.textSecondary, fontSize = 12.sp)
                                    Spacer(Modifier.height(6.dp))
                                    if (presets.isEmpty()) {
                                        Text("暂无预设，先在图片 A 调好参数再保存", color = colors.textTertiary, fontSize = 12.sp)
                                    } else {
                                        Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                                            presets.forEach { p ->
                                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(colors.bgSurface.copy(alpha = 0.5f))
                                                    .padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(p.name, color = colors.textPrimary, fontSize = 13.sp)
                                                        val fn = films.firstOrNull { it.id == p.filmId }?.name ?: p.filmId
                                                        val date = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(p.createdAt))
                                                        Text("$fn · $date", color = colors.textTertiary, fontSize = 11.sp)
                                                    }
                                                    Spacer(Modifier.width(8.dp))
                                                    LiquidButton(onClick = { presetDeleteName = p.name }, backdrop = backdrop, height = 38.dp, inPanel = true) {
                                                        Text("删除", color = colors.brandDanger, fontSize = 12.sp)
                                                    }
                                                    Spacer(Modifier.width(6.dp))
                                                    LiquidButton(onClick = { sharePreset(p) }, backdrop = backdrop, height = 38.dp, inPanel = true) {
                                                        Text("分享", color = colors.textPrimary, fontSize = 12.sp)
                                                    }
                                                    Spacer(Modifier.width(6.dp))
                                                    LiquidButton(onClick = { applyPreset(p) }, backdrop = backdrop, height = 38.dp, inPanel = true) {
                                                        Text("应用", color = colors.textPrimary, fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // 删除预设二次确认（居中玻璃胶囊，遮罩挡住底层）
                    presetDeleteName?.let { name ->
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                                .clickable { presetDeleteName = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            LiquidPanel(backdrop = backdrop, shape = RoundedCornerShape(22.dp),
                                modifier = Modifier.clickable { }) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                ) {
                                    Text("删除预设「$name」？", color = colors.textPrimary, fontSize = 14.sp)
                                    Text("删除后不可恢复", color = colors.textTertiary, fontSize = 11.sp)
                                    Spacer(Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        GlassIconButton(AppIcons.Close, "取消", backdrop,
                                            onClick = { presetDeleteName = null }, inPanel = true)
                                        GlassIconButton(AppIcons.Delete, "确认删除", backdrop,
                                            onClick = {
                                                deletePreset(name)
                                                presetDeleteName = null
                                            }, tint = colors.brandDanger, danger = true, inPanel = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

/**
 * 胶片名称跑马灯（移植 lazyfilm FilmTab.MarqueeFilmName）：
 * 选中态且名称实测宽度超过容器 → 左右滚动；未选中 → 单行省略号。
 * 解决长胶片名（如 fujifilm_crystal_archive_typeii）在 80dp 卡片里显示不全的问题。
 */
@Composable
private fun MarqueeFilmName(
    text: String,
    isSelected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 10.sp,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableIntStateOf(0) }
    val textWidthPx = remember(text, fontSize, fontWeight, textMeasurer) {
        textMeasurer.measure(
            text = text,
            style = TextStyle(fontSize = fontSize, fontWeight = fontWeight),
            maxLines = 1,
            softWrap = false,
        ).size.width.toFloat()
    }
    val needsMarquee = isSelected && textWidthPx > containerWidthPx && containerWidthPx > 0
    val offsetAnim = remember { Animatable(0f) }
    LaunchedEffect(needsMarquee, isSelected, textWidthPx, containerWidthPx) {
        if (needsMarquee) {
            val overflow = (textWidthPx - containerWidthPx + 12f).coerceAtLeast(0f)
            while (isActive) {
                delay(1200)
                offsetAnim.animateTo(
                    targetValue = -overflow,
                    animationSpec = tween(
                        durationMillis = (overflow * 1.2f).toInt().coerceIn(800, 4000),
                        easing = LinearEasing,
                    )
                )
                delay(1500)
                offsetAnim.snapTo(0f)
            }
        } else if (offsetAnim.value != 0f) {
            offsetAnim.snapTo(0f)
        }
    }
    Box(
        modifier = modifier.clipToBounds().onSizeChanged { containerWidthPx = it.width },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (needsMarquee) {
            Text(
                text = text,
                modifier = Modifier.offset(x = with(density) { offsetAnim.value.toDp() }),
                fontSize = fontSize,
                color = color,
                fontWeight = fontWeight,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
        } else {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                fontSize = fontSize,
                color = color,
                fontWeight = fontWeight,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = if (isSelected) TextOverflow.Visible else TextOverflow.Ellipsis,
            )
        }
    }
}

/** 胶片 tab：品牌分类 + 胶片卡片 + 测光 + 相纸入口（对齐 SpektraLab buildFilmPanel） */
@Composable
private fun FilmTabPanel(
    films: List<StockOption>, filmId: String, onFilm: (String) -> Unit,
    papers: List<StockOption>, paperId: String, onPaper: (String) -> Unit,
    wire: MutableMap<String, Any>,
    showMeter: Boolean, onShowMeter: (Boolean) -> Unit,
    showPaper: Boolean, onShowPaper: (Boolean) -> Unit,
    brand: String, onBrand: (String) -> Unit,
    backdrop: com.kyant.backdrop.Backdrop,
    onEdit: () -> Unit = {},
    onRestore: () -> Unit = {},
    isShowingOriginal: Boolean = false,
) {
    val colors = LocalLazyFilmColors.current
    val bridge = LocalFilmBridge.current
    val density = LocalDensity.current
    val brands = listOf("all" to "全部", "kodak" to "KODAK", "fujifilm" to "FUJIFILM", "ilford" to "ILFORD", "cinestill" to "CINESTILL")
    val filtered = if (brand == "all") films else films.filter { it.id.contains(brand) }
    val meter = wire["auto_exposure_method"] as? String ?: ""
    val meterLabel = METER_CHOICES[METER_VALUES.indexOf(meter).coerceIn(0, METER_CHOICES.size - 1)]

    // 真实效果缩略图：首次进入触发后台逐张渲染 + 落盘，每张完成递增版本触发卡片重读。
    var thumbVersion by remember { mutableIntStateOf(0) }
    val thumbScope = rememberCoroutineScope()
    LaunchedEffect(bridge, films) {
        if (bridge == null || films.isEmpty()) return@LaunchedEffect
        // ensureFilmThumbs 内部用 IO scope 启动并回调（非 suspend），这里在回调里 launch 切回主线程更新 UI。
        bridge.ensureFilmThumbs(films) { _, _ ->
            thumbScope.launch(Dispatchers.Main) { thumbVersion++ }
        }
    }

    // 选中项回中（移植 lazyfilm FilmTab）：进入显影页 / 选中胶片后把选中卡滚到视口中央。
    // 布局：索引 0 是「原图」卡，胶片 filtered 从索引 1 起；每槽 = 80dp 卡宽 + 8dp 间距。
    val filmListState = rememberLazyListState()
    var filmViewportWidth by remember { mutableIntStateOf(0) }
    LaunchedEffect(filmId, filtered, filmViewportWidth) {
        if (filmViewportWidth <= 0 || filtered.isEmpty()) return@LaunchedEffect
        // 选中卡在 LazyRow 中的索引：原图=FILM_NONE→0；否则 1 + filtered 内位置
        val selectedIndex = if (filmId == FILM_NONE) 0 else 1 + filtered.indexOfFirst { it.id == filmId }
        if (selectedIndex < 0) return@LaunchedEffect // 当前品牌过滤下无该胶片（如切品牌），不回中
        delay(120) // 等面板入场动画/布局稳定
        val slotWidth = with(density) { 88.dp.toPx() }.toInt() // 80dp 卡宽 + 8dp 间距
        val halfItem = with(density) { 40.dp.toPx() }.toInt()
        val viewportCenter = filmViewportWidth / 2
        val targetScroll = selectedIndex * slotWidth - (viewportCenter - halfItem)
        val effectiveScroll = maxOf(0, targetScroll)
        val virtualIndex = (effectiveScroll / slotWidth).coerceAtMost((filmListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        val virtualOffset = effectiveScroll % slotWidth
        filmListState.animateScrollToItem(virtualIndex, virtualOffset)
    }

    if (showPaper) {
        // 相纸选择弹层（Column 纵向排列——外层是 Box 层叠布局，多个平级子项会重叠）
        // 首项「无相纸」：未显影/恢复原图时的默认状态。
        val paperChoices = listOf(StockOption(PAPER_NONE, "无相纸")) + papers
        Column(Modifier.fillMaxWidth()) {
            PickerHeader("更换相纸", backdrop) { onShowPaper(false) }
            // 当前相纸特性描述（权威 paperNote）：随选中即时切换，无相纸也有说明
            val note = paperNoteOf(paperId)
            if (note.isNotEmpty()) {
                Text(
                    note,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            StockRow(paperChoices, paperId) { selectedPaper ->
                onPaper(selectedPaper)
                onShowPaper(false)
            }
        }
        return
    }
    if (showMeter) {
        // 测光方法弹层（对齐 SpektraLab showMeterPicker）；Column 避免与标题重叠
        Column(Modifier.fillMaxWidth()) {
            PickerHeader("测光", backdrop) { onShowMeter(false) }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(METER_CHOICES.size) { i ->
                    val sel = METER_VALUES[i] == meter
                    ChoiceChip(METER_CHOICES[i], sel) {
                        wire["auto_exposure_method"] = METER_VALUES[i]
                        wire["auto_exposure"] = METER_VALUES[i].isNotEmpty()
                        onEdit()
                        onShowMeter(false)
                    }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        // 标题行 + 测光按钮（显示当前方法名，对齐权威 meterChip）
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("胶片与相纸 / FILM & PAPER", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Box(
                Modifier.clip(RoundedCornerShape(14.dp)).background(colors.brandPrimary.copy(0.2f))
                    .clickable { onShowMeter(true) }.padding(horizontal = 10.dp, vertical = 4.dp)
            ) { Text(if (meter.isEmpty()) "测光" else "测光：$meterLabel", color = colors.textPrimary, fontSize = 12.sp) }
        }
        Spacer(Modifier.height(8.dp))
        // 品牌分类行（带数量，对齐权威 rebuildBrandRow "KODAK 17"）
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(brands) { (id, name) ->
                val count = if (id == "all") films.size else films.count { it.id.contains(id) }
                Text("$name $count", color = if (id == brand) colors.brandLight1 else colors.textTertiary,
                    fontSize = 12.sp, fontWeight = if (id == brand) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clickable { onBrand(id) })
            }
        }
        Spacer(Modifier.height(8.dp))
        // 胶片卡片横滚：首卡"原图" + 各胶片卡（带胶片盒缩略图占位）
        if (filtered.isEmpty()) {
            Text("加载中…", color = colors.textTertiary, fontSize = 12.sp)
        } else {
            LazyRow(
                state = filmListState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.onSizeChanged { filmViewportWidth = it.width },
            ) {
                // 原图卡片（对齐权威首卡 original；点击=恢复原图）
                item(key = "__original__") {
                    val sel = isShowingOriginal
                    Column(
                        modifier = Modifier.width(80.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) colors.brandPrimary.copy(alpha = 0.25f) else colors.bgSurface)
                            .then(if (sel) Modifier.border(1.5.dp, colors.brandLight1, RoundedCornerShape(10.dp)) else Modifier)
                            .clickable { onRestore() }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(6.dp))
                            .background(colors.bgElevated), contentAlignment = Alignment.Center) {
                            Icon(AppIcons.AutoAwesome, "原图", tint = colors.textTertiary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("原图", color = colors.textPrimary, fontSize = 10.sp)
                    }
                }
                items(filtered, key = { it.id }) { opt ->
                    val sel = opt.id == filmId && !isShowingOriginal
                    Column(
                        modifier = Modifier.width(80.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) colors.brandPrimary.copy(alpha = 0.25f) else colors.bgSurface)
                            .then(if (sel) Modifier.border(1.5.dp, colors.brandLight1, RoundedCornerShape(10.dp)) else Modifier)
                            .clickable { onFilm(opt.id) }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 真实效果缩略图：引擎按样片渲染的 JPEG（落盘缓存），未生成好时显示胶卷图标占位
                        val thumbBitmap = remember(opt.id, thumbVersion) {
                            val bytes = bridge?.filmThumb(opt.id)
                            if (bytes != null && bytes.isNotEmpty()) {
                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    ?.asImageBitmap()
                            } else null
                        }
                        Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(6.dp))
                            .background(colors.bgElevated), contentAlignment = Alignment.Center) {
                            if (thumbBitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = thumbBitmap,
                                    contentDescription = opt.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(AppIcons.Film, opt.name, tint = colors.brandLight1.copy(alpha = 0.7f), modifier = Modifier.size(26.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        MarqueeFilmName(
                            text = opt.name,
                            isSelected = sel,
                            color = colors.textPrimary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // 相纸入口：正片（反转片）直接扫描、无相纸这一步（对齐权威 refreshPaperLabel/showPaperPicker）
        val isPositive = films.firstOrNull { it.id == filmId }?.isPositive == true
        val noPaper = paperId == PAPER_NONE
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.bgSurface)
            .then(if (isPositive) Modifier else Modifier.clickable { onShowPaper(true) }).padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            when {
                isPositive -> Text("相纸：无相纸 · 直接扫描（反转片）", color = colors.textSecondary, fontSize = 12.sp)
                noPaper -> {
                    Text("相纸：无相纸", color = colors.textSecondary, fontSize = 12.sp)
                    Icon(AppIcons.ArrowDown, contentDescription = "更换相纸", tint = colors.brandLight1, modifier = Modifier.size(18.dp))
                }
                else -> {
                    val curPaper = papers.firstOrNull { it.id == paperId }?.name ?: paperId
                    Text("相纸 / 拷贝片：$curPaper", color = colors.textPrimary, fontSize = 12.sp)
                    Icon(AppIcons.ArrowDown, contentDescription = "更换相纸", tint = colors.brandLight1, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PickerHeader(title: String, backdrop: com.kyant.backdrop.Backdrop, onClose: () -> Unit) {
    val colors = LocalLazyFilmColors.current
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Icon(AppIcons.ArrowUp, contentDescription = "收起", tint = colors.textSecondary,
            modifier = Modifier.size(22.dp).clickable(onClick = onClose))
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalLazyFilmColors.current
    Box(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.brandPrimary.copy(0.3f) else colors.bgSurface)
            .then(if (selected) Modifier.border(1.dp, colors.brandLight1, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)
    ) { Text(label, color = colors.textPrimary, fontSize = 12.sp) }
}

/**
 * 相纸艺术卡片配色：由权威 paperNote（MainActivity L18102-18124）的实测数据派生——
 * 渐变跨度 = 黑位深浅（p05 越小越黑），圆点浓淡 = 饱和度，色调 = 相纸性格。
 * 纯绘制、不调引擎、不落盘。
 */
private data class PaperArt(
    val top: Color, val mid: Color, val bottom: Color,
    val orbLight: Color, val orbDark: Color,
)

private fun paperArtOf(id: String): PaperArt = when (id) {
    "kodak_portra_endura" -> PaperArt(
        Color(0xFFF2EDE4), Color(0xFF8A8378), Color(0xFF3A3835),
        Color(0xFFE8D9BF), Color(0xFFB9A98C))
    "kodak_endura_premier" -> PaperArt(
        Color(0xFFF7F7F7), Color(0xFF6A6A6A), Color(0xFF0F0F0F),
        Color(0xFFFFFFFF), Color(0xFF9A9A9A))
    "kodak_supra_endura" -> PaperArt(
        Color(0xFFF0F0F0), Color(0xFF5A5A5A), Color(0xFF1A1A1A),
        Color(0xFFF0F0F0), Color(0xFF8C8C8C))
    "kodak_ultra_endura" -> PaperArt(
        Color(0xFFEEF2F6), Color(0xFF5A6672), Color(0xFF16191C),
        Color(0xFFDFE9F2), Color(0xFF7F93A6))
    "kodak_ektacolor_edge" -> PaperArt(
        Color(0xFFEEEEEC), Color(0xFF777775), Color(0xFF3D3D3B),
        Color(0xFFE6E6E2), Color(0xFFA8A8A2))
    "fujifilm_crystal_archive_typeii" -> PaperArt(
        Color(0xFFEEF5F0), Color(0xFF4A6B5C), Color(0xFF0D1512),
        Color(0xFFCFE8D6), Color(0xFF5F9A78))
    "ilford_multigrade_iv_rc" -> PaperArt(
        Color(0xFFF4F4F4), Color(0xFF6E6E6E), Color(0xFF242424),
        Color(0xFFFFFFFF), Color(0xFF9C9C9C))
    "kodak_2383" -> PaperArt(
        Color(0xFFF7EFDC), Color(0xFF7A6244), Color(0xFF0A0805),
        Color(0xFFF0D9A8), Color(0xFFA8813F))
    "kodak_2393" -> PaperArt(
        Color(0xFFFAF0E0), Color(0xFF6B5030), Color(0xFF050403),
        Color(0xFFF5D9A0), Color(0xFF9C6A28))
    else -> PaperArt( // 无相纸 / 未知：中性灰阶
        Color(0xFFEEEEEE), Color(0xFF777777), Color(0xFF222222),
        Color(0xFFDDDDDD), Color(0xFF888888))
}

/** 相纸特性描述：权威 paperNote 原文（MainActivity L18110-18124），无相纸见 L18073 */
private fun paperNoteOf(id: String): String = when (id) {
    "kodak_portra_endura" -> "中性 · 黑位偏亮 · 人像常用"
    "kodak_endura_premier" -> "更亮 · 深黑 · 对比高"
    "kodak_supra_endura" -> "中性 · 对比适中"
    "kodak_ultra_endura" -> "中性偏冷 · 对比适中"
    "kodak_ektacolor_edge" -> "低饱和 · 柔和"
    "fujifilm_crystal_archive_typeii" -> "偏青 · 富士色调"
    "ilford_multigrade_iv_rc" -> "可变反差 · 高光留细节 · 黑白"
    "kodak_2383" -> "偏暖 · 深黑 · 电影感"
    "kodak_2393" -> "最深黑 · 高饱和"
    PAPER_NONE -> "胶片本身 · 橙片基 · 反相不还原"
    else -> ""
}

/** 卡片角标：2 字极简标签（从 paperNote 提炼，一眼可横向对比） */
private fun paperBadgeOf(id: String): String = when (id) {
    "kodak_portra_endura" -> "人像"
    "kodak_endura_premier" -> "高反差"
    "kodak_supra_endura" -> "通用"
    "kodak_ultra_endura" -> "冷调"
    "kodak_ektacolor_edge" -> "柔和"
    "fujifilm_crystal_archive_typeii" -> "青绿"
    "ilford_multigrade_iv_rc" -> "黑白"
    "kodak_2383" -> "电影"
    "kodak_2393" -> "浓艳"
    PAPER_NONE -> "原片"
    else -> ""
}

/** 相纸艺术卡片：垂直渐变（白点→黑位）+ 中央色点（饱和度/色调） */
@Composable
private fun PaperArtCard(art: PaperArt, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(
            Brush.verticalGradient(listOf(art.top, art.mid, art.bottom))
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(20.dp).clip(CircleShape).background(
                Brush.radialGradient(listOf(art.orbLight, art.orbDark))
            )
        )
    }
}

@Composable
private fun StockRow(options: List<StockOption>, selectedId: String, onSelect: (String) -> Unit) {
    val colors = LocalLazyFilmColors.current
    val density = LocalDensity.current
    // 相纸列表选中回中（与胶片列表同款）：每张 80dp + 8dp 间距
    val listState = rememberLazyListState()
    var viewportWidth by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectedId, options, viewportWidth) {
        if (viewportWidth <= 0 || options.isEmpty()) return@LaunchedEffect
        val index = options.indexOfFirst { it.id == selectedId }
        if (index < 0) return@LaunchedEffect
        delay(120)
        val slot = with(density) { 88.dp.toPx() }.toInt()
        val half = with(density) { 40.dp.toPx() }.toInt()
        val target = maxOf(0, index * slot - (viewportWidth / 2 - half))
        val vi = (target / slot).coerceAtMost((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        listState.animateScrollToItem(vi, target % slot)
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.onSizeChanged { viewportWidth = it.width },
    ) {
        items(options, key = { it.id }) { opt ->
            val sel = opt.id == selectedId
            Column(
                modifier = Modifier.width(80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) colors.brandPrimary.copy(alpha = 0.25f) else colors.bgSurface)
                    .then(if (sel) Modifier.border(1.5.dp, colors.brandLight1, RoundedCornerShape(10.dp)) else Modifier)
                    .clickable { onSelect(opt.id) }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.fillMaxWidth().height(40.dp)) {
                    PaperArtCard(paperArtOf(opt.id), Modifier.fillMaxWidth().height(40.dp))
                    // 2 字极简角标：一眼横向对比各相纸性格
                    val badge = paperBadgeOf(opt.id)
                    if (badge.isNotEmpty()) {
                        Box(
                            Modifier.align(Alignment.BottomEnd)
                                .padding(end = 3.dp, bottom = 0.dp)
                                .padding(horizontal = 2.dp),
                        ) {
                            Text(badge, color = Color.White, fontSize = 8.sp)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                MarqueeFilmName(
                    text = opt.name,
                    isSelected = sel,
                    color = colors.textPrimary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 数据驱动参数 tab：按 ParamSpec 渲染滑块/开关/枚举（对齐 SpektraLab 冲印/光学/尺度等 tab） */
@Composable
private fun ParamTabPanel(
    tabSpec: ParamTab,
    subTab: Int,
    onSubTab: (Int) -> Unit,
    wireState: MutableMap<String, Any>,
    backdrop: com.kyant.backdrop.Backdrop,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onEdit: () -> Unit = {},
    // 构图裁切/旋转回调（仅构图 tab 用到，带默认值不影响其它 tab）
    cropPending: com.filmlens.nicolink.film.CropRect = com.filmlens.nicolink.film.CropRect(),
    cropRatioIdx: Int = 0,
    onCropRatio: (Int) -> Unit = {},
    onCropApply: () -> Unit = {},
    onQuarter: (Int) -> Unit = {},
    showGuideLines: Boolean = true,
    onGuideChange: (Boolean) -> Unit = {},
    // 尺度物理尺幅回调（仅尺度 tab 用到，带默认值）
    fmtCardIdx: Int = 0,
    overscanOn: Boolean = false,
    onFormatPick: (Int) -> Unit = {},
    // 手动开片边回调（仅边框 tab 用到，带 gate 校验）
    onOverscanToggle: ((Boolean) -> Unit)? = null,
    // 专业模式：false 时隐藏 advanced 参数（普通用户只看得懂的核心旋钮）
    proMode: Boolean = false,
) {
    val colors = LocalLazyFilmColors.current
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        // 子页切换（光学 5 页 / 边框 2 页等）。普通模式过滤掉「该子页全是专业参数」的页，
        // 避免点进去空白（如光学的曲线/分级在普通模式下无内容）。
        val visibleSubTabIndices = tabSpec.subTabs.indices.filter { i ->
            proMode || tabSpec.params.any { p -> (p.subTab == -1 || p.subTab == i) && !p.isAdvanced() }
        }
        if (visibleSubTabIndices.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visibleSubTabIndices) { i ->
                    ChoiceChip(tabSpec.subTabs[i], i == subTab) { onSubTab(i) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (tabSpec.label == "光学") {
            Text("光学是叠在成片上的调色；未选胶片时改动可能看不出效果。",
                color = colors.textTertiary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(4.dp))
        }
        // 当前子页下的参数（按 subTab 过滤；-1=全部；普通模式再过滤专业参数）
        val visible = tabSpec.params.filter {
            (it.subTab == -1 || it.subTab == subTab) && (proMode || !it.isAdvanced())
        }
        // 滑块类参数：顶部一排 chip 选择当前参数，下方只渲染一个滑块（对齐 SpektraLab 共用滑块）
        val sliders = visible.filter { it.kind == ParamKind.SLIDER }
        if (sliders.isNotEmpty()) {
            var sliderIdx by remember(subTab) { mutableIntStateOf(0) }
            if (sliderIdx >= sliders.size) sliderIdx = 0
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sliders.size) { i ->
                    ChoiceChip(sliders[i].short.ifEmpty { sliders[i].label }, i == sliderIdx) { sliderIdx = i }
                }
            }
            Spacer(Modifier.height(8.dp))
            SliderControl(sliders[sliderIdx], wireState, onDragStart, onDragEnd, onEdit)
            Spacer(Modifier.height(8.dp))
        }
        // 非滑块控件（开关/选择/曲线/色轮/动作面板等）保持逐个显示
        visible.filter { it.kind != ParamKind.SLIDER }.forEach { spec ->
            ParamControl(spec, wireState, backdrop, onDragStart, onDragEnd, onEdit,
                cropPending, cropRatioIdx, onCropRatio, onCropApply, onQuarter, showGuideLines, onGuideChange,
                fmtCardIdx, overscanOn, onFormatPick, onOverscanToggle)
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** 共用滑块模式的单个滑块：数值+说明 + 居中缩短的 GlassThumbSlider，避免误触屏幕边缘 */
@Composable
private fun SliderControl(
    spec: ParamSpec,
    wire: MutableMap<String, Any>,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = LocalLazyFilmColors.current
    val defF = (spec.def as? Number)?.toFloat() ?: 0f
    val wireValue = (wire[spec.key] as? Number)?.toFloat() ?: (defF * spec.scale)
    val uiValue = wireValue / spec.scale
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(spec.label, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(spec.format.format(uiValue), color = colors.brandLight1, fontSize = 13.sp)
        }
        if (spec.note.isNotEmpty()) {
            Text(spec.note, color = colors.textTertiary, fontSize = 10.sp, maxLines = 2)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GlassThumbSlider(
                label = null,
                value = ((uiValue - spec.min) / spec.step).roundToInt(),
                onValueChange = { idx ->
                    val ui = spec.min + idx * spec.step
                    wire[spec.key] = ui * spec.scale
                    onEdit()
                },
                onDragStart = onDragStart,
                onDragEnd = onDragEnd,
                range = 0..((spec.max - spec.min) / spec.step).roundToInt().coerceAtLeast(1),
                glassThumb = true,
                enabled = true,
                // R3：居中且缩短（85% 宽），避免误触屏幕边缘
                modifier = Modifier.fillMaxWidth(0.85f).height(40.dp)
            )
        }
    }
}

/** 单个参数控件：SLIDER 用 GlassThumbSlider，TOGGLE 用 LiquidToggle，CHOICE 用普通 chip 行 */
@Composable
private fun ParamControl(
    spec: ParamSpec,
    wire: MutableMap<String, Any>,
    backdrop: com.kyant.backdrop.Backdrop,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onEdit: () -> Unit = {},
    cropPending: com.filmlens.nicolink.film.CropRect = com.filmlens.nicolink.film.CropRect(),
    cropRatioIdx: Int = 0,
    onCropRatio: (Int) -> Unit = {},
    onCropApply: () -> Unit = {},
    onQuarter: (Int) -> Unit = {},
    showGuideLines: Boolean = true,
    onGuideChange: (Boolean) -> Unit = {},
    fmtCardIdx: Int = 0,
    overscanOn: Boolean = false,
    onFormatPick: (Int) -> Unit = {},
    onOverscanToggle: ((Boolean) -> Unit)? = null,
) {
    val colors = LocalLazyFilmColors.current
    when (spec.kind) {
        ParamKind.TOGGLE -> {
            // 液态玻璃开关（LiquidToggle）：已修复整条轨道可点击，保留液态视觉同时保证可靠触发
            val checked = wire[spec.key] as? Boolean ?: (spec.def as? Boolean ?: false)
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(spec.label, color = colors.textPrimary, fontSize = 13.sp)
                com.filmlens.nicolink.ui.shared.liquid.LiquidToggle(
                    selected = { wire[spec.key] as? Boolean ?: (spec.def as? Boolean ?: false) },
                    onSelect = {
                        // 片边开关走带校验的回调（gate 不匹配时自愈重裁），其余开关照常
                        if (spec.key == "overscan_active" && onOverscanToggle != null) onOverscanToggle(it)
                        else { wire[spec.key] = it; onEdit() }
                    },
                    backdrop = backdrop,
                )
            }
        }
        ParamKind.SLIDER -> {
            val defF = (spec.def as? Number)?.toFloat() ?: 0f
            // wire 里存的是 wire 值（= ui × scale）；读回显示要除以 scale 还原成 UI 值，
            // 否则 scale≠1 的滑块（如预闪 scale=0.04）拖到最大写 wire=0.04 后，
            // 读回 uiValue=0.04 会让滑块跳回接近 0 —— 表现为"只能拖到0.04 + 被抢滑块"。
            val wireValue = (wire[spec.key] as? Number)?.toFloat() ?: (defF * spec.scale)
            val uiValue = wireValue / spec.scale
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(spec.label, color = colors.textPrimary, fontSize = 13.sp)
                    Text(spec.format.format(uiValue), color = colors.brandLight1, fontSize = 12.sp)
                }
                if (spec.note.isNotEmpty()) {
                    Text(spec.note, color = colors.textTertiary, fontSize = 10.sp, maxLines = 1)
                }
                // 液态玻璃滑块：UI 值 × step 量化 → wire 值 = ui × scale
                GlassThumbSlider(
                    label = null,
                    value = ((uiValue - spec.min) / spec.step).roundToInt(),
                    onValueChange = { idx ->
                        val ui = spec.min + idx * spec.step
                        wire[spec.key] = ui * spec.scale
                        onEdit()
                    },
                    onDragStart = onDragStart,
                    onDragEnd = onDragEnd,
                    range = 0..((spec.max - spec.min) / spec.step).roundToInt().coerceAtLeast(1),
                    glassThumb = true,
                    enabled = true,
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                )
            }
        }
        ParamKind.CHOICE -> {
            val cur = wire[spec.key]
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(spec.label, color = colors.textPrimary, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(spec.choices.size) { i ->
                        ChoiceChip(spec.choices[i], spec.choiceValues[i] == cur) {
                            wire[spec.key] = spec.choiceValues[i]
                            onEdit()
                        }
                    }
                }
            }
        }
        ParamKind.CURVE -> {
            @Suppress("UNCHECKED_CAST")
            var pts = (wire[spec.key] as? List<List<com.filmlens.nicolink.film.CurvePoint>>)
                ?: com.filmlens.nicolink.film.defaultCurves()
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(spec.label, color = colors.textPrimary, fontSize = 13.sp)
                if (spec.note.isNotEmpty()) {
                    Text(spec.note, color = colors.textTertiary, fontSize = 10.sp, maxLines = 1)
                }
                CurveEditor(
                    curves = pts,
                    onCurvesChange = { pts = it; wire[spec.key] = it; onEdit() },
                    backdrop = backdrop,
                )
            }
        }
        ParamKind.COLOR_WHEEL -> {
            @Suppress("UNCHECKED_CAST")
            var wheels = (wire[spec.key] as? List<com.filmlens.nicolink.film.ColorWheelState>)
                ?: com.filmlens.nicolink.film.defaultColorWheels()
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(spec.label, color = colors.textPrimary, fontSize = 13.sp)
                if (spec.note.isNotEmpty()) {
                    Text(spec.note, color = colors.textTertiary, fontSize = 10.sp, maxLines = 1)
                }
                ColorWheelEditor(
                    wheels = wheels,
                    onWheelsChange = { wheels = it; wire[spec.key] = it; onEdit() },
                    backdrop = backdrop,
                )
            }
        }
        ParamKind.CROP_PROPOSAL -> {
            // 裁切提案面板：比例卡片横滚 + 应用按钮（框在主预览图上拖拽，见 CropOverlay）
            CropProposalPanel(
                ratioIdx = cropRatioIdx,
                onRatio = onCropRatio,
                onApply = onCropApply,
                crop = cropPending,
                backdrop = backdrop,
            )
        }
        ParamKind.ROTATE_QUARTER -> {
            // 90° 旋转动作 + 参考线开关
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                listOf("↺ 左90°", "↻ 右90°", "归零").forEachIndexed { i, label ->
                    ChoiceChip(label, false) { onQuarter(i) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("参考线", color = colors.textPrimary, fontSize = 13.sp)
                LiquidToggle(selected = { showGuideLines }, onSelect = { onGuideChange(it) },
                    backdrop = rememberCanvasBackdrop { drawRect(colors.bgSurface) }, accentColor = colors.brandPrimary)
            }
        }
        ParamKind.FORMAT_CARD -> {
            // 物理尺幅卡片条（权威尺度页唯一控件）：10 片门横滚，点卡片联动尺幅+片门
            FormatCardStrip(
                selectedIdx = fmtCardIdx,
                overscanOn = overscanOn,
                onPick = onFormatPick,
                backdrop = backdrop,
            )
        }
    }
}

@Composable
private fun ExportPanel(info: String?, onExport: () -> Unit) {
    val colors = LocalLazyFilmColors.current
    Column(Modifier.fillMaxWidth().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("导出 JPEG", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(info ?: "全分辨率导出到 DCIM/NicoLink（相册可见）", color = colors.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(colors.brandPrimary).clickable(onClick = onExport), contentAlignment = Alignment.Center) {
            Text("导出", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ───────────────────────────── 设置页 ─────────────────────────────

/**
 * 打开各厂商「后台数据 / 后台活动 / 自启动」设置页（用户实测开启「后台数据」后台不再断连）。
 * 按机型依次尝试已知 component，全部失败回退到应用详情页（那里一定能找到后台数据开关）。
 */
private fun openBackgroundDataSettings(context: android.content.Context) {
    val candidates = listOf(
        // 荣耀 MagicOS / 华为 EMUI
        Triple("com.hihonor.systemmanager", "com.hihonor.systemmanager.appmanager.ui.AppManagerActivity", "后台数据"),
        Triple("com.huawei.systemmanager", "com.huawei.systemmanager.netassistant.ui.NetAssistantActivity", "后台数据"),
        // 小米 / HyperOS
        Triple("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity", "自启动"),
        // vivo / OriginOS
        Triple("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity", "后台数据"),
        // OPPO / ColorOS
        Triple("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity", "自启动"),
    )
    for ((pkg, cls, _) in candidates) {
        try {
            val intent = android.content.Intent().apply {
                component = android.content.ComponentName(pkg, cls)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (_: Exception) { }
    }
    openAppDetails(context)
}

/** 回退：应用详情页（可手动找到「移动数据 / 后台数据 / 自启动」） */
private fun openAppDetails(context: android.content.Context) {
    try {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) { }
}

@Composable
fun SettingsScreen(
    nav: AppNav,
    themeMode: com.filmlens.nicolink.ui.theme.ThemeMode = com.filmlens.nicolink.ui.theme.ThemeMode.SYSTEM,
    onThemeChanged: (com.filmlens.nicolink.ui.theme.ThemeMode) -> Unit = {},
    liquidGlassEnabled: Boolean = true,
    onLiquidGlassChanged: (Boolean) -> Unit = {},
    accountRepo: AccountRepository? = null,
    heatmapManager: HeatmapManager? = null,
    accountVersion: Int = 0,
    onOpenUserCenter: () -> Unit = {},
    onNeedLogin: () -> Unit = {},
    /** 点击「软件版本号」——手动检查更新（不受主页静默检查的节流限制）；长按 5 秒仍进调试页 */
    onCheckUpdate: () -> Unit = {},
) {
    val colors = LocalLazyFilmColors.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    // 自动显影相关状态提到顶层，供 content 与 overlay（悬浮预设面板）共享
    val savedPresets = remember { com.filmlens.nicolink.film.FilmPresets.load(context) }
    var autoDev by remember { mutableStateOf(UiPrefs.autoDevelopEnabled) }
    var devJpg by remember { mutableStateOf(UiPrefs.autoDevelopJpg) }
    var devRaw by remember { mutableStateOf(UiPrefs.autoDevelopRaw) }
    var devPreset by remember { mutableStateOf(UiPrefs.autoDevelopPreset) }
    var showPresetPicker by remember { mutableStateOf(false) }
    // 开关液态效果用的 backdrop（开关在 content 内，无页面 backdrop，用纯色画布兜底）
    val toggleBackdrop = rememberCanvasBackdrop { drawRect(colors.bgSurface) }
    GlassPage(
        topBar = { backdrop ->
            GlassTopBar(title = "设置", backdrop = backdrop, onBack = { nav.pop() })
        },
        content = {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            ) {
                // 标题/状态栏透明：列表可滚到顶栏与状态栏下方，顶部留白由滚动内容内的 Spacer 提供
                Spacer(Modifier.statusBarsPadding().height(64.dp))
                // ── 账号面板（头像行 + 拍照热力图）──
                if (accountRepo != null && heatmapManager != null) {
                    val loggedIn = remember(accountVersion) { accountRepo.isLoggedIn }
                    val nickname = remember(accountVersion) { accountRepo.nickname }
                    val avatar = remember(accountVersion) { accountRepo.avatar }
                    AccountPanelSection(
                        loggedIn = loggedIn,
                        nickname = nickname,
                        avatar = avatar,
                        heatmapGrid = heatmapManager.grid,
                        onOpenUserCenter = onOpenUserCenter,
                        onNeedLogin = onNeedLogin,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                // ── 外观（主题切换，参考 lazyfilm AppearanceSection）──
                SectionTitle("外观")
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(12.dp)).background(colors.bgSurface)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("主题", color = colors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        val modes = listOf(
                            com.filmlens.nicolink.ui.theme.ThemeMode.SYSTEM to "默认",
                            com.filmlens.nicolink.ui.theme.ThemeMode.LIGHT to "亮色",
                            com.filmlens.nicolink.ui.theme.ThemeMode.DARK to "暗色",
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            modes.forEach { (mode, label) ->
                                ChoiceChip(label, themeMode == mode) { onThemeChanged(mode) }
                            }
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(color = colors.bgDivider)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("液态玻璃", color = colors.textPrimary, fontSize = 14.sp)
                            Text("关闭后控件不再折射画面内容，使用实色界面", color = colors.textTertiary, fontSize = 11.sp)
                        }
                        com.filmlens.nicolink.ui.shared.liquid.LiquidToggle(
                            selected = { liquidGlassEnabled },
                            onSelect = { onLiquidGlassChanged(it) },
                            backdrop = toggleBackdrop,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SectionTitle("相机连接")
                var autoConnect by remember { mutableStateOf(UiPrefs.autoConnectCamera) }
                var keepAlive by remember { mutableStateOf(UiPrefs.keepAliveEnabled) }
                val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                val readBatteryIgnored = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
                        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
                    else true
                }
                var batteryIgnored by remember { mutableStateOf(readBatteryIgnored()) }
                // 从系统电池设置返回后刷新状态（Activity ON_RESUME）
                val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            batteryIgnored = readBatteryIgnored()
                        }
                    }
                    val lifecycle = lifecycleOwner.lifecycle
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(12.dp)).background(colors.bgSurface)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    // 自动连接
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("自动连接", color = colors.textPrimary, fontSize = 14.sp)
                            Text("开启后 App 启动自动连接上次相机", color = colors.textTertiary, fontSize = 11.sp)
                        }
                        com.filmlens.nicolink.ui.shared.liquid.LiquidToggle(
                            selected = { autoConnect },
                            onSelect = { autoConnect = it; UiPrefs.autoConnectCamera = it },
                            backdrop = toggleBackdrop,
                        )
                    }
                    androidx.compose.material3.HorizontalDivider(color = colors.bgDivider)
                    // 保活通知
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("保活通知", color = colors.textPrimary, fontSize = 14.sp)
                            Text("常驻通知，防止后台断开相机连接", color = colors.textTertiary, fontSize = 11.sp)
                        }
                        com.filmlens.nicolink.ui.shared.liquid.LiquidToggle(
                            selected = { keepAlive },
                            onSelect = {
                                keepAlive = it
                                UiPrefs.keepAliveEnabled = it
                                if (it) com.filmlens.nicolink.ui.KeepAliveController.start()
                                else com.filmlens.nicolink.ui.KeepAliveController.stop()
                            },
                            backdrop = toggleBackdrop,
                        )
                    }
                    androidx.compose.material3.HorizontalDivider(color = colors.bgDivider)
                    // 电池白名单入口
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            .clickable {
                                try {
                                    val intent = android.content.Intent(
                                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (_: Exception) { }
                                }
                            }
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("电池优化白名单", color = colors.textPrimary, fontSize = 14.sp)
                            Text(
                                if (batteryIgnored) "已加入白名单 · 后台连接更稳定" else "未加入 · 后台可能被系统断连",
                                color = if (batteryIgnored) colors.brandLight1 else colors.brandDanger, fontSize = 11.sp
                            )
                        }
                        Text("›", color = colors.brandLight1, fontSize = 20.sp)
                    }
                    androidx.compose.material3.HorizontalDivider(color = colors.bgDivider)
                    // 后台数据/自启动入口：用户实测开启「后台数据」后断连消失（对齐 LunaShare 厂商引导）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            .clickable { openBackgroundDataSettings(context) }
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("后台数据 / 自启动", color = colors.textPrimary, fontSize = 14.sp)
                            Text("允许后台联网，防止 ROM 后台冻结断连", color = colors.textTertiary, fontSize = 11.sp)
                        }
                        Text("›", color = colors.brandLight1, fontSize = 20.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                // ── 自动显影（相机自动上传的照片自动应用预设）──
                SectionTitle("自动显影")
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(12.dp)).background(colors.bgSurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // 总开关
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("开启自动显影", color = colors.textPrimary, fontSize = 14.sp)
                            Text("新上传的照片自动应用选定预设", color = colors.textTertiary, fontSize = 11.sp)
                        }
                        com.filmlens.nicolink.ui.shared.liquid.LiquidToggle(
                            selected = { autoDev },
                            onSelect = { on ->
                                autoDev = on
                                UiPrefs.autoDevelopEnabled = on
                                // 开启时若未选预设，自动选中第一个可用预设，保证开启即可用
                                if (on && devPreset.isBlank() && savedPresets.isNotEmpty()) {
                                    devPreset = savedPresets[0].name
                                    UiPrefs.autoDevelopPreset = devPreset
                                }
                            },
                            backdrop = toggleBackdrop,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // 应用预设：点击弹出预设选择面板（置灰不可选当开关关闭时）
                    val rowAlpha = if (autoDev) 1f else 0.4f
                    Column(
                        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = rowAlpha }
                            .then(if (autoDev) Modifier.clickable { showPresetPicker = true } else Modifier)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("应用预设", color = colors.textSecondary, fontSize = 12.sp)
                                Text(if (devPreset.isBlank()) "点击选择预设" else devPreset,
                                    color = colors.textPrimary, fontSize = 13.sp)
                            }
                            Text("›", color = colors.brandLight1, fontSize = 20.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // 照片类型 JPG/RAW 多选（开关关闭时置灰不可点）
                    Column(modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = rowAlpha }) {
                        Text("照片类型", color = colors.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChoiceChip("JPG", devJpg) { if (autoDev) { devJpg = !devJpg; UiPrefs.autoDevelopJpg = devJpg } }
                            ChoiceChip("RAW", devRaw) { if (autoDev) { devRaw = !devRaw; UiPrefs.autoDevelopRaw = devRaw } }
                        }
                    }
                }

                SectionTitle("关于")
                // 与「外观 / 相机连接 / 自动显影」同款：单块 bgSurface 圆角面板 + 分隔线行
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(12.dp)).background(colors.bgSurface)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    // 真实版本号（从包信息读；构建产物版本与 build.gradle.kts 保持一致）
                    val pkgInfo = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() }
                    val versionName: String = remember(pkgInfo) {
                        @Suppress("DEPRECATION")
                        pkgInfo?.versionName ?: ""
                    }
                    val aboutRows: List<Triple<String, String, () -> Unit>> = listOf(
                        Triple("懒胶片", "", {
                            runCatching { uriHandler.openUri("https://takeoneai.cn/lazyfilm/") }
                            Unit
                        }),
                        Triple("关注小红书", "", {
                            try {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("xhsdiscover://user/615000ac0000000002018b45")
                                )
                                intent.setPackage("com.xingin.xhs")
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                runCatching { uriHandler.openUri("https://www.xiaohongshu.com/") }
                            }
                        }),
                        Triple("开源许可", "第三方组件与许可声明", { nav.push(Route.LICENSES) }),
                        Triple("用户协议", "", {
                            runCatching { uriHandler.openUri("https://takeoneai.cn/nicolink/agreement.html") }
                            Unit
                        }),
                        Triple("隐私政策", "", {
                            runCatching { uriHandler.openUri("https://takeoneai.cn/nicolink/privacy.html") }
                            Unit
                        }),
                        Triple("软件版本号", if (versionName.isNotBlank()) "v$versionName" else "v0.1.0", { onCheckUpdate() }),
                    )
                    aboutRows.forEachIndexed { index, (title, subtitle, action) ->
                        if (index > 0) {
                            androidx.compose.material3.HorizontalDivider(color = colors.bgDivider)
                        }
                        val isVersionRow = title == "软件版本号"
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .then(
                                    if (isVersionRow) Modifier.pointerInput(Unit) {
                                        // 短按松手 → 手动检查更新；按住满 5 秒 → 进调试页
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            val releasedEarly = withTimeoutOrNull(5000L) {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    if (event.changes.all { !it.pressed }) break
                                                }
                                            }
                                            if (releasedEarly == null) {
                                                nav.push(Route.DEBUG)
                                            } else {
                                                onCheckUpdate()
                                            }
                                        }
                                    } else Modifier.clickable(onClick = action)
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(title, color = colors.textPrimary, fontSize = 14.sp)
                                if (subtitle.isNotBlank()) {
                                    Text(subtitle, color = colors.textTertiary, fontSize = 11.sp)
                                }
                            }
                            if (title != "软件版本号") {
                                Icon(AppIcons.ChevronRight, contentDescription = null,
                                    tint = colors.iconSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        // 悬浮预设选择面板：overlay 兄弟浮层覆盖全屏，不受 content 滚动约束（真正的弹出层）
        overlay = { backdrop ->
            AnimatedVisibility(
                visible = showPresetPicker,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.45f))
                        .clickable { showPresetPicker = false })
                    LiquidPanel(backdrop = backdrop, shape = RoundedCornerShape(24.dp)) {
                        Column(
                            modifier = Modifier.clip(RoundedCornerShape(24.dp))
                                .background(colors.bgElevated.copy(alpha = 0.96f))
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                                .widthIn(min = 320.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("选择预设", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text("✕", color = colors.textSecondary, fontSize = 16.sp,
                                    modifier = Modifier.clip(CircleShape).clickable(onClick = { showPresetPicker = false }).padding(4.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("已保存的预设（${savedPresets.size}）", color = colors.textSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(6.dp))
                            if (savedPresets.isEmpty()) {
                                Text("暂无预设，请先在显影页保存预设", color = colors.textTertiary, fontSize = 12.sp)
                            } else {
                                Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                                    savedPresets.forEach { p ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (devPreset == p.name) colors.brandPrimary.copy(0.25f) else colors.bgSurface.copy(0.5f))
                                                .clickable {
                                                    devPreset = p.name; UiPrefs.autoDevelopPreset = p.name
                                                    showPresetPicker = false
                                                }.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(p.name, color = colors.textPrimary, fontSize = 13.sp)
                                                Text(p.filmId, color = colors.textTertiary, fontSize = 11.sp)
                                            }
                                            if (devPreset == p.name) Text("✓", color = colors.brandLight1, fontSize = 16.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

// ══════════════════════════════════════════════════════════════
// 光学调色编辑器（对齐 SpektraLab 曲线页 / 分级页）
// ══════════════════════════════════════════════════════════════

/** Photoshop 式 5 通道曲线编辑器：平滑 S 曲线、可增删点、首尾点固定 x 只可移 y（无断头）。坐标归一化 0..1（y 向上）。 */
@Composable
private fun CurveEditor(
    curves: List<List<com.filmlens.nicolink.film.CurvePoint>>,
    onCurvesChange: (List<List<com.filmlens.nicolink.film.CurvePoint>>) -> Unit,
    backdrop: com.kyant.backdrop.Backdrop,
) {
    val colors = LocalLazyFilmColors.current
    var channel by remember { mutableIntStateOf(0) }
    val chNames = listOf("RGB", "亮度", "R", "G", "B")
    val defaultPts = listOf(
        com.filmlens.nicolink.film.CurvePoint(0f, 0f),
        com.filmlens.nicolink.film.CurvePoint(1f, 1f),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        chNames.forEachIndexed { i, name ->
            ChoiceChip(name, channel == i) { channel = i }
        }
    }
    Spacer(Modifier.height(6.dp))
    // 本地曲线状态：拖动时只改本地（Canvas 实时跟手重绘），松手/加点/删点才提交 wire 触发渲染。
    // 这样避免拖动每帧都写 wire → 触发引擎重渲（每次 setViewCurves 80 个 double）导致的卡顿。
    var localPts by remember { mutableStateOf(curves.getOrNull(0) ?: defaultPts) }
    // 切换通道 / 外部数据变化（如恢复原图）时同步本地状态
    LaunchedEffect(channel, curves) {
        val external = curves.getOrNull(channel)
        if (external != null && external != localPts) localPts = external
    }
    // 用 rememberUpdatedState 读取最新值，避免 pointerInput lambda 持有旧数组/通道
    val curvesState = rememberUpdatedState(curves)
    val chState = rememberUpdatedState(channel)
    var dragIndex by remember { mutableIntStateOf(-1) }
    val hitRadius = 64f // px，手机端加大命中控制点容差

    // 归一化：x 升序 + 首尾锚点 x 固定 0/1，保证曲线始终从底到顶连贯、无断头
    fun normalize(newPts: List<com.filmlens.nicolink.film.CurvePoint>): List<com.filmlens.nicolink.film.CurvePoint> {
        val sorted = newPts.sortedBy { it.x }.toMutableList()
        if (sorted.isNotEmpty()) {
            sorted[0] = sorted[0].copy(x = 0f)
            sorted[sorted.size - 1] = sorted[sorted.size - 1].copy(x = 1f)
        }
        return sorted
    }

    // 只更新本地显示（Canvas 重绘），不写 wire、不触发渲染
    fun updateLocal(newPts: List<com.filmlens.nicolink.film.CurvePoint>) {
        localPts = normalize(newPts)
    }

    // 提交到 wire（真正触发渲染）：仅在松手 / 加点 / 删点 / 重置时调用
    fun submit() {
        val ch = chState.value
        val updated = curvesState.value.toMutableList()
        while (updated.size <= ch) updated.add(emptyList())
        updated[ch] = normalize(localPts)
        onCurvesChange(updated)
    }

    // 重置当前通道为默认直线
    fun resetChannel() {
        localPts = defaultPts
        val ch = chState.value
        val updated = curvesState.value.toMutableList()
        while (updated.size <= ch) updated.add(emptyList())
        updated[ch] = defaultPts
        onCurvesChange(updated)
    }

    // 重置按钮放 Canvas 上方——外层 320dp 限高会裁掉 Canvas 下方内容，放这里确保可见
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text("重置曲线", color = colors.textSecondary, fontSize = 12.sp,
            modifier = Modifier.clickable { resetChannel() }.padding(horizontal = 8.dp, vertical = 2.dp))
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { off ->
                        val x = (off.x / size.width).coerceIn(0f, 1f)
                        val y = (1f - off.y / size.height).coerceIn(0f, 1f)
                        val cur = localPts.sortedBy { it.x }
                        if (cur.size >= 10) return@detectTapGestures
                        // 新点插在正确的 x 位置（首尾锚点之间）；加点是离散操作，直接提交渲染
                        val insertAt = cur.indexOfFirst { it.x > x }.let { if (it < 0) cur.size else it }
                        val np = cur.toMutableList().also { it.add(insertAt, com.filmlens.nicolink.film.CurvePoint(x, y)) }
                        updateLocal(np)
                        submit()
                    },
                    // 长按删点（手机端比双击更容易触发、无单击等待延迟）
                    onLongPress = { off ->
                        val cur = localPts.sortedBy { it.x }
                        if (cur.size <= 2) return@detectTapGestures
                        val x = off.x / size.width
                        val y = 1f - off.y / size.height
                        // 只删中间点：首尾锚点（index 0 / last）不可删
                        var bestIdx = -1; var bestD = Float.MAX_VALUE
                        for (i in 1 until cur.size - 1) {
                            val p = cur[i]
                            val d = sqrt((p.x - x).toDouble() * (p.x - x) + (p.y - y).toDouble() * (p.y - y)).toFloat()
                            if (d < bestD) { bestD = d; bestIdx = i }
                        }
                        if (bestIdx >= 0 && bestD * size.width < hitRadius) {
                            updateLocal(cur.filterIndexed { i, _ -> i != bestIdx })
                            submit()
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { off ->
                        val x = off.x / size.width; val y = 1f - off.y / size.height
                        val cur = localPts.sortedBy { it.x }
                        var best = -1; var bestD = Float.MAX_VALUE
                        cur.forEachIndexed { i, p ->
                            val d = sqrt((p.x - x).toDouble() * (p.x - x) + (p.y - y).toDouble() * (p.y - y)).toFloat()
                            if (d < bestD) { bestD = d; best = i }
                        }
                        // 命中容差内才拖动；dragIndex 是 sorted 数组下标
                        dragIndex = if (bestD * size.width < hitRadius) best else -1
                    },
                    onDrag = { change, _ ->
                        val cur = localPts.sortedBy { it.x }
                        val idx = dragIndex
                        if (idx in cur.indices) {
                            change.consume()
                            val ny = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                            if (cur.size == 2 || idx == 0 || idx == cur.size - 1) {
                                // 首尾两点（或首尾锚点）：固定 x，只改 y
                                updateLocal(cur.mapIndexed { i, p -> if (i == idx) p.copy(y = ny) else p })
                            } else {
                                // 中间点：x 夹在相邻点之间，保证单调有序无交叉
                                val minX = cur[idx - 1].x + 0.005f
                                val maxX = cur[idx + 1].x - 0.005f
                                val nx = (change.position.x / size.width).coerceIn(minX, maxX)
                                updateLocal(cur.mapIndexed { i, p -> if (i == idx) p.copy(x = nx, y = ny) else p })
                            }
                        }
                    },
                    // 松手才提交渲染（防抖核心）：拖动过程只更新本地 Canvas，不写 wire
                    onDragEnd = { if (dragIndex >= 0) submit(); dragIndex = -1 },
                    onDragCancel = { dragIndex = -1 },
                )
            },
    ) {
        val grid = colors.textTertiary.copy(alpha = 0.25f)
        val line = colors.brandPrimary
        // 网格（对角参考线 = 原图映射）
        for (i in 1..3) {
            val fx = size.width * i / 4f
            val fy = size.height * i / 4f
            drawLine(grid, Offset(fx, 0f), Offset(fx, size.height), strokeWidth = 1f)
            drawLine(grid, Offset(0f, fy), Offset(size.width, fy), strokeWidth = 1f)
        }
        drawLine(grid, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 1f)
        // 平滑 S 曲线：Catmull-Rom → 三次贝塞尔，经过所有控制点，首尾连贯（用本地状态实时绘制）
        val sorted = localPts.sortedBy { it.x }
        if (sorted.size >= 2) {
            val path = Path()
            val px = { v: Float -> v * size.width }
            val py = { v: Float -> (1f - v) * size.height }
            path.moveTo(px(sorted[0].x), py(sorted[0].y))
            for (i in 0 until sorted.size - 1) {
                val p0 = sorted[maxOf(0, i - 1)]
                val p1 = sorted[i]
                val p2 = sorted[i + 1]
                val p3 = sorted[minOf(sorted.size - 1, i + 2)]
                // Catmull-Rom 张力 1/6 转贝塞尔控制点；端点用自身复制（切线自然）
                val c1x = p1.x + (p2.x - p0.x) / 6f
                val c1y = p1.y + (p2.y - p0.y) / 6f
                val c2x = p2.x - (p3.x - p1.x) / 6f
                val c2y = p2.y - (p3.y - p1.y) / 6f
                path.cubicTo(px(c1x), py(c1y), px(c2x), py(c2y), px(p2.x), py(p2.y))
            }
            drawPath(path, line, style = Stroke(width = 5f))
        }
        // 控制点（首尾锚点用不同色；手机端加大点半径）
        sorted.forEachIndexed { i, p ->
            val isAnchor = i == 0 || i == sorted.size - 1
            drawCircle(if (isAnchor) colors.brandLight1 else line, radius = 14f,
                center = Offset(p.x * size.width, (1f - p.y) * size.height))
            drawCircle(colors.textPrimary, radius = 6f,
                center = Offset(p.x * size.width, (1f - p.y) * size.height))
        }
    }
}

/** 4 区间颜色轮：圆盘 HSV 取色 + 明亮度滑块。 */
@Composable
private fun ColorWheelEditor(
    wheels: List<com.filmlens.nicolink.film.ColorWheelState>,
    onWheelsChange: (List<com.filmlens.nicolink.film.ColorWheelState>) -> Unit,
    backdrop: com.kyant.backdrop.Backdrop,
) {
    val colors = LocalLazyFilmColors.current
    val segNames = listOf("主色", "阴影", "中间调", "高光")
    var segment by remember { mutableIntStateOf(0) }
    val cur = wheels.getOrNull(segment) ?: com.filmlens.nicolink.film.ColorWheelState()
    val wheelsState = rememberUpdatedState(wheels)
    val curState = rememberUpdatedState(cur)
    val segState = rememberUpdatedState(segment)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        segNames.forEachIndexed { i, name -> ChoiceChip(name, segment == i) { segment = i } }
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures { off ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val dx = off.x - c.x; val dy = off.y - c.y
                        val rMax = size.width.coerceAtMost(size.height) / 2f
                        val rad = sqrt(dx.toDouble() * dx + dy.toDouble() * dy).toFloat()
                        val hue = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360) % 360).toFloat()
                        val sat = (rad / rMax).coerceIn(0f, 1f)
                        val seg = segState.value
                        val updated = wheelsState.value.toMutableList()
                        while (updated.size <= seg) updated.add(com.filmlens.nicolink.film.ColorWheelState())
                        updated[seg] = curState.value.copy(hue = hue, saturation = sat)
                        onWheelsChange(updated)
                    }
                },
        ) {
            val rMax = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            // 色相环：36 个扇形
            for (i in 0 until 36) {
                val h = i * 10f
                drawArc(Color.hsv(h, 1f, 1f), startAngle = i * 10f - 90f, sweepAngle = 10.1f,
                    useCenter = true, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, size.height))
            }
            // 中心白盘（降低饱和）
            drawCircle(Color.White.copy(alpha = 0.55f), radius = rMax * 0.62f, center = c)
            // 当前选择指示点
            val rad = cur.saturation * rMax
            val ang = Math.toRadians(cur.hue.toDouble())
            val mark = Offset(c.x + rad * cos(ang).toFloat(), c.y + rad * sin(ang).toFloat())
            drawCircle(Color.White, radius = 10f, center = mark)
            drawCircle(cur.hueColor(), radius = 6f, center = mark)
            drawCircle(Color.Black, radius = 11f, center = mark, style = Stroke(2f))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("明亮度 ${String.format("%.2f", cur.luminance)}", color = colors.textSecondary, fontSize = 12.sp)
            GlassThumbSlider(
                label = null,
                value = ((cur.luminance + 1f) * 100f).toInt().coerceIn(0, 200), // -1..1 → 0..200
                range = 0..200,
                onValueChange = { v ->
                    val updated = wheels.toMutableList()
                    while (updated.size <= segment) updated.add(com.filmlens.nicolink.film.ColorWheelState())
                    updated[segment] = cur.copy(luminance = v / 100f - 1f)
                    onWheelsChange(updated)
                },
                glassThumb = true,
                modifier = Modifier.fillMaxWidth().height(40.dp),
            )
        }
    }
}

/** 颜色轮当前色（HSV→RGB，角度用权威 hue 约定） */
private fun com.filmlens.nicolink.film.ColorWheelState.hueColor(): Color = Color.hsv(hue, 1f, 1f)

/** 物理尺幅卡片条（对齐权威 buildFormatPanel）：10 片门横滚，点卡片联动 film_format_mm + overscan_format */
@Composable
private fun FormatCardStrip(
    selectedIdx: Int,
    overscanOn: Boolean,
    onPick: (Int) -> Unit,
    backdrop: com.kyant.backdrop.Backdrop,
) {
    val colors = LocalLazyFilmColors.current
    val cards = com.filmlens.nicolink.film.FORMAT_CARDS
    Text(
        if (overscanOn) "片边已开：点卡换片门会自动从原图重裁" else "选片门：自动开片边并从原图重裁 · 裁切/旋转在相邻子页",
        color = colors.textTertiary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp)
    )
    Spacer(Modifier.height(6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(cards.size) { i ->
            val c = cards[i]
            val selected = i == selectedIdx
            val mmText = if (c.mm == c.mm.toInt().toFloat()) "%.0f".format(c.mm) else "%.1f".format(c.mm)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) colors.brandPrimary.copy(alpha = 0.25f) else colors.bgSurface.copy(alpha = 0.5f))
                    .border(1.dp, if (selected) colors.brandPrimary else colors.textTertiary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .clickable(enabled = true) { onPick(i) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(mmText, color = if (selected) colors.brandLight1 else colors.textPrimary,
                    fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(3.dp))
                Text(c.label, color = if (selected) colors.brandLight1 else colors.textSecondary, fontSize = 10.sp)
            }
        }
    }
}

/** 构图-裁切提案面板：比例卡片横滚 + 应用按钮（对齐 SpektraLab buildCropPage 的卡片条 + commitPendingCrop） */
@Composable
private fun CropProposalPanel(
    ratioIdx: Int,
    onRatio: (Int) -> Unit,
    onApply: () -> Unit,
    crop: com.filmlens.nicolink.film.CropRect,
    backdrop: com.kyant.backdrop.Backdrop,
) {
    val colors = LocalLazyFilmColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text("裁切比例", color = colors.textPrimary, fontSize = 13.sp)
        Spacer(Modifier.height(5.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(com.filmlens.nicolink.film.CROP_RATIOS.size) { i ->
                ChoiceChip(com.filmlens.nicolink.film.CROP_RATIOS[i].label, ratioIdx == i) { onRatio(i) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("框 %.0f%%×%.0f%% @ (%.0f%%,%.0f%%)".format(
            crop.w * 100, crop.h * 100, crop.x * 100, crop.y * 100),
            color = colors.textTertiary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(21.dp))
                .background(colors.brandPrimary).clickable(onClick = onApply),
            contentAlignment = Alignment.Center,
        ) {
            Text("应用裁切", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text("在上方预览图拖动框调整范围；按应用才渲染", color = colors.textTertiary, fontSize = 10.sp)
    }
}

/**
 * 构图-裁切框叠加层：在主预览图上画可拖拽裁切框 + 三分参考线。
 * 框坐标归一化 0..1，按图片 Fit + imageScale 后的显示矩形定位。
 */
@Composable
private fun CropOverlay(
    crop: com.filmlens.nicolink.film.CropRect,
    onCropChange: (com.filmlens.nicolink.film.CropRect) -> Unit,
    imgSize: Pair<Int, Int>?,
    containerSize: Pair<Int, Int>?,
    imageScale: Float,
    showGuide: Boolean,
) {
    val colors = LocalLazyFilmColors.current
    val cropState = rememberUpdatedState(crop)
    // 图片 Fit + 缩放后的显示矩形（px）
    val rect = remember(imgSize, containerSize, imageScale) {
        val (cw, ch) = containerSize ?: (0 to 0)
        val (iw, ih) = imgSize ?: (cw to ch)
        if (cw <= 0 || ch <= 0 || iw <= 0 || ih <= 0) {
            null
        } else {
            val fit = minOf(cw.toFloat() / iw, ch.toFloat() / ih)
            val dw = iw * fit * imageScale
            val dh = ih * fit * imageScale
            val ox = (cw - dw) / 2f
            val oy = (ch - dh) / 2f
            FloatArray(4).also { it[0] = ox; it[1] = oy; it[2] = dw; it[3] = dh }
        }
    }
    var dragMode by remember { mutableIntStateOf(-1) } // 0 移动 1左上 2右上 3左下 4右下
    Canvas(modifier = Modifier.fillMaxSize().pointerInput(rect) {
        detectDragGestures(
            onDragStart = { off ->
                val r = rect ?: return@detectDragGestures
                val (ox, oy, dw, dh) = r
                val c = cropState.value
                val fx = (off.x - ox) / dw; val fy = (off.y - oy) / dh
                dragMode = when {
                    kotlin.math.abs(fx - c.x) < 0.06f && kotlin.math.abs(fy - c.y) < 0.06f -> 1
                    kotlin.math.abs(fx - (c.x + c.w)) < 0.06f && kotlin.math.abs(fy - c.y) < 0.06f -> 2
                    kotlin.math.abs(fx - c.x) < 0.06f && kotlin.math.abs(fy - (c.y + c.h)) < 0.06f -> 3
                    kotlin.math.abs(fx - (c.x + c.w)) < 0.06f && kotlin.math.abs(fy - (c.y + c.h)) < 0.06f -> 4
                    fx in c.x..(c.x + c.w) && fy in c.y..(c.y + c.h) -> 0
                    else -> -1
                }
            },
            onDrag = { change, _ ->
                val r = rect
                if (r != null && dragMode >= 0) {
                    change.consume()
                    val (ox, oy, dw, dh) = r
                    val c = cropState.value
                    val fx = ((change.position.x - ox) / dw).coerceIn(0f, 1f)
                    val fy = ((change.position.y - oy) / dh).coerceIn(0f, 1f)
                    val nc = when (dragMode) {
                        0 -> {
                            val mx = (fx - c.x); val my = (fy - c.y)
                            val nx = (c.x + mx).coerceIn(0f, 1f - c.w)
                            val ny = (c.y + my).coerceIn(0f, 1f - c.h)
                            c.copy(x = nx, y = ny)
                        }
                        1 -> com.filmlens.nicolink.film.CropRect(
                            fx.coerceIn(0f, c.x + c.w - 0.01f), fy.coerceIn(0f, c.y + c.h - 0.01f),
                            (c.x + c.w - fx).coerceIn(0.01f, 1f), (c.y + c.h - fy).coerceIn(0.01f, 1f))
                        2 -> com.filmlens.nicolink.film.CropRect(
                            c.x, fy.coerceIn(0f, c.y + c.h - 0.01f),
                            (fx - c.x).coerceIn(0.01f, 1f), (c.y + c.h - fy).coerceIn(0.01f, 1f))
                        3 -> com.filmlens.nicolink.film.CropRect(
                            fx.coerceIn(0f, c.x + c.w - 0.01f), c.y,
                            (c.x + c.w - fx).coerceIn(0.01f, 1f), (fy - c.y).coerceIn(0.01f, 1f))
                        else -> com.filmlens.nicolink.film.CropRect(
                            c.x, c.y, (fx - c.x).coerceIn(0.01f, 1f), (fy - c.y).coerceIn(0.01f, 1f))
                    }
                    onCropChange(nc)
                }
            },
            onDragEnd = { dragMode = -1 },
        )
    }) {
        val r = rect ?: return@Canvas
        val (ox, oy, dw, dh) = r
        val c = cropState.value
        val l = ox + c.x * dw; val t = oy + c.y * dh
        val rw = c.w * dw; val rh = c.h * dh
        val accent = colors.brandPrimary
        // 裁切框外遮罩（四块半透明）
        val mask = Color.Black.copy(alpha = 0.45f)
        drawRect(mask, topLeft = Offset(ox, oy), size = androidx.compose.ui.geometry.Size(dw, t - oy))
        drawRect(mask, topLeft = Offset(ox, t + rh), size = androidx.compose.ui.geometry.Size(dw, oy + dh - (t + rh)))
        drawRect(mask, topLeft = Offset(ox, t), size = androidx.compose.ui.geometry.Size(l - ox, rh))
        drawRect(mask, topLeft = Offset(l + rw, t), size = androidx.compose.ui.geometry.Size(ox + dw - (l + rw), rh))
        // 参考线（三分）
        if (showGuide) {
            val line = Color.White.copy(alpha = 0.4f)
            for (i in 1..2) {
                drawLine(line, Offset(l + rw * i / 3f, t), Offset(l + rw * i / 3f, t + rh), strokeWidth = 1f)
                drawLine(line, Offset(l, t + rh * i / 3f), Offset(l + rw, t + rh * i / 3f), strokeWidth = 1f)
            }
        }
        // 框线 + 四角把手
        drawRect(accent, topLeft = Offset(l, t), size = androidx.compose.ui.geometry.Size(rw, rh), style = Stroke(2f))
        val hs = 10f
        listOf(Offset(l, t), Offset(l + rw, t), Offset(l, t + rh), Offset(l + rw, t + rh)).forEach { p ->
            drawRect(Color.White, topLeft = Offset(p.x - hs / 2, p.y - hs / 2),
                size = androidx.compose.ui.geometry.Size(hs, hs))
            drawRect(accent, topLeft = Offset(p.x - hs / 2, p.y - hs / 2),
                size = androidx.compose.ui.geometry.Size(hs, hs), style = Stroke(2f))
        }
    }
}
