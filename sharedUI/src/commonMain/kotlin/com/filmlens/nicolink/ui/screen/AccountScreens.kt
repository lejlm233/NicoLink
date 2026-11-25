package com.filmlens.nicolink.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.filmlens.nicolink.ui.theme.LocalHapticEnabled
import com.filmlens.nicolink.ui.theme.hapticClick
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import coil3.compose.AsyncImage
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import com.filmlens.nicolink.camera.CameraSession
import com.filmlens.nicolink.camera.CameraStore
import com.filmlens.nicolink.camera.SavedCamera
import com.filmlens.nicolink.camera.formatConnectedDuration
import com.filmlens.nicolink.camera.splitCameraName
import com.filmlens.nicolink.data.cloud.AccountRepository
import com.filmlens.nicolink.data.cloud.EmailScene
import com.filmlens.nicolink.data.cloud.HeatmapManager
import com.filmlens.nicolink.data.cloud.SavedCameraDto
import com.filmlens.nicolink.ui.AppNav
import com.filmlens.nicolink.ui.Route
import com.filmlens.nicolink.ui.UiPrefs
import com.filmlens.nicolink.ui.shared.GlassPage
import com.filmlens.nicolink.ui.shared.GlassTopBar
import com.filmlens.nicolink.ui.shared.SectionTitle
import com.filmlens.nicolink.ui.theme.AppIcons
import com.filmlens.nicolink.ui.theme.LazyFilmColors
import com.filmlens.nicolink.ui.theme.LocalLazyFilmColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================
// 账号 UI —— 设置页账户面板（头像 + 热力图）、登录/注册弹层、用户中心、相机陈列架
// 无 VIP / 支付 / 邀请；登录方式仅邮箱注册 + 账号密码 + 邮箱绑定/改密/找回。
// ============================================================

/** 头像资源解析：云端存 a01~a20 编号或 http 直链，转 coil 可加载的 model */
private fun avatarModel(ref: String): String = when {
    ref.isBlank() -> ""
    ref.startsWith("http") -> ref
    else -> "file:///android_asset/avatars/$ref.png"
}

