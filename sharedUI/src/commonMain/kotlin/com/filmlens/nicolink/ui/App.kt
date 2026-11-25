package com.filmlens.nicolink.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.filmlens.nicolink.camera.CameraSession
import com.filmlens.nicolink.camera.splitCameraName
import com.filmlens.nicolink.data.PhotoItem
import com.filmlens.nicolink.data.cloud.AccountRepository
import com.filmlens.nicolink.data.cloud.AppLatestVersion
import com.filmlens.nicolink.data.cloud.HeatmapManager
import com.filmlens.nicolink.data.cloud.SavedCameraDto
import com.filmlens.nicolink.data.cloud.UpdateRepository
import com.filmlens.nicolink.film.FilmBridge
import com.filmlens.nicolink.film.FilmParams
import com.filmlens.nicolink.film.LocalFilmBridge
import com.filmlens.nicolink.camera.SavedCamera
import com.filmlens.nicolink.ui.screen.CameraConnectedPopup
import com.filmlens.nicolink.ui.screen.CameraScreen
import com.filmlens.nicolink.ui.screen.ConnectScreen
import com.filmlens.nicolink.ui.screen.DevelopScreen
import com.filmlens.nicolink.ui.screen.HomeScreen
import com.filmlens.nicolink.ui.screen.LoginOverlay
import com.filmlens.nicolink.ui.screen.MonitorScreen
import com.filmlens.nicolink.ui.screen.PreviewScreen
import com.filmlens.nicolink.ui.screen.SettingsScreen
import com.filmlens.nicolink.ui.screen.UserCenterScreen
import com.filmlens.nicolink.ui.shared.UpdateDialog
import com.filmlens.nicolink.ui.shared.liquid.LocalLiquidGlassEnabled
import com.filmlens.nicolink.ui.shared.liquid.LocalLiquidGlassLightTheme
import com.filmlens.nicolink.ui.theme.LazyFilmTheme
import com.filmlens.nicolink.ui.theme.ThemeMode

/** M0 路由表 */
enum class Route {
    HOME, CAMERA, CONNECT, MONITOR, PREVIEW, DEVELOP, SETTINGS, LICENSES, USER_CENTER, DEBUG
}

/**
 * 英雄视图（照片列表 → 详情共享元素转场）所需的 scope。
 * 由 App 顶层的 SharedTransitionLayout / AnimatedContent 提供，页面按需读取。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** 显影页最近一次调节快照（供详情页「导出 / 分享调节后图」复用同样参数） */
data class DevelopSnapshot(
    val filmId: String,
    val paperId: String,
    val params: FilmParams,
)

/** 轻量导航栈：M0 只支持 push / pop / replace / popToHome */
class AppNav {
    // 用 SnapshotStateList，push/pop 时触发重组，页面才能切换
    private val stack = mutableStateListOf(Route.HOME)

    // M1：当前预览的照片（HomeScreen 点缩略图时设置）
    var selectedPhoto by mutableStateOf<PhotoItem?>(null)
        private set

    // M1.1：预览照片组（供左右滑动切图）
    var previewPhotos by mutableStateOf<List<PhotoItem>>(emptyList())
        private set
    var previewIndex by mutableStateOf(0)
        private set

    // 显影页最近一次调节快照：详情页导出/分享复用同样参数；恢复原图时清空
    var developSnapshot by mutableStateOf<DevelopSnapshot?>(null)
        private set

    /**
     * 显影页设置的返回拦截器：返回 true 表示页面自己处理返回（先落盘再 pop），
     * 全局 BackHandler 与页面顶部返回按钮都先问它。非显影页为 null。
     */
    var developBackInterceptor: (() -> Boolean)? = null

    fun saveDevelop(snapshot: DevelopSnapshot) {
        developSnapshot = snapshot
    }

    fun clearDevelop() {
        developSnapshot = null
    }

    // 主页照片网格滚动状态：提升到 AppNav 持久持有，避免进详情返回后 HOME 重建导致滚动归零
    val homeGridState = LazyGridState()

    // 照片列表缓存：提升到 AppNav 持久持有，避免从相机 Tab 切回/详情返回时 HOME 重建反复同步扫盘导致卡顿
    var photosCache by mutableStateOf<List<com.filmlens.nicolink.data.PhotoItem>>(emptyList())
        private set
    var photosCacheLoaded by mutableStateOf(false)
        private set

    fun updatePhotosCache(list: List<com.filmlens.nicolink.data.PhotoItem>) {
        photosCache = list
        photosCacheLoaded = true
    }

    // 照片列表版本号：删除照片后自增，主页 LaunchedEffect 监听它重读 App 目录
    var photosVersion by mutableStateOf(0)
        private set

