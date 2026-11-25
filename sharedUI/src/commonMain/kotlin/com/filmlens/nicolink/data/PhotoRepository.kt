package com.filmlens.nicolink.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 本机相册的一条照片记录（M1：只读本机 MediaStore，不含相机传入的 RAW）。
 */
data class PhotoItem(
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long, // 秒
    val mimeType: String,
    val width: Int,
    val height: Int,
    /** 非破坏性编辑后的缩略图（显示用）；原图 uri 始终不变，恢复原图即清此项 */
    val editedThumb: Uri? = null,
) {
    /** 是否视频（P2 录像产物，.mov/.mp4） */
    val isVideo: Boolean
        get() {
            val n = displayName.lowercase()
            return n.endsWith(".mov") || n.endsWith(".mp4") ||
                mimeType.lowercase().startsWith("video/")
        }

    /** 是否 RAW：按扩展名或 mimeType 判断（dng/nef/arw/cr3/raf 等） */
    val isRaw: Boolean
        get() {
            val n = displayName.lowercase()
            val byName = RAW_EXTS.any { n.endsWith(it) }
            val byMime = mimeType.lowercase().let { m ->
                m.contains("dng") || m.contains("nef") || m.contains("arw") ||
                    m.contains("cr2") || m.contains("cr3") || m.contains("raf") ||
                    m.contains("raw")
            }
            return byName || byMime
        }

    companion object {
        private val RAW_EXTS = listOf(
            ".dng", ".nef", ".nrw", ".arw", ".srf", ".sr2",
            ".cr2", ".cr3", ".crw", ".raf", ".orf", ".rw2", ".pef",
        )
    }
}

/**
 * 只读访问 App 专属图片目录（Android/data/<pkg>/files/Pictures/NicoLink）。
 * 该目录属于 App 自身，无需 READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE 权限。
 * commonMain 直接调用 Android API（本项目只有 android target）。
 */