/** 头像组件：有 avatar 显示图片，否则显示昵称首字占位 */
@Composable
private fun AvatarImage(avatarRef: String, nickname: String, size: androidx.compose.ui.unit.Dp) {
    val colors = LocalLazyFilmColors.current
    val model = avatarModel(avatarRef)
    Box(
        modifier = Modifier.size(size).clip(CircleShape)
            .background(colors.brandPrimary.copy(alpha = 0.25f))
            .border(1.dp, colors.brandLight1.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (model.isNotBlank()) {
            AsyncImage(
                model = model,
                contentDescription = "头像",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Text(
                text = if (nickname.isNotBlank()) nickname.take(1).uppercase() else "◌",
                color = colors.brandLight1,
                fontSize = (size.value * 0.45f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * 自绘输入框：用 BasicTextField 替代 material3 TextField。
 * 原因：项目 composeMultiplatform 1.11.1 的 foundation 引入 CustomStyle API，
 * 而 material3 1.11.0-alpha07 的 TextField 内部与之不兼容，渲染即抛 AbstractMethodError 崩溃。
 * BasicTextField 在本项目其它页面已验证可用。
 */
@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    emailKeyboard: Boolean = false,
) {
    val colors = LocalLazyFilmColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.bgSurface)
            .border(1.dp, colors.bgDivider, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = colors.textTertiary, fontSize = 13.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = colors.textPrimary, fontSize = 14.sp),
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = if (emailKeyboard) KeyboardOptions(keyboardType = KeyboardType.Email) else KeyboardOptions.Default,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.brandPrimary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 设置页账户面板：头像行 + 39×7 热力图（纯本地即可显示） */
@Composable
fun AccountPanelSection(
    loggedIn: Boolean,
    nickname: String,
    avatar: String = "",
    heatmapGrid: List<List<Int>>,
    onOpenUserCenter: () -> Unit,
    onNeedLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLazyFilmColors.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp)).background(colors.bgSurface)
            .clickable(onClick = if (loggedIn) onOpenUserCenter else onNeedLogin)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarImage(if (loggedIn) avatar else "", nickname, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (loggedIn) nickname.ifBlank { "NicoLink 用户" } else "登录 / 注册",
                    color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (loggedIn) "进入用户中心" else "使用邮箱注册，同步拍照热力图",
                    color = colors.textTertiary, fontSize = 11.sp
                )
            }
            Text("›", color = colors.brandLight1, fontSize = 22.sp)
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = colors.bgDivider)
        // ── 热力图：图例 + 网格（完全复刻 lazyfilm）──
        HeatmapGrid(grid = heatmapGrid)
    }
}

/** GitHub 贡献图式 39×7 网格：月份横轴 + 一三五竖直轴 + 默认滚到最新（完全复刻 lazyfilm） */
@Composable
fun HeatmapGrid(grid: List<List<Int>>, modifier: Modifier = Modifier) {
    val colors = LocalLazyFilmColors.current
    // ── 参数（与 lazyfilm 完全一致）──
    val cellSize = 10.dp
    val gap = 2.dp
    val totalCell = cellSize + gap
    val dayLabelWidth = 28.dp
    val totalWeeks = grid.size.coerceAtLeast(1)

    // 颜色映射与 lazyfilm 完全一致（level 3/4 同色为原版行为）
    val heatmapColors = listOf(
        colors.bgCard,          // inactive
        colors.brandDark2,
        colors.brandPrimary,
        colors.brandLight1,
        colors.brandLight1,     // most active
    )

    val monthNames = listOf("1月","2月","3月","4月","5月","6月","7月","8月","9月","10月","11月","12月")
    // 从当前月份往过去推 9 个月（3个季度），当前月在最右侧
    val currentMonth = remember { java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) }
    val monthSpans = remember {
        val totalMonths = 9
        val totalWeeks = 39
        val base = totalWeeks / totalMonths   // 4
        val extra = totalWeeks % totalMonths  // 2
        List(totalMonths) { i ->
            val monthIndex = (currentMonth - totalMonths + 1 + i + 12) % 12
            monthIndex to (base + if (i < extra) 1 else 0)
        }
    }

    val dayLabels = listOf("", "一", "", "三", "", "五", "")
    val dayLabelRows = setOf(1, 3, 5)

    Column(modifier = modifier.padding(top = 12.dp)) {
        // ── 图例：非活跃 + 5 色块 + 活跃（右对齐，与 lazyfilm 一致）──
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("非活跃", fontSize = 10.sp, color = colors.textDisabled)
                Box(
                    Modifier.size(8.dp)
                        .background(heatmapColors[0], RoundedCornerShape(1.dp))
                        .border(0.5.dp, colors.bgControl, RoundedCornerShape(1.dp))
                )
                heatmapColors.drop(1).forEach { c ->
                    Box(Modifier.size(8.dp).background(c, RoundedCornerShape(1.dp)))
                }
                Text("活跃", fontSize = 10.sp, color = colors.textDisabled)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Grid — 默认滚动到最右侧（当前月份）
        val heatmapScrollState = rememberScrollState()
        LaunchedEffect(Unit) {
            delay(100)
            if (heatmapScrollState.maxValue > 0) {
                heatmapScrollState.scrollTo(heatmapScrollState.maxValue)
            }
        }

        val textMeasurer = rememberTextMeasurer()
        val labelStyle = TextStyle(fontSize = 10.sp, color = colors.textDisabled)
        val gridHeight = 20.dp + totalCell * 7f

        Box(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .horizontalScroll(heatmapScrollState),
        ) {
            Canvas(
                modifier = Modifier
                    .width(dayLabelWidth + totalCell * totalWeeks + 4.dp)
                    .height(gridHeight),
            ) {
                val cellPx = cellSize.toPx()
                val gapPx = gap.toPx()
                val tCellPx = cellPx + gapPx
                val labelPx = dayLabelWidth.toPx()
                val monthRowPx = 20.dp.toPx()

                // 月份标签（横轴）
                var weekOff = 0f
                for ((monthIdx, span) in monthSpans) {
                    val spanW = span * tCellPx
                    val layout = textMeasurer.measure(
                        AnnotatedString(monthNames[monthIdx]),
                        style = labelStyle,
                    )
                    val tx = labelPx + weekOff + (spanW - layout.size.width) / 2f
                    drawText(layout, topLeft = Offset(tx, 0f))
                    weekOff += spanW
                }

                // 星期标签（一三五，竖直轴）
                for (d in 0 until 7) {
                    if (d in dayLabelRows) {
                        val layout = textMeasurer.measure(
                            AnnotatedString(dayLabels[d]),
                            style = labelStyle,
                        )
                        val ty = monthRowPx + d * tCellPx + (tCellPx - layout.size.height) / 2f
                        drawText(layout, topLeft = Offset(0f, ty))
                    }
                }

                // Cells
                val corner = CornerRadius(1.dp.toPx())
                for (w in 0 until totalWeeks) {
                    for (d in 0 until 7) {
                        val level = grid.getOrNull(w)?.getOrNull(d) ?: 0
                        val color = heatmapColors[level.coerceIn(0, 4)]
                        val cx = labelPx + w * tCellPx
                        val cy = monthRowPx + d * tCellPx
                        if (level == 0) {
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(cx, cy),
                                size = Size(cellPx, cellPx),
                                cornerRadius = corner,
                                style = Stroke(width = 0.5.dp.toPx()),
                            )
                        } else {
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(cx, cy),
                                size = Size(cellPx, cellPx),
                                cornerRadius = corner,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 登录 / 注册 / 找回 弹层 ──

private enum class AuthTab { LOGIN, REGISTER, RESET }

/**
 * 全屏登录弹层（由 App 根 overlay 挂载）。
 * tab：登录 / 注册 / 忘记密码；全部走邮箱验证码体系，无支付宝。
 */
@Composable
fun LoginOverlay(
    accountRepo: AccountRepository,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit,
) {
    val colors = LocalLazyFilmColors.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(AuthTab.LOGIN) }
    var account by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(20.dp)).background(colors.bgElevated)
                .clickable { /* 消费点击，避免点卡片内关闭弹层 */ }
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NicoLink 账号", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("✕", color = colors.textSecondary, fontSize = 16.sp,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(6.dp))
            }
            Spacer(Modifier.height(14.dp))
            // Tab
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.bgSurface)) {
                val tabs = listOf(AuthTab.LOGIN to "登录", AuthTab.REGISTER to "注册", AuthTab.RESET to "忘记密码")
                tabs.forEach { (t, label) ->
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (tab == t) colors.brandPrimary else Color.Transparent)
                            .clickable { tab = t; message = "" }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (tab == t) colors.textInverse else colors.textSecondary, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            when (tab) {
                AuthTab.LOGIN -> {
                    AppTextField(account, { account = it }, "邮箱或用户名", Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    AppTextField(password, { password = it }, "密码", Modifier.fillMaxWidth(), password = true)
                }
                AuthTab.REGISTER -> {
                    AppTextField(email, { email = it }, "邮箱", Modifier.fillMaxWidth(), emailKeyboard = true)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppTextField(code, { code = it }, "验证码", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.brandPrimary)
                                .clickable(enabled = !busy && countdown == 0 && email.contains("@")) {
                                    scope.launch {
                                        busy = true; message = ""
                                        runCatching { accountRepo.sendEmailCode(email, EmailScene.REGISTER) }
                                            .onSuccess { countdown = 60 }
                                            .onFailure { message = it.message ?: "发送失败" }
                                        busy = false
                                    }
                                }.padding(horizontal = 12.dp, vertical = 14.dp)
                        ) {
                            Text(if (countdown > 0) "${countdown}s" else "发送",
                                color = colors.textInverse, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    AppTextField(password, { password = it }, "密码（8-64 位，含字母和数字）", Modifier.fillMaxWidth(), password = true)
                    Spacer(Modifier.height(10.dp))
                    AppTextField(confirm, { confirm = it }, "确认密码", Modifier.fillMaxWidth(), password = true)
                }
                AuthTab.RESET -> {
                    AppTextField(email, { email = it }, "邮箱", Modifier.fillMaxWidth(), emailKeyboard = true)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppTextField(code, { code = it }, "验证码", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.brandPrimary)
                                .clickable(enabled = !busy && countdown == 0 && email.contains("@")) {
                                    scope.launch {
                                        busy = true; message = ""
                                        runCatching { accountRepo.sendEmailCode(email, EmailScene.RESET_PASSWORD) }
                                            .onSuccess { countdown = 60 }
                                            .onFailure { message = it.message ?: "发送失败" }
                                        busy = false
                                    }
                                }.padding(horizontal = 12.dp, vertical = 14.dp)
                        ) {
                            Text(if (countdown > 0) "${countdown}s" else "发送",
                                color = colors.textInverse, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    AppTextField(password, { password = it }, "新密码", Modifier.fillMaxWidth(), password = true)
                }
            }

            if (message.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(message, color = colors.brandDanger, fontSize = 12.sp)
            }
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.brandPrimary)
                    .clickable(enabled = !busy) {
                        scope.launch {
                            busy = true; message = ""
                            val result = runCatching {
                                when (tab) {
                                    AuthTab.LOGIN -> accountRepo.login(account.trim(), password)
                                    AuthTab.REGISTER -> {
                                        if (password != confirm) throw IllegalStateException("两次密码不一致")
                                        accountRepo.register(email.trim(), code.trim(), password)
                                    }
                                    AuthTab.RESET -> {
                                        accountRepo.resetPassword(email.trim(), code.trim(), password)
                                        "密码已重设，请返回登录"
                                    }
                                }
                            }
                            busy = false
                            result.onSuccess { name ->
                                if (tab == AuthTab.RESET) {
                                    message = name; tab = AuthTab.LOGIN
                                } else {
                                    onSuccess(name)
                                }
                            }.onFailure { message = it.message ?: "操作失败，请重试" }
                        }
                    }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (busy) "请稍候…" else when (tab) {
                    AuthTab.LOGIN -> "登录"
                    AuthTab.REGISTER -> "注册并登录"
                    AuthTab.RESET -> "重设密码"
                }, color = colors.textInverse, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── 用户中心 ──

@Composable
fun UserCenterScreen(
    nav: AppNav,
    accountRepo: AccountRepository,
    heatmapManager: HeatmapManager,
    accountVersion: Int,
    onAccountChanged: () -> Unit,
    onNeedLogin: () -> Unit,
) {
    val colors = LocalLazyFilmColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loggedIn = remember(accountVersion) { accountRepo.isLoggedIn }
    val nickname = remember(accountVersion) { accountRepo.nickname }
    val avatar = remember(accountVersion) { accountRepo.avatar }
    val email = remember(accountVersion) { accountRepo.email }
    val hasPassword = remember(accountVersion) { accountRepo.hasPassword }

    // 绑定邮箱 / 改密 弹窗状态
    var showBindEmail by remember { mutableStateOf(false) }
    var showSetPassword by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    GlassPage(
        topBar = { backdrop -> GlassTopBar(title = "用户中心", backdrop = backdrop, onBack = { nav.pop() }) },
        content = {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.statusBarsPadding().height(64.dp))

                // 头部：头像 + 昵称 + 邮箱
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(12.dp)).background(colors.bgSurface)
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarImage(if (loggedIn) avatar else "", nickname, 56.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(if (loggedIn) nickname.ifBlank { "NicoLink 用户" } else "未登录",
                                color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(if (email.isNotBlank()) email else "绑定邮箱后可跨设备登录",
                                color = colors.textTertiary, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                if (!loggedIn) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(12.dp)).background(colors.brandPrimary)
                            .clickable(onClick = onNeedLogin).padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("登录 / 注册", color = colors.textInverse, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // 相机陈列架（云端收藏：记录用户用过的设备）——放在账号区前
                CameraShelfSection(nav = nav, accountRepo = accountRepo, accountVersion = accountVersion)
                Spacer(Modifier.height(8.dp))

                // 账号区（绑定邮箱/注销等常用功能，紧随头部便于快速操作）
                SectionTitle("账号")
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(12.dp)).background(colors.bgSurface)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    if (loggedIn) {
                        AccountRow("绑定邮箱", if (email.isNotBlank()) email else "未绑定") { showBindEmail = true }
                        HorizontalDivider(color = colors.bgDivider)
                        AccountRow("登录密码", if (hasPassword) "已设置 · 点击修改" else "未设置 · 点击设置") { showSetPassword = true }
                        HorizontalDivider(color = colors.bgDivider)
                        AccountRow("退出登录", "") {
                            scope.launch { accountRepo.logout(); onAccountChanged() }
                        }
                        HorizontalDivider(color = colors.bgDivider)
                        AccountRow("注销账号", "30 天恢复期内可撤销", danger = true) { showDeleteConfirm = true }
                    } else {
                        AccountRow("登录 / 注册", "邮箱验证码注册") { onNeedLogin() }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    )

    if (showBindEmail) {
        BindEmailOverlay(
            accountRepo = accountRepo,
            onDismiss = { showBindEmail = false },
            onSuccess = { showBindEmail = false; onAccountChanged() }
        )
    }
    if (showSetPassword) {
        SetPasswordOverlay(
            accountRepo = accountRepo,
            hasPassword = hasPassword,
            onDismiss = { showSetPassword = false },
            onSuccess = { showSetPassword = false; onAccountChanged() }
        )
    }
    if (showDeleteConfirm) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.55f))
                .clickable(onClick = { showDeleteConfirm = false }),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(16.dp)).background(colors.bgElevated).padding(20.dp)
                    .clickable { }
            ) {
                Text("注销账号？", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("账号进入 30 天恢复期，期间重新登录可撤销。到期后资料与热力数据将无法恢复。",
                    color = colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))
                Row {
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(colors.bgSurface)
                        .clickable { showDeleteConfirm = false }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center) {
                        Text("取消", color = colors.textSecondary, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(colors.brandDanger)
                        .clickable {
                            showDeleteConfirm = false
                            scope.launch { runCatching { accountRepo.deleteAccount() }; onAccountChanged() }
                        }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("确认注销", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    val colors = LocalLazyFilmColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (danger) colors.brandDanger else colors.textPrimary, fontSize = 14.sp)
            if (subtitle.isNotBlank()) Text(subtitle, color = colors.textTertiary, fontSize = 11.sp)
        }
        Text("›", color = colors.brandLight1, fontSize = 20.sp)
    }
}

@Composable
private fun BindEmailOverlay(
    accountRepo: AccountRepository,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val colors = LocalLazyFilmColors.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }
    LaunchedEffect(countdown) { if (countdown > 0) { delay(1000); countdown-- } }

    SimpleOverlay(title = "绑定邮箱", onDismiss = onDismiss) {
        AppTextField(email, { email = it }, "邮箱", Modifier.fillMaxWidth(), emailKeyboard = true)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppTextField(code, { code = it }, "验证码", Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(colors.brandPrimary)
                .clickable(enabled = !busy && countdown == 0 && email.contains("@")) {
                    scope.launch {
                        busy = true; message = ""
                        runCatching { accountRepo.sendEmailCode(email, EmailScene.BIND_EMAIL) }
                            .onSuccess { countdown = 60 }
                            .onFailure { message = it.message ?: "发送失败" }
                        busy = false
                    }
                }.padding(horizontal = 12.dp, vertical = 14.dp)) {
                Text(if (countdown > 0) "${countdown}s" else "发送", color = colors.textInverse, fontSize = 12.sp)
            }
        }
        if (message.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(message, color = colors.brandDanger, fontSize = 12.sp) }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = if (busy) "请稍候…" else "确认绑定", enabled = !busy) {
            scope.launch {
                busy = true; message = ""
                runCatching { accountRepo.bindEmail(email, code) }
                    .onSuccess { onSuccess() }
                    .onFailure { message = it.message ?: "绑定失败" }
                busy = false
            }
        }
    }
}

@Composable
private fun SetPasswordOverlay(
    accountRepo: AccountRepository,
    hasPassword: Boolean,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val colors = LocalLazyFilmColors.current
    val scope = rememberCoroutineScope()
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    SimpleOverlay(title = if (hasPassword) "修改密码" else "设置密码", onDismiss = onDismiss) {
        if (hasPassword) {
            AppTextField(oldPwd, { oldPwd = it }, "当前密码", Modifier.fillMaxWidth(), password = true)
            Spacer(Modifier.height(10.dp))
        }
        AppTextField(newPwd, { newPwd = it }, "新密码（8-64 位，含字母和数字）", Modifier.fillMaxWidth(), password = true)
        if (message.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(message, color = colors.brandDanger, fontSize = 12.sp) }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = if (busy) "请稍候…" else "确认", enabled = !busy) {
            scope.launch {
                busy = true; message = ""
                runCatching { accountRepo.setPassword(oldPwd.takeIf { it.isNotBlank() }, newPwd) }
                    .onSuccess { onSuccess() }
                    .onFailure { message = it.message ?: "设置失败" }
                busy = false
            }
        }
    }
}

