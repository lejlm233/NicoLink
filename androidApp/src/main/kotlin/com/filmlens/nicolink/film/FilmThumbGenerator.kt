package com.filmlens.nicolink.film

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * 胶片卡片「真实效果缩略图」生成器。
 *
 * 用自带样片 [SAMPLE_ASSET]（lazyfilm 的 sample_photo.jpg 航拍原图）经 SpektraLab 引擎
 * 按每款胶片各渲染一张预览，落盘到 filesDir/filmThumbs/{filmId}.jpg。
 * 首次进入显影页后台逐张生成（幂等，已存在则跳过），之后卡片直接读缓存，零运行时开销。
 *
 * 引擎 renderPreview 是 synchronized 单渲染器，这里后台串行生成，不与用户显影竞争会话
 * （样片用独立 file:// key，各自持有 session）。单张约 0.5-1s，25 张首屏分批出现属预期。
 */
object FilmThumbGenerator {
    private const val TAG = "NicoLinkFilmThumb"
    private const val SAMPLE_ASSET = "sample_photo.jpg"
    private const val THUMB_DIR = "filmThumbs"
    private const val PREVIEW_EDGE = 800 // 引擎下限

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running = false

    /** 缩略图缓存目录 */
    private fun thumbDir(context: Context) = File(context.filesDir, THUMB_DIR).apply { mkdirs() }

    private fun cachedFile(context: Context, filmId: String) = File(thumbDir(context), "$filmId.jpg")

    /** 同步读已缓存的缩略图字节；未生成返回 null */
    fun get(context: Context, filmId: String): ByteArray? = try {
        val f = cachedFile(context, filmId)
        if (f.exists() && f.length() > 0) f.readBytes() else null
    } catch (t: Throwable) {
        null
    }

    /**
     * 后台生成全部胶片缩略图（幂等）。样片只从 assets 解压一次到 filesDir。
     * @param films 胶片清单（id 用于渲染 + 命名）
     * @param onProgress 每生成一张回调 (已完成数, 总数)
     */
    fun ensure(context: Context, films: List<StockOption>, onProgress: (Int, Int) -> Unit) {
        if (running || films.isEmpty()) return
        running = true
        scope.launch {
            try {
                // 样片：assets → filesDir（引擎 openInput 对 file:// 直接走 File，避免 content:// EACCES）
                val sampleFile = File(context.filesDir, SAMPLE_ASSET)
                if (!sampleFile.exists()) {
                    context.assets.open(SAMPLE_ASSET).use { input ->
                        sampleFile.outputStream().use { out -> input.copyTo(out) }
                    }
                }
                val sampleUri = Uri.fromFile(sampleFile).toString()
                val pending = films.filter { !cachedFile(context, it.id).exists() }
                var done = 0
                for (film in pending) {
                    val bytes = try {
                        FilmEngine.renderPreview(
                            context,
                            Uri.parse(sampleUri),
                            film.id,
                            PAPER_NONE, // 正片自动 scan_film=true；负片引擎 fallback 默认相纸
                            FilmParams(previewLongEdge = PREVIEW_EDGE),
                        )
                    } catch (t: Throwable) {
                        android.util.Log.e(TAG, "render ${film.id} failed: ${t.message}")
                        null
                    }
                    if (bytes != null && bytes.isNotEmpty()) {
                        cachedFile(context, film.id).writeBytes(bytes)
                    }
                    done++
                    onProgress(done, pending.size)
                }
                // 渲染产生的样片 session 不再需要，释放避免常驻内存
                if (pending.isNotEmpty()) FilmEngine.release(sampleUri)
                android.util.Log.i(TAG, "film thumbs ready: ${pending.size} generated")
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "ensure failed: ${t.message}")
            } finally {
                running = false
            }
        }
    }
}