object PhotoRepository {
    /** App 专属图片目录（本机 Tab 数据源 + M2 下载落盘共用，无需存储权限） */
    fun appPictureDir(context: Context): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "NicoLink")

    /**
     * 从系统图片选择器选中的 Uri 批量导入到 App 专属目录（无需存储权限）。
     * 用 contentResolver.openInputStream 读，文件名冲突时加序号避免覆盖。
     * @return 成功导入的文件数
     */
    fun importPhotos(context: Context, uris: List<android.net.Uri>): Int {
        val dir = appPictureDir(context).apply { mkdirs() }
        var count = 0
        for (uri in uris) {
            try {
                // 从 Uri 取显示名；取不到就用时间戳兜底
                var name: String? = null
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) name = c.getString(idx)
                }
                var base = name?.takeIf { it.isNotBlank() }
                    ?: "import_${System.currentTimeMillis()}"
                // 只保留图片/视频扩展名，未知则按 jpg 处理，保证能被 loadPhotos 识别
                val ext = base.substringAfterLast('.', "").let { ".$it" }
                if (ext !in IMAGE_EXTS && ext !in VIDEO_EXTS) base = "$base.jpg"
                var target = File(dir, base)
                var n = 1
                while (target.exists()) {
                    val dot = base.lastIndexOf('.')
                    val stem = if (dot > 0) base.substring(0, dot) else base
                    val suffix = if (dot > 0) base.substring(dot) else ""
                    target = File(dir, "${stem}_${n++}$suffix")
                }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                } ?: continue
                count++
            } catch (_: Throwable) {
                // 单个失败不影响其它
            }
        }
        return count
    }

    private val IMAGE_EXTS = listOf(
        ".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".bmp",
        ".dng", ".nef", ".nrw", ".arw", ".srf", ".sr2",
        ".cr2", ".cr3", ".crw", ".raf", ".orf", ".rw2", ".pef",
    )

    /** 视频也进本机照片流（P2 录像落 App 目录后要能看见） */
    private val VIDEO_EXTS = listOf(".mov", ".mp4")

    private fun mimeFor(name: String): String = when {
        name.endsWith(".mov", true) -> "video/quicktime"
        name.endsWith(".mp4", true) -> "video/mp4"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".webp", true) -> "image/webp"
        name.endsWith(".heic", true) || name.endsWith(".heif", true) -> "image/heic"
        name.endsWith(".dng", true) -> "image/x-adobe-dng"
        name.endsWith(".nef", true) || name.endsWith(".nrw", true) -> "image/x-nikon-nef"
        name.endsWith(".arw", true) -> "image/x-sony-arw"
        name.endsWith(".cr2", true) -> "image/x-canon-cr2"
        name.endsWith(".cr3", true) -> "image/x-canon-cr3"
        name.endsWith(".raf", true) -> "image/x-fuji-raf"
        else -> "image/jpeg"
    }

    /** 编辑缩略图缓存目录（非破坏性：原图永不被改写，只在此写派生图） */
    fun editThumbDir(context: Context): File =
        File(context.cacheDir, "edits")

    /** 原图文件对应的缓存缩略图文件（按 displayName hash 命名，避开文件名非法字符） */
    fun editThumbFile(context: Context, displayName: String): File =
        File(editThumbDir(context), "${displayName.hashCode()}.jpg")

    /** 渲染成功后写缩略图缓存，供本机主页显示调节后的样子 */
    fun saveEditThumb(context: Context, displayName: String, jpegBytes: ByteArray) {
        val dir = editThumbDir(context)
        if (!dir.exists()) dir.mkdirs()
        editThumbFile(context, displayName).writeBytes(jpegBytes)
    }

    /** 恢复原图：删掉该照片的派生缩略图 */
    fun clearEditThumb(context: Context, displayName: String) {
        editThumbFile(context, displayName).delete()
    }

    /**
     * 原图宽高比（裁切必须从原图整幅重算，不能用已裁切的预览图，否则越裁越小）。
     * 用 inJustDecodeBounds 只读头，不解码整图；RAW 无法解码时返回 null，由调用方兜底。
     */
    fun originalAspect(context: Context, displayName: String): Float? {
        val file = File(appPictureDir(context), displayName)
        if (!file.exists()) return null
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth.toFloat() / opts.outHeight.toFloat() else null
        } catch (_: Throwable) {
            null
        }
    }

    /** 删除原照片文件 + 其编辑缩略图，返回是否成功 */
    fun deletePhoto(context: Context, photo: PhotoItem): Boolean {
        val original = File(appPictureDir(context), photo.displayName)
        val deleted = if (original.exists()) original.delete() else false
        clearEditThumb(context, photo.displayName)
        return deleted
    }

    /** 照片文件信息（详情页「查看参数」面板用） */
    data class PhotoInfo(
        val name: String,
        val format: String,   // RAW / JPEG / PNG ...
        val sizeText: String, // 人类可读大小
        val dateText: String, // 修改日期
        val dimensions: String, // 宽×高，读取失败时为「—」
    )

    /** 读取照片的文件信息（尺寸用 BitmapFactory inJustDecodeBounds，不解码整图） */
    fun photoInfo(context: Context, photo: PhotoItem): PhotoInfo {
        val file = File(appPictureDir(context), photo.displayName)
        val sizeText = when {
            file.length() >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", file.length() / 1024f / 1024f)
            file.length() >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", file.length() / 1024f)
            else -> "${file.length()} B"
        }
        val dateText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(photo.dateAdded * 1000L))
        var dimensions = "—"
        if (photo.isVideo) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                if (w != null && h != null) dimensions = "$w × $h"
            } catch (_: Throwable) {
            } finally {
                runCatching { retriever.release() }
            }
        } else {
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) dimensions = "${opts.outWidth} × ${opts.outHeight}"
            } catch (_: Throwable) { }
        }
        return PhotoInfo(
            name = photo.displayName,
            format = when {
                photo.isVideo -> "VIDEO"
                photo.isRaw -> "RAW"
                else -> photo.mimeType.removePrefix("image/").uppercase()
            },
            sizeText = sizeText,
            dateText = dateText,
            dimensions = dimensions,
        )
    }

    /** 视频首帧缩略图（最大边 320px，避免大帧 OOM；失败返回 null，UI 用图标占位） */
    fun videoThumbnail(context: Context, displayName: String): ImageBitmap? {
        val file = File(appPictureDir(context), displayName)
        if (!file.exists()) return null
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(0) ?: return null
            val scale = (320f / maxOf(frame.width, frame.height).coerceAtLeast(1)).coerceAtMost(1f)
            val thumb = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    frame,
                    (frame.width * scale).toInt().coerceAtLeast(1),
                    (frame.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else frame
            thumb.asImageBitmap()
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun loadPhotos(context: Context): List<PhotoItem> {
        val dir = appPictureDir(context)
        if (!dir.exists()) return emptyList()
        return (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && (IMAGE_EXTS + VIDEO_EXTS).any { ext -> it.name.endsWith(ext, true) } }
            .map { f ->
                val thumb = editThumbFile(context, f.name)
                // 只读边界拿原图尺寸（不解码像素，几乎不占内存），供详情页渐进式局部解码使用
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                runCatching { BitmapFactory.decodeFile(f.absolutePath, bounds) }
                PhotoItem(
                    uri = Uri.fromFile(f),
                    displayName = f.name,
                    dateAdded = f.lastModified() / 1000L,
                    mimeType = mimeFor(f.name),
                    width = bounds.outWidth,
                    height = bounds.outHeight,
                    editedThumb = if (thumb.exists()) Uri.fromFile(thumb) else null,
                )
            }
            .sortedByDescending { it.dateAdded }
    }

    /**
     * 把按 DATE_ADDED DESC 排好的照片按日期分组。
     * 今天 / 昨天 / 更早的按「yyyy年M月d日」分组。
     */
    fun groupByDate(photos: List<PhotoItem>): List<PhotoGroup> {
        if (photos.isEmpty()) return emptyList()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = cal.timeInMillis / 1000
        val yesterday = today - 86400
        val fmt = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())

        val groups = mutableListOf<PhotoGroup>()
        var currentLabel = ""
        var currentList = mutableListOf<PhotoItem>()
        for (photo in photos) {
            val label = when {
                photo.dateAdded >= today -> "今天"
                photo.dateAdded >= yesterday -> "昨天"
                else -> fmt.format(Date(photo.dateAdded * 1000))
            }
            if (label != currentLabel) {
                if (currentList.isNotEmpty()) {
                    groups.add(PhotoGroup(currentLabel, currentList.toList()))
                    currentList = mutableListOf()
                }
                currentLabel = label
            }
            currentList.add(photo)
        }
        if (currentList.isNotEmpty()) groups.add(PhotoGroup(currentLabel, currentList.toList()))
        return groups
    }
}

