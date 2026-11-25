package com.filmlens.nicolink.camera

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * 把相机传来的照片落到 App 专属目录（getExternalFilesDir/Pictures/NicoLink）：
 * 落在 Android/data/<包名>/files 下，读写都不需要任何存储权限，
 * 且与"本机"Tab 的数据源（PhotoRepository.appPictureDir）保持一致。
 *
 * 分两步（create → 写入 → commit/abort），因为 PTP 下载是挂起函数，
 * 不能塞进一个同步 write 回调里。
 */
object CameraMedia {
    fun create(context: Context, filename: String, mime: String): PendingMedia {
        val safe = sanitize(filename)
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
            "NicoLink",
        )
        if (!dir.exists() && !dir.mkdirs()) throw IOException("无法创建目录 ${dir.absolutePath}")
        val file = File(dir, safe)
        return PendingMedia(context, file, file.absolutePath, mime)
    }

    /** 相机文件名是不可信输入：只保留文件名部分并去掉控制字符 */
    private fun sanitize(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\u0000-\\u001F]"), "_")
            .trim()
        return base.ifBlank { "IMG_${System.currentTimeMillis()}.jpg" }
    }
}

/** 一条待落盘的照片；commit 后即可在"本机"Tab 看到 */
class PendingMedia internal constructor(
    private val context: Context,
    private val file: File,
    val displayPath: String,
    private val mime: String,
) {
    fun openOutput(): OutputStream = file.outputStream()

    /** App 私有目录即时可见，无需 MediaScanner / IS_PENDING */
    fun commit() { /* no-op */ }

    fun abort() { runCatching { file.delete() } }
}