@Composable
private fun SimpleOverlay(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val colors = LocalLazyFilmColors.current
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)
            .clip(RoundedCornerShape(16.dp)).background(colors.bgElevated).clickable { }
            .padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("✕", color = colors.textSecondary, fontSize = 16.sp,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(6.dp))
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalLazyFilmColors.current
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .background(if (enabled) colors.brandPrimary else colors.bgControl)
        .clickable(enabled = enabled, onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center) {
        Text(text, color = if (enabled) colors.textInverse else colors.textDisabled,
            fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// ── 相机陈列架（替代胶卷抽屉） ──

/** 机型大图：assets/camera_shelf（独立于旧的 camera_models 缩略图），未知机型回退相机图标 */
@Composable
private fun SavedCameraImage(
    modelHint: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
) {
    val context = LocalContext.current
    val colors = LocalLazyFilmColors.current
    val bitmap = remember(modelHint) {
        val key = modelHint.lowercase().replace("nikon", "").replace(" ", "").replace("-", "").trim()
        val assets = listOf("Z30", "Z6II", "Z7II", "Z8", "Z9", "Zf", "Zfc", "Z50II", "Z5II", "Z5IIC", "Z6III")
        val match = assets.firstOrNull { it.lowercase() == key }
        if (match != null) {
            try {
                context.assets.open("camera_shelf/$match.png").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            } catch (_: Exception) {
                null
            }
        } else null
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = modelHint, contentScale = contentScale, alignment = alignment, modifier = modifier)
    } else {
        androidx.compose.material3.Icon(AppIcons.CameraIris, contentDescription = modelHint, tint = colors.brandLight1, modifier = modifier)
    }
}

/** 相机收藏本地缓存（仅作离线展示，登录后以云端为准） */
private object CameraFavouriteCache {
    private const val PREFS = "nicolink_camera_favourites"
    private const val KEY = "items"
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val serializer = kotlinx.serialization.builtins.ListSerializer(SavedCameraDto.serializer())

    fun load(context: android.content.Context): List<SavedCameraDto> {
        val raw = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    fun save(context: android.content.Context, items: List<SavedCameraDto>) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(KEY, json.encodeToString(serializer, items)).apply()
    }
}

/**
 * 相机陈列架（云端收藏）：记录用户用过哪些设备，收藏性质。
 * - 登录：拉云端全量显示（本机相机的上报交给连接 / 断开事件，避免移除后被本机记录带回来）
 * - 未登录：显示本机已连接相机 + 本地缓存
 * - 长按相机 → 二次确认 → 移除收藏（仅云端记录，不动本机连接配置）
 * - 一行一个，左机型大图、右品牌/型号/连接统计
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraShelfSection(nav: AppNav, accountRepo: AccountRepository, accountVersion: Int) {
    val colors = LocalLazyFilmColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loggedIn = remember(accountVersion) { accountRepo.isLoggedIn }
    var enabled by remember { mutableStateOf(UiPrefs.cameraCloudSyncEnabled) }
    var cameras by remember { mutableStateOf(emptyList<SavedCameraDto>()) }
    var removeTarget by remember { mutableStateOf<SavedCameraDto?>(null) }
    var showAuthDialog by remember { mutableStateOf(false) }

    LaunchedEffect(accountVersion, loggedIn, enabled) {
        if (!enabled) {
            cameras = emptyList()
            return@LaunchedEffect
        }
        val local = CameraStore.savedCameras(context).map { it.toDto() }
        val cached = CameraFavouriteCache.load(context)
        // 身份键：序列号优先，无序列号才用 id；两台同型号/换 IP 都不会重复
        fun identity(d: SavedCameraDto) = d.serial.ifBlank { d.id }
        val merged = (local + cached).distinctBy { identity(it) }
        if (loggedIn) {
            // 启动时只拉取云端全量，不把本机已连接相机重复 upsert 回去（避免被移除的相机"复活"）。
            // 本机相机的上报由连接/断开/快门解析事件（App.kt reportCamera）负责。
            val remote = accountRepo.syncCameras(cached)
            if (remote != null) {
                cameras = remote.distinctBy { identity(it) }
                CameraFavouriteCache.save(context, cameras)
            } else {
                cameras = merged
            }
        } else {
            cameras = merged
        }
    }

    SectionTitle("相机陈列架")
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp)).background(colors.bgSurface).padding(12.dp)
    ) {
        if (!enabled) {
            Text("记录你用过的相机，登录后可将连接时长、快门次数等同步到云端。",
                color = colors.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(colors.brandPrimary).clickable { showAuthDialog = true }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("开启云端相机收藏", color = colors.textInverse, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        } else if (cameras.isEmpty()) {
            Text("暂无收藏的相机 · 连接过的相机会自动出现在这里",
                color = colors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            cameras.forEach { cam ->
                CameraTile(cam = cam, onLongPress = { removeTarget = cam })
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    // 授权弹窗：告知会上传哪些数据，用户决定
    if (showAuthDialog) {
        Dialog(onDismissRequest = { showAuthDialog = false }) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)).background(colors.bgElevated).padding(20.dp),
            ) {
                Text("开启云端相机收藏？", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("开启后，以下相机信息会上传到你的 NicoLink 云端账号（仅你可见）：",
                    color = colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "相机型号 / 品牌 / 序列号",
                    "最近连接时间与累计连接时长",
                    "从照片读取的快门次数",
                    "本机 IP 地址（用于区分设备）",
                ).forEach {
                    Text("· $it", color = colors.textTertiary, fontSize = 12.sp)
                    Spacer(Modifier.height(3.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text("这些数据仅用于在你的账号下展示相机陈列架，不会共享给其他人。未开启时不会上传任何相机数据。",
                    color = colors.textTertiary, fontSize = 11.sp)
                Spacer(Modifier.height(16.dp))
                Row {
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(colors.bgSurface)
                        .clickable { showAuthDialog = false }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center) {
                        Text("暂不开启", color = colors.textSecondary, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(colors.brandPrimary)
                        .clickable {
                            UiPrefs.cameraCloudSyncEnabled = true
                            enabled = true
                            showAuthDialog = false
                        }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("同意并开启", color = colors.textInverse, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // 移除收藏二次确认：仅云端记录、不动本机连接配置；按 serial 身份删除
    removeTarget?.let { target ->
        Dialog(onDismissRequest = { removeTarget = null }) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)).background(colors.bgElevated).padding(20.dp),
            ) {
                Text("移除相机收藏？", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("仅从收藏架移除记录，不影响相机的本机连接配置。", color = colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text(target.name.ifBlank { target.host }, color = colors.textTertiary, fontSize = 11.sp)
                Spacer(Modifier.height(18.dp))
                Row {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(colors.bgSurface)
                            .clickable { removeTarget = null }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("取消", color = colors.textSecondary, fontSize = 13.sp) }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(colors.brandDanger)
                            .clickable {
                                removeTarget = null
                                cameras = cameras.filterNot { it.serial.ifBlank { it.id } == target.serial.ifBlank { target.id } }
                                CameraFavouriteCache.save(context, cameras)
                                scope.launch {
                                    if (loggedIn) accountRepo.removeCamera(target.id, target.serial)
                                }
                            }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("移除", color = Color.White, fontSize = 13.sp) }
                }
            }
        }
    }
}

/** 本机记录转云端 DTO：陈列架展示字段（品牌/型号/累计时长/快门次数）一并带上 */
private fun SavedCamera.toDto(): SavedCameraDto {
    val (b, m) = splitCameraName(name)
    return SavedCameraDto(
        id = id, name = name, host = host, serial = serial,
        last_connected_at = lastConnectedAt,
        brand = b, model = m,
        total_connected_ms = totalConnectedMs,
        shutter_count = shutterCount,
    )
}

@Composable
private fun CameraTile(cam: SavedCameraDto, onLongPress: () -> Unit) {
    val colors = LocalLazyFilmColors.current
    // 品牌/型号以相机名（权威字段）为准重算，兼容云端旧缓存里已存错的拆分值
    val (nameBrand, nameModel) = splitCameraName(cam.name)
    val brandText = nameBrand.ifBlank { cam.brand.ifBlank { "Nikon" } }
    val modelText = nameModel.ifBlank { cam.model.ifBlank { cam.serial.ifBlank { "—" } } }
    val dateText = if (cam.last_connected_at > 0) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(cam.last_connected_at))
    } else "—"
    val durationText = formatConnectedDuration(cam.total_connected_ms)
    val shutterText = if (cam.shutter_count > 0) {
        String.format(Locale.getDefault(), "%,d", cam.shutter_count)
    } else "—"

    val shape = RoundedCornerShape(16.dp)
    val imageShape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(colors.bgCard, colors.brandPrimary.copy(alpha = 0.24f), colors.bgCard)
                )
            )
            .border(1.dp, colors.brandLight1.copy(alpha = 0.38f), shape)
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
    ) {
        // 斜向光纹 + 右上光晕 + 顶部高光：玻璃 / 霓虹质感
        Canvas(modifier = Modifier.matchParentSize()) {
            val step = 34.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(
                    color = colors.brandLight1.copy(alpha = 0.07f),
                    start = Offset(x, size.height),
                    end = Offset(x + size.height, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
                x += step
            }
        }
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.radialGradient(
                    colors = listOf(colors.brandLight1.copy(alpha = 0.32f), Color.Transparent),
                    center = Offset(1200f, 0f),
                    radius = 900f,
                )
            )
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp).background(
                Brush.linearGradient(
                    listOf(Color.Transparent, colors.brandLight1.copy(alpha = 0.75f), Color.Transparent)
                )
            )
        )
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // 透视展台 + 相机（与连接成功弹窗共用同一组件）
            CameraPlatform(
                modelHint = cam.model.ifBlank { cam.name }.ifBlank { cam.serial },
                modifier = Modifier.width(190.dp).height(116.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                ShelfInfoRow("品牌", brandText, colors)
                ShelfInfoRow("型号", modelText, colors)
                ShelfInfoRow("上次连接", dateText, colors)
                ShelfInfoRow("连接时长", durationText, colors)
                ShelfInfoRow("快门次数", shutterText, colors)
            }
        }
    }
}