    fun bumpPhotos() {
        photosVersion++
    }

    fun openPhoto(photo: PhotoItem, photos: List<PhotoItem>) {
        previewPhotos = photos
        previewIndex = photos.indexOfFirst { it.uri == photo.uri }.coerceAtLeast(0)
        selectedPhoto = photo
        push(Route.PREVIEW)
    }

    /** 左右滑动切换照片时更新索引与当前照片 */
    fun syncPreviewIndex(index: Int) {
        if (previewPhotos.isEmpty()) return
        previewIndex = index.coerceIn(0, previewPhotos.lastIndex)
        selectedPhoto = previewPhotos[previewIndex]
    }

    /**
     * 显影保存编辑缩略图后刷新预览列表：让详情页拿到新的 editedThumb，显示调节后的图。
     * 按当前照片的 displayName 复位索引，避免刷新后跳到别的照片。
     */
    fun refreshPreviewPhotos(photos: List<PhotoItem>) {
        val currentName = previewPhotos.getOrNull(previewIndex)?.displayName
        previewPhotos = photos
        val idx = photos.indexOfFirst { it.displayName == currentName }
        previewIndex = if (idx >= 0) idx else 0
        selectedPhoto = photos.getOrNull(previewIndex) ?: selectedPhoto
    }

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun push(route: Route) {
        if (route != current) stack.add(route)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun replace(route: Route) {
        stack.clear()
        stack.add(route)
    }

    fun popToHome() {
        stack.clear()
        stack.add(Route.HOME)
    }
}

@Composable
fun rememberAppNav(): AppNav = remember { AppNav() }

/** 应用根：套 LazyFilm 主题 + 手写 AnimatedContent 页面栈 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun App(
    filmBridge: FilmBridge? = null,
    initialThemeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeChanged: ((ThemeMode) -> Unit)? = null,
    onStatusBarChanged: ((Boolean) -> Unit)? = null,
    onExitApp: (() -> Unit)? = null,
) {
    var themeMode by remember { mutableStateOf(initialThemeMode) }
    // 液态玻璃总开关（设置页可切换，持久化；关闭后 GlassPage 给控件传实色 backdrop）
    var liquidGlass by remember { mutableStateOf(UiPrefs.liquidGlassEnabled) }
    val systemDark = isSystemInDarkTheme()
    val isDarkTheme = themeMode == ThemeMode.DARK || (themeMode == ThemeMode.SYSTEM && systemDark)
    // 状态栏图标色跟随主题（亮色主题用深色图标）
    LaunchedEffect(isDarkTheme) { onStatusBarChanged?.invoke(isDarkTheme) }

    LazyFilmTheme(darkTheme = isDarkTheme) {
        // 全局启用液态玻璃：折射 + 镜头畸变。出问题改 false 即回到普通占位样式。
        // 注入显影引擎桥接（androidApp 实现），GPL 引擎不进入 commonMain。
        CompositionLocalProvider(
            LocalLiquidGlassEnabled provides liquidGlass,
            LocalLiquidGlassLightTheme provides !isDarkTheme,
            LocalFilmBridge provides filmBridge
        ) {
            val nav = rememberAppNav()
            // ── 账号 + 热力图（NicoLink 独立空间，与 lazyfilm 完全隔离）──
            val context = LocalContext.current
            val accountRepo = remember { AccountRepository(context) }
            val heatmapManager = remember { HeatmapManager(context) }
            var showLogin by remember { mutableStateOf(false) }
            var accountVersion by remember { mutableStateOf(0) }
            // 相机连接成功弹窗：记录刚连接的相机，延迟 1 秒弹出（照顾从连接页返回主页的时间）
            var popupCamera by remember { mutableStateOf<SavedCamera?>(null) }
            var showCameraPopup by remember { mutableStateOf(false) }
            // 连接成功弹窗的液态玻璃记录层：主内容放进 layerBackdrop，弹窗玻璃折射真实页面
            val cameraPopupBackdrop = rememberLayerBackdrop()
            LaunchedEffect(popupCamera) {
                if (popupCamera != null) {
                    kotlinx.coroutines.delay(1000)
                    // 未注册（首次连接/受限会话）不弹成功卡片：此时用户要留在连接页完成首次注册
                    if (CameraSession.state.connected && CameraSession.state.registered) {
                        showCameraPopup = true
                    }
                }
            }
            val appScope = rememberCoroutineScope()
            var heatmapSyncJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

            // 启动静默续期；登录后延迟同步热力图（避开首帧/协议等并发）
            LaunchedEffect(Unit) {
                accountRepo.renewTokenIfNeeded()
                kotlinx.coroutines.delay(3000)
                if (accountRepo.isLoggedIn) heatmapManager.syncCloud(accountRepo)
            }

            // ── 检查更新：主页连续停留 6 秒后静默查一次，且距上次检查满 7 天才查
            // （节流见 UpdateRepository）；切离主页自动取消计时。发现新版才弹窗；
            // 设置页点「软件版本号」的手动检查不受节流限制。
            val updateRepo = remember { UpdateRepository(context) }
            var pendingUpdate by remember { mutableStateOf<AppLatestVersion?>(null) }
            LaunchedEffect(nav.current) {
                if (nav.current != Route.HOME) return@LaunchedEffect
                kotlinx.coroutines.delay(6_000)
                if (!updateRepo.dueForAutoCheck()) return@LaunchedEffect
                // 失败也记为已查：离线用户每次启动都重试没有意义，等下个周期
                updateRepo.markChecked()
                updateRepo.fetchLatest()
                    ?.takeIf { it.versionCode > updateRepo.currentVersionCode() }
                    ?.let { pendingUpdate = it }
            }
            // 用系统浏览器打开外链：更新弹窗的下载链接走外部浏览器，不留在应用内
            val openExternalUrl: (String) -> Unit = { url ->
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url)
                        )
                    )
                }.onFailure {
                    android.widget.Toast.makeText(
                        context,
                        "未找到可打开链接的应用",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // 系统返回键统一处理：非主页 pop 返回；主页 2 秒内双击退出（首次弹 toast）
            var exitToast by remember { mutableStateOf(false) }
            var lastBackAt by remember { mutableStateOf(0L) }
            BackHandler {
                // 显影页有未落盘的编辑时，交给页面先完成最终渲染+缩略图落盘再返回
                if (nav.current == Route.DEVELOP && nav.developBackInterceptor?.invoke() == true) {
                    return@BackHandler
                }
                val now = android.os.SystemClock.elapsedRealtime()
                val doublePress = now - lastBackAt < 2000L
                lastBackAt = now
                if (nav.current != Route.HOME) {
                    nav.pop()
                    // 从子页面连续返回两次、且第二次刚好回到主页 → 直接退出，
                    // 不再把「第一次主页返回」浪费在弹提示上（原来会变成主页→主页）
                    if (doublePress && nav.current == Route.HOME) {
                        onExitApp?.invoke()
                    }
                } else {
                    if (doublePress) {
                        onExitApp?.invoke()
                    } else {
                        exitToast = true
                    }
                }
            }
            LaunchedEffect(exitToast) {
                if (exitToast) {
                    kotlinx.coroutines.delay(1800)
                    exitToast = false
                }
            }
            // 自动显影每张保存缩略图后 bump，刷新三页缩略图（不等全部传输完成）
            androidx.compose.runtime.DisposableEffect(Unit) {
                CameraSession.onEditThumbSaved = { nav.bumpPhotos() }
                CameraSession.onPhotoDownloaded = {
                    heatmapManager.incrementToday()
                    // 5 秒防抖：连续拍照只同步一次
                    heatmapSyncJob?.cancel()
                    heatmapSyncJob = appScope.launch {
                        kotlinx.coroutines.delay(5000)
                        heatmapManager.syncCloud(accountRepo)
                    }
                }
                // 连接成功 / 断开结算 → 把"用过的设备"与累计时长上报到云端收藏
                // 仅在用户明确授权"相机陈列架云端同步"后才上报；未授权时完全不上传相机数据
                fun reportCamera(cam: com.filmlens.nicolink.camera.SavedCamera) {
                    if (!UiPrefs.cameraCloudSyncEnabled) return
                    val (brand, model) = splitCameraName(cam.name)
                    appScope.launch {
                        accountRepo.syncCameras(
                            listOf(
                                SavedCameraDto(
                                    id = cam.id, name = cam.name, host = cam.host,
                                    serial = cam.serial, last_connected_at = cam.lastConnectedAt,
                                    brand = brand, model = model,
                                    total_connected_ms = cam.totalConnectedMs,
                                    shutter_count = cam.shutterCount,
                                )
                            )
                        )
                    }
                }
                CameraSession.onCameraConnected = {
                    reportCamera(it)
                    popupCamera = it // 连接成功（手动 / 自动重连）→ 延迟 1 秒弹连接成功卡片
                }
                CameraSession.onCameraDisconnected = { reportCamera(it) }
                CameraSession.onShutterCountUpdated = { reportCamera(it) }
                onDispose {
                    CameraSession.onEditThumbSaved = null
                    CameraSession.onPhotoDownloaded = null
                    CameraSession.onCameraConnected = null
                    CameraSession.onCameraDisconnected = null
                    CameraSession.onShutterCountUpdated = null
                    heatmapSyncJob?.cancel()
                }
            }
            // 全局 edge-to-edge：内容画到状态栏下，各页面自行处理顶部留白
            // SharedTransitionLayout 提供共享元素转场 scope（照片列表 → 详情英雄视图）
            Box(modifier = Modifier.fillMaxSize().preferredFrameRate(FrameRateCategory.High)) {
                // 被折射层：主内容画进记录层，供连接成功弹窗的液态玻璃透出真实页面
                Box(modifier = Modifier.fillMaxSize().layerBackdrop(cameraPopupBackdrop)) {
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                        AnimatedContent(
                            targetState = nav.current,
                            transitionSpec = {
                                // 缩短且对齐时长：避免淡入淡出比共享元素 bounds 动画慢，造成「先卡一下再返回」
                                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                            },
                            label = "route"
                        ) { route ->
                            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            when (route) {
                        Route.HOME -> HomeScreen(
                            nav,
                            onExitApp = { onExitApp?.invoke() },
                            onPhotosImported = { n -> heatmapManager.incrementToday(n) },
                        )
                        Route.CAMERA -> CameraScreen(nav)
                        Route.CONNECT -> ConnectScreen(nav)
                        Route.MONITOR -> MonitorScreen(nav)
                        Route.PREVIEW -> PreviewScreen(nav)
                        Route.DEVELOP -> DevelopScreen(nav)
                        Route.SETTINGS -> SettingsScreen(
                            nav = nav,
                            themeMode = themeMode,
                            onThemeChanged = { mode ->
                                themeMode = mode
                                onThemeChanged?.invoke(mode)
                            },
                            liquidGlassEnabled = liquidGlass,
                            onLiquidGlassChanged = { enabled ->
                                liquidGlass = enabled
                                UiPrefs.liquidGlassEnabled = enabled
                            },
                            accountRepo = accountRepo,
                            heatmapManager = heatmapManager,
                            accountVersion = accountVersion,
                            onOpenUserCenter = { nav.push(Route.USER_CENTER) },
                            onNeedLogin = { showLogin = true },
                            onCheckUpdate = {
                                // 手动检查不受 7 天节流限制；成功才记时间（失败下次启动还能自动补查）
                                appScope.launch {
                                    val latest = updateRepo.fetchLatest()
                                    when {
                                        latest == null -> android.widget.Toast.makeText(
                                            context, "检查更新失败，请检查网络", android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                        latest.versionCode > updateRepo.currentVersionCode() -> {
                                            updateRepo.markChecked()
                                            pendingUpdate = latest
                                        }
                                        else -> {
                                            updateRepo.markChecked()
                                            android.widget.Toast.makeText(
                                                context, "已是最新版本", android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                }
                            },
                        )
                        Route.LICENSES -> com.filmlens.nicolink.ui.screen.LicensesScreen(nav = nav)
                        Route.DEBUG -> com.filmlens.nicolink.ui.screen.DebugScreen(nav = nav)
                        Route.USER_CENTER -> UserCenterScreen(
                            nav = nav,
                            accountRepo = accountRepo,
                            heatmapManager = heatmapManager,
                            accountVersion = accountVersion,
                            onAccountChanged = {
                                accountVersion++
                                appScope.launch { heatmapManager.syncCloud(accountRepo) }
                            },
                            onNeedLogin = { showLogin = true },
                        )
                    }
                            }
                        }
                    }
                }
                }
                // 登录 / 注册弹层（全局，可由设置页与用户中心唤起）
                if (showLogin) {
                    LoginOverlay(
                        accountRepo = accountRepo,
                        onDismiss = { showLogin = false },
                        onSuccess = {
                            showLogin = false
                            accountVersion++
                            appScope.launch { heatmapManager.syncCloud(accountRepo) }
                        },
                    )
                }
                // 双击返回退出提示
                if (exitToast) {
                    androidx.compose.material3.Snackbar(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)
                    ) { androidx.compose.material3.Text("再次返回退出应用程序") }
                }
                // ── 发现新版本弹窗（主页静默检查 / 设置页手动检查触发） ──
                pendingUpdate?.let { latest ->
                    UpdateDialog(
                        latest = latest,
                        currentVersionName = updateRepo.currentVersionName(),
                        onOpenUrl = openExternalUrl,
                        onDismiss = { pendingUpdate = null },
                    )
                }
                // ── 相机连接成功弹窗（底部滑入 / 3 秒后滑出 / 点外部关闭） ──
                if (showCameraPopup) {
                    popupCamera?.let { cam ->
                        CameraConnectedPopup(
                            cam = cam,
                            backdrop = cameraPopupBackdrop,
                            onDismiss = {
                                showCameraPopup = false
                                popupCamera = null
                            },
                        )
                    }
                }
            }
        }
    }
}