/** 一个日期分组：label（今天/昨天/yyyy年M月d日）+ 该组照片 */
data class PhotoGroup(val label: String, val photos: List<PhotoItem>)

// ─────────── 月/季/年分组网格（照片流 + 右缘时间轴共用，对齐 lazyfilm） ───────────

/** 月份分组网格条目：Header=整行月份标题，Photo=单格照片 */
sealed class MonthGridEntry {
    data class Header(val key: String, val label: String) : MonthGridEntry()
    data class Photo(val photo: PhotoItem) : MonthGridEntry()
}

/** 月份网格 + 照片下标→网格下标 的换算表（时间轴跳转/选中锚定标题行用） */
class MonthGrid(
    val entries: List<MonthGridEntry>,
    /** 照片下标 → 网格条目下标 */
    val photoGridIdx: IntArray,
    /** 照片下标 → 其所属分组标题的网格条目下标（跳转锚点，跳过去标题可见） */
    val headerGridIdx: IntArray,
)

/**
 * 按「本年度按月 / 上一年按季 / 更早按年」聚合（与时间轴刻度同源）。
 * 照片须按 dateAdded DESC 排好（loadPhotos 已保证）。
 */
fun buildMonthGrid(photos: List<PhotoItem>): MonthGrid {
    val cal = Calendar.getInstance()
    val thisYear = cal.get(Calendar.YEAR)
    val entries = mutableListOf<MonthGridEntry>()
    val photoGridIdx = IntArray(photos.size)
    val headerGridIdx = IntArray(photos.size)
    var curKey = ""
    var lastHeaderIdx = 0
    photos.forEachIndexed { idx, p ->
        cal.timeInMillis = p.dateAdded * 1000L
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val key: String
        val label: String
        when {
            y == thisYear -> { key = "M$m"; label = "${m}月" }
            y == thisYear - 1 -> {
                val q = (m - 1) / 3 + 1
                key = "Q$q"; label = "${y % 100}年Q$q"
            }
            else -> { key = "Y$y"; label = "${y}年" }
        }
        if (key != curKey) {
            curKey = key
            entries.add(MonthGridEntry.Header(key, label))
            lastHeaderIdx = entries.size - 1
        }
        headerGridIdx[idx] = lastHeaderIdx
        entries.add(MonthGridEntry.Photo(p))
        photoGridIdx[idx] = entries.size - 1
    }
    return MonthGrid(entries, photoGridIdx, headerGridIdx)
}