@Composable
private fun ShelfInfoRow(label: String, value: String, colors: LazyFilmColors) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.textTertiary, fontSize = 10.sp, modifier = Modifier.width(56.dp))
        Text(value, color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

// ── 连接成功弹窗（全局，从底部滑入，展示台 + 相机） ──

/**
 * 透视展台 + 相机图（陈列架卡片的左半部分），供卡片与连接成功弹窗复用。
 */
@Composable
fun CameraPlatform(
    modelHint: String,
    modifier: Modifier = Modifier,
    platformHeight: androidx.compose.ui.unit.Dp = 116.dp,
) {
    val colors = LocalLazyFilmColors.current
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(platformHeight)) {
            val w = size.width
            val h = size.height
            val topInset = w * 0.10f
            val topY = h * 0.80f
            val bottomY = h * 1.0f
            val platform = Path().apply {
                moveTo(topInset, topY)
                lineTo(w - topInset, topY)
                lineTo(w, bottomY)
                lineTo(0f, bottomY)
                close()
            }
            drawPath(
                path = platform,
                brush = Brush.verticalGradient(
                    colors = listOf(colors.brandDark1, colors.brandPrimary.copy(alpha = 0.6f)),
                    startY = topY,
                    endY = bottomY,
                ),
            )
            drawPath(
                path = platform,
                color = colors.brandLight1.copy(alpha = 0.6f),
                style = Stroke(width = 1.4.dp.toPx()),
            )
        }
        SavedCameraImage(
            modelHint = modelHint,
            modifier = Modifier.fillMaxWidth().height(platformHeight).offset(y = (-8).dp),
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomCenter,
        )
    }
}

