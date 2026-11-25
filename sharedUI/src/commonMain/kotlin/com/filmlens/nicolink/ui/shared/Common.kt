package com.filmlens.nicolink.ui.shared

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.filmlens.nicolink.data.PhotoItem
import com.filmlens.nicolink.ui.LocalAnimatedVisibilityScope
import com.filmlens.nicolink.ui.LocalSharedTransitionScope
import com.filmlens.nicolink.ui.shared.liquid.LiquidPanel
import com.filmlens.nicolink.ui.theme.AppIcons
import com.filmlens.nicolink.ui.theme.LocalLazyFilmColors
import com.kyant.backdrop.Backdrop

/** 统一的顶部标题栏：左侧返回（可选）、标题、右侧操作（可选） */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = LocalLazyFilmColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bgPrimary)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgElevated)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.ArrowLeft,
                    contentDescription = "返回",
                    tint = colors.iconPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** 分组标题 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val colors = LocalLazyFilmColors.current
    Text(
        text = text,
        color = colors.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** 通用占位卡片：图标 + 标题 + 副标题，用于连接相机 / 添加相机等入口 */
@Composable
fun PlaceholderCard(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLazyFilmColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.bgCard)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.brandDark2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.brandLight1, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = colors.textTertiary, fontSize = 12.sp)
            }
        }
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(20.dp))
    }
}

/** 页面中央的占位提示（M0 空壳） */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    val colors = LocalLazyFilmColors.current
    Box(modifier = modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = colors.textTertiary, fontSize = 14.sp)
    }
}

/** 普通照片格（非玻璃）：lazyfilm 里照片流是普通卡片，不折射。M1：接真实照片 + Coil 缩略图 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PhotoTile(
    photo: PhotoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbVersion: Int = 0,
    onLongClick: (() -> Unit)? = null,
    selectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggle: (() -> Unit)? = null,
) {
    val colors = LocalLazyFilmColors.current
    // 英雄视图：列表缩略图与详情页大图用同一个 key（photo.uri）做共享元素转场
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "photo_${photo.uri}"),
                animatedVisibilityScope = animatedVisibilityScope,
                // sharedBounds：容器做 bounds 动画，两端图片各自按自身 ContentScale 绘制并交叉淡入淡出，
                // 避免 sharedElement 在 Crop↔Fit 之间跳变造成的「先变全屏再缩回」顿挫
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(180)),
                boundsTransform = { _, _ ->
                    tween<androidx.compose.ui.geometry.Rect>(280, easing = FastOutSlowInEasing)
                },
            )
        }
    } else Modifier
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bgCard)
            .combinedClickable(
                onClick = { if (selectMode) onToggle?.invoke() else onClick() },
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.BottomStart
    ) {
        // 底层占位：Coil 加载中/失败时露出，成功时图片不透明覆盖
        Box(
            modifier = Modifier.fillMaxWidth().height(110.dp).background(colors.bgElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (photo.isVideo) AppIcons.Video else AppIcons.PhotoCamera,
                contentDescription = null,
                tint = colors.textDisabled,
                modifier = Modifier.size(28.dp)
            )
        }
        // 实时查缩略图文件：只要文件存在就显示编辑图，不依赖 PhotoItem 快照；
        // thumbVersion 由外部（nav.photosVersion）驱动重组，自动显影保存后 bump 即可刷新。
        val context = androidx.compose.ui.platform.LocalContext.current
        val videoThumb = remember(photo.displayName, thumbVersion) {
            if (photo.isVideo) com.filmlens.nicolink.data.PhotoRepository.videoThumbnail(context, photo.displayName) else null
        }
        if (videoThumb != null) {
            Image(
                bitmap = videoThumb,
                contentDescription = photo.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(110.dp).then(sharedModifier),
            )
        } else {
            val liveThumb = remember(photo.displayName, thumbVersion) {
                com.filmlens.nicolink.data.PhotoRepository.editThumbFile(context, photo.displayName)
                    .let { if (it.exists()) android.net.Uri.fromFile(it) else null }
            }
            // 限制解码尺寸：网格只需 110dp 高，按 320px 解码避免大图逐张全尺寸解码造成滚动卡顿
            val request = remember(photo.displayName, liveThumb, thumbVersion) {
                ImageRequest.Builder(context)
                    .data(liveThumb ?: photo.editedThumb ?: photo.uri)
                    .size(320, 320)
                    // 缩略图文件路径恒定，Coil 默认按 Uri 命中内存缓存 → 改完参数返回仍是旧图。
                    // 用版本号做 cache key，bumpPhotos 后强制重新解码。
                    .memoryCacheKey("edit_${photo.displayName}_v$thumbVersion")
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = photo.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(110.dp).then(sharedModifier)
            )
        }
        // 批量选择态：蓝色遮罩 + 右上勾选圈（对齐 lazyfilm）
        if (selectMode) {
            if (isSelected) {
                Box(
                    Modifier.fillMaxWidth().height(110.dp)
                        .background(Color(0xFF2196F3).copy(alpha = 0.25f))
                )
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
            photo.displayName.endsWith(".mov", true) -> "MOV" to Color(0xFF4FC3F7)
            photo.displayName.endsWith(".mp4", true) -> "MP4" to Color.White
            photo.isRaw -> "RAW" to Color(0xFFE6A817)
            else -> "JPG" to Color.White
        }
        // 与左下已下载图标同一高度：同样 18dp 高 + 6dp 内缩的框
        Box(
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).height(18.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(badgeText, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 玻璃弹出面板（lazyfilm 的「弹出面板」用法）：LiquidPanel 承载浮层内容 */
@Composable
fun GlassPanel(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit
) {
    LiquidPanel(backdrop = backdrop, modifier = modifier, shape = shape, content = content)
}
