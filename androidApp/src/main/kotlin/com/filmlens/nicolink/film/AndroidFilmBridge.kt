package com.filmlens.nicolink.film

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.filmlens.nicolink.film.FilmBridge
import com.filmlens.nicolink.film.FilmParams
import com.filmlens.nicolink.film.StockOption

/**
 * FilmBridge 的 Android 实现：把 commonMain 的契约接到 GPL 引擎封装 [FilmEngine]。
 * 在 MainActivity 创建并注入 App，GPL 代码不进入 commonMain。
 */
class AndroidFilmBridge(private val context: Context) : FilmBridge {
    override fun isReady(): Boolean = try {
        FilmEngine.buildInfo(context).isNotBlank()
    } catch (t: Throwable) {
        false
    }

    override fun films(): List<StockOption> = FilmEngine.films(context)

    override fun papers(): List<StockOption> = FilmEngine.papers(context)

    override fun render(
        photoUri: String,
        filmId: String,
        paperId: String,
        params: FilmParams,
    ): ByteArray? = try {
        FilmEngine.renderPreview(context, Uri.parse(photoUri), filmId, paperId, params)
    } catch (t: Throwable) {
        DiagLog.mark("bridge.render: FAILED $t")
        android.util.Log.e("NicoLinkFilm", "render failed", t)
        null
    }

    override fun export(
        photoUri: String,
        filmId: String,
        paperId: String,
        params: FilmParams,
        format: String,
        quality: Int,
    ): String = FilmEngine.export(context, Uri.parse(photoUri), filmId, paperId, params, format, quality)

    /**
     * 原图导出：不经过引擎、不做任何修改，把 App 目录的原始图片字节直接复制到相册 DCIM/NicoLink。
     * 文件名保留原 displayName（冲突加序号），返回显示路径。
     */
    override fun exportOriginal(photoUri: String, displayName: String): String {
        val source = java.io.File(com.filmlens.nicolink.data.PhotoRepository.appPictureDir(context), displayName)
        if (!source.exists()) throw IllegalStateException("原图不存在: $displayName")
        val ext = displayName.substringAfterLast('.', "jpg").lowercase()
        val isVideo = ext == "mov" || ext == "mp4"
        val mime = when (ext) {
            "png" -> "image/png"
            "jpeg", "jpg" -> "image/jpeg"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            "mov" -> "video/quicktime"
            "mp4" -> "video/mp4"
            else -> if (isVideo) "video/*" else "image/*"
        }
        // 视频必须写 Video.Media，否则相册「视频」里看不到（之前所有文件都写 Images.Media）
        val collection = if (mime.startsWith("video/")) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val resolver = context.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+：写公共 DCIM/NicoLink，IS_PENDING 机制
            var name = displayName
            var n = 1
            while (resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(name), null
            )?.use { it.count > 0 } == true) {
                val dot = displayName.lastIndexOf('.')
                val stem = if (dot > 0) displayName.substring(0, dot) else displayName
                val suffix = if (dot > 0) displayName.substring(dot) else ""
                name = "${stem}_${n++}$suffix"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/NicoLink")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("MediaStore insert failed")
            try {
                resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                    ?: throw IllegalStateException("openOutputStream failed")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
            "${Environment.DIRECTORY_DCIM}/NicoLink/$name"
        } else {
            // Android 9-：无 RELATIVE_PATH，退回 App 专属目录
            val outDir = java.io.File(context.getExternalFilesDir("NicoLink"), "exports").apply { mkdirs() }
            var dest = java.io.File(outDir, displayName)
            var n = 1
            while (dest.exists()) {
                val dot = displayName.lastIndexOf('.')
                val stem = if (dot > 0) displayName.substring(0, dot) else displayName
                val suffix = if (dot > 0) displayName.substring(dot) else ""
                dest = java.io.File(outDir, "${stem}_${n++}$suffix")
            }
            source.inputStream().use { it.copyTo(dest.outputStream()) }
            dest.absolutePath
        }
    }

    override fun release(photoUri: String?) {
        FilmEngine.release(photoUri)
    }

    override fun saveEditThumb(photoUri: String, displayName: String, pngBytes: ByteArray) {
        try {
            val bmp = android.graphics.BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
            val out = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out)
            bmp.recycle()
            com.filmlens.nicolink.data.PhotoRepository.saveEditThumb(context, displayName, out.toByteArray())
        } catch (t: Throwable) {
            DiagLog.mark("bridge.saveEditThumb: FAILED $t")
        }
    }

    override fun clearEditThumb(displayName: String) {
        com.filmlens.nicolink.data.PhotoRepository.clearEditThumb(context, displayName)
    }

    override fun filmThumb(filmId: String): ByteArray? = FilmThumbGenerator.get(context, filmId)

    override fun ensureFilmThumbs(films: List<StockOption>, onProgress: (Int, Int) -> Unit) {
        FilmThumbGenerator.ensure(context, films, onProgress)
    }
}
