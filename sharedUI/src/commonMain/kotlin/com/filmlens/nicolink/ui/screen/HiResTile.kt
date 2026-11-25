package com.filmlens.nicolink.ui.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.engawapg.lib.zoomable.ZoomState

private data class HiResTileData(
    val bitmap: Bitmap,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/**
 * 渐进式局部解码叠加层。
 *
 * 底层由 AsyncImage 按 1080p 快速解码出全图；本组件在放大时（scale > 1.15）
 * 用 [BitmapRegionDecoder] 只解码当前视口对应的原图区域，并按 zoomable 的
 * graphicsLayer 变换矩阵（以组件中心为 pivot，scaleX/Y=scale，translation=offset）
 * 精确对齐绘制，盖住对应低清区域，实现「看哪解哪」。
 *
 * 拖动时通过 120ms 防抖避免每帧解码；解码在 IO 线程进行。
 */
@Composable
fun HiResTile(
    uri: Uri,
    origWidth: Int,
    origHeight: Int,
    zoomState: ZoomState,
    layoutSize: Size,
    enabled: Boolean,
) {
    val context = LocalContext.current
    var tile by remember { mutableStateOf<HiResTileData?>(null) }

    // 直接在组合中读 Animatable.value（Compose State），随缩放/拖动触发重组
    val scale = zoomState.scale
    val offsetX = zoomState.offsetX
    val offsetY = zoomState.offsetY

    LaunchedEffect(scale, offsetX, offsetY, layoutSize, uri, enabled) {
        if (!enabled || scale <= 1.15f || layoutSize.width <= 0f || origWidth <= 0 || origHeight <= 0) {
            tile = null
            return@LaunchedEffect
        }
        delay(120) // 防抖：拖动停止后再解码，避免每帧 IO
        val fitScale = minOf(layoutSize.width / origWidth, layoutSize.height / origHeight)
        if (fitScale <= 0f) return@LaunchedEffect
        val fitW = origWidth * fitScale
        val fitH = origHeight * fitScale
        val contentLeft = (layoutSize.width - fitW) / 2f
        val contentTop = (layoutSize.height - fitH) / 2f
        val cx = layoutSize.width / 2f
        val cy = layoutSize.height / 2f

        // 屏幕坐标 → 原图坐标（逆 graphicsLayer 变换 + 逆 Fit 缩放）
        fun screenToOrig(sx: Float, sy: Float): Pair<Float, Float> {
            val fcx = (sx - cx - offsetX) / scale + cx
            val fcy = (sy - cy - offsetY) / scale + cy
            return (fcx - contentLeft) / fitScale to (fcy - contentTop) / fitScale
        }

        val (l, t) = screenToOrig(0f, 0f)
        val (r, b) = screenToOrig(layoutSize.width, layoutSize.height)
        val left = l.coerceIn(0f, origWidth.toFloat()).toInt()
        val top = t.coerceIn(0f, origHeight.toFloat()).toInt()
        val right = r.coerceIn((left + 1).toFloat(), origWidth.toFloat()).toInt()
        val bottom = b.coerceIn((top + 1).toFloat(), origHeight.toFloat()).toInt()
        if (right <= left || bottom <= top) return@LaunchedEffect

        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val decoder = BitmapRegionDecoder.newInstance(input, false)
                    decoder?.decodeRegion(
                        Rect(left, top, right, bottom),
                        BitmapFactory.Options().apply { inSampleSize = 1 },
                    )?.also { decoder.recycle() }
                }
            }.getOrNull()
        }
        if (decoded != null) tile = HiResTileData(decoded, left, top, right, bottom)
    }

    DisposableEffect(Unit) {
        onDispose { tile?.bitmap?.recycle() }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = tile ?: return@Canvas
        val fitScale = minOf(size.width / origWidth, size.height / origHeight)
        val fitW = origWidth * fitScale
        val fitH = origHeight * fitScale
        val contentLeft = (size.width - fitW) / 2f
        val contentTop = (size.height - fitH) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f

        // 原图矩形 → Fit 后内容坐标 → 屏幕坐标（graphicsLayer 以中心为 pivot）
        val fcLeft = contentLeft + t.left * fitScale
        val fcTop = contentTop + t.top * fitScale
        val sx = (fcLeft - cx) * scale + cx + offsetX
        val sy = (fcTop - cy) * scale + cy + offsetY
        val sw = (t.right - t.left) * fitScale * scale
        val sh = (t.bottom - t.top) * fitScale * scale

        drawImage(
            image = t.bitmap.asImageBitmap(),
            dstOffset = IntOffset(sx.toInt(), sy.toInt()),
            dstSize = IntSize(sw.toInt().coerceAtLeast(1), sh.toInt().coerceAtLeast(1)),
        )

    }
}