/**
 * 相机连接成功弹窗：底部滑入 / 3 秒后滑出；点外部可关闭。
 * 内容 = 液态玻璃面板 + 展示台与相机（无右侧参数）。
 */
@Composable
fun CameraConnectedPopup(
    cam: SavedCamera,
    backdrop: com.kyant.backdrop.Backdrop,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalLazyFilmColors.current
    val hapticView = LocalView.current
    val hapticEnabled = LocalHapticEnabled.current
    var visible by remember { mutableStateOf(false) }
    var typedCount by remember { mutableStateOf(0) }
    var halo by remember { mutableStateOf(false) }
    val pacifico = remember(context) { loadPacifico(context) }
    // 标题逐字显示：Pacifico 连笔字体逐字会断连笔，故用淡入的字数递增营造打字机仪式感
    val (brand, model) = splitCameraName(cam.name)
    val title = "$brand $model".trim()
    val cameraAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(700))
    val cameraScale by animateFloatAsState(if (visible) 1f else 0.82f, tween(700))
    // 光晕 0→1（一次性动画）
    val haloProgress by animateFloatAsState(if (halo) 1f else 0f, tween(1100))

    LaunchedEffect(Unit) {
        hapticClick(hapticView, hapticEnabled) // 连接成功震动反馈
        visible = true       // 触发滑入 + 相机登场
        delay(200)
        halo = true          // 光晕脉冲：聚光灯打亮
        // 逐字打字机
        for (i in title.indices) {
            delay(90)
            typedCount = i + 1
        }
        delay(2800 - title.length * 90L) // 保证总展示约 4 秒
        visible = false      // 触发滑出
        delay(350)
        onDismiss()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // 点击遮罩关闭（与滑出同源：直接 onDismiss）
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onDismiss),
        )
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxWidth(0.9f).padding(bottom = 24.dp),
            enter = slideInVertically(animationSpec = tween(350)) { it },
            exit = slideOutVertically(animationSpec = tween(300)) { it },
        ) {
            com.filmlens.nicolink.ui.shared.liquid.LiquidPanel(
                backdrop = backdrop,
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val modelHint = cam.name.ifBlank { cam.serial }
                    // 标题：主页同款 Pacifico，逐字打字机；末尾光标闪烁
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title.take(typedCount),
                            fontFamily = pacifico,
                            fontSize = 30.sp,
                            color = colors.brandLight1,
                            modifier = Modifier.graphicsLayer { scaleX = 1.15f; transformOrigin = TransformOrigin(0f, 0.5f) },
                        )
                        if (typedCount < title.length) {
                            Spacer(Modifier.width(2.dp))
                            Box(Modifier.size(width = 2.dp, height = 26.dp).background(colors.brandLight1))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    // 相机 + 展台：淡入 + 从 0.82 放大到 1；背后环形光晕扩散淡出（聚光灯亮起）
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                        if (haloProgress > 0f && haloProgress < 1f) {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val cx = size.width / 2f
                                val cy = size.height * 0.55f
                                val radius = 20.dp.toPx() + haloProgress * 100.dp.toPx()
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            colors.brandLight1.copy(alpha = (1f - haloProgress) * 0.45f),
                                            Color.Transparent,
                                        ),
                                        center = Offset(cx, cy),
                                        radius = radius,
                                    ),
                                    radius = radius,
                                    center = Offset(cx, cy),
                                )
                                drawCircle(
                                    color = colors.brandLight1.copy(alpha = (1f - haloProgress) * 0.6f),
                                    radius = radius,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 1.5.dp.toPx()),
                                )
                            }
                        }
                        Box(modifier = Modifier.matchParentSize().graphicsLayer {
                            alpha = cameraAlpha
                            scaleX = cameraScale
                            scaleY = cameraScale
                        }) {
                            CameraPlatform(
                                modelHint = modelHint,
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                platformHeight = 160.dp,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("连接成功", color = colors.brandLight1, fontSize = 13.sp, letterSpacing = 2.sp)
                }
            }
        }
    }
}
