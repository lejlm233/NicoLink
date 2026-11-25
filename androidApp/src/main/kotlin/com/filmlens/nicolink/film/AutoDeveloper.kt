package com.filmlens.nicolink.film

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * 自动显影：相机自动上传的照片落盘后，按设置页选定的预设对 JPG/RAW 自动应用参数。
 * 由 MainActivity 注册为 CameraSession.autoDevelopHook。
 * 单飞串行队列：每张显影完立即保存缩略图并通知 UI 刷新，不等全部传输完成。
 */
object AutoDeveloper {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var pending = 0

    /**
     * @param displayPath 落盘文件绝对路径
     * @param displayName 文件名（含扩展名，用于判断 JPG/RAW 及持久化 key）
     */
    fun onPhotoSaved(context: Context, bridge: FilmBridge, displayPath: String, displayName: String) {
        val prefs = context.getSharedPreferences("nicolink_ui", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_develop_enabled", false)) return
        val isRaw = isRawName(displayName)
        if (isRaw) {
            if (!prefs.getBoolean("auto_develop_raw", false)) return
        } else {
            if (!prefs.getBoolean("auto_develop_jpg", true)) return
        }
        val presetName = prefs.getString("auto_develop_preset", "") ?: ""
        if (presetName.isBlank()) return
        val preset = FilmPresets.load(context).firstOrNull { it.name == presetName }
        if (preset == null) {
            com.filmlens.nicolink.film.DiagLog.mark("autoDevelop: preset not found: $presetName")
            return
        }
        val file = File(displayPath)
        if (!file.exists()) return

        pending++
        updateProgress(active = true, currentName = displayName, remaining = pending)
        com.filmlens.nicolink.film.DiagLog.mark("autoDevelop: QUEUED $displayName pending=$pending (落盘即入队，与传输并行)")
        scope.launch {
            mutex.withLock {
                try {
                    val uri = Uri.fromFile(file)
                    com.filmlens.nicolink.film.DiagLog.mark("autoDevelop: start $displayName preset=$presetName raw=$isRaw")
                    val bytes = bridge.render(uri.toString(), preset.filmId, preset.paperId,
                        FilmParams(wire = preset.wire)) ?: run {
                        com.filmlens.nicolink.film.DiagLog.mark("autoDevelop: render failed $displayName")
                        return@withLock
                    }
                    bridge.saveEditThumb(uri.toString(), displayName, bytes)
                    DevelopEdits.save(context, displayName,
                        EditState(preset.filmId, preset.paperId, preset.wire, System.currentTimeMillis()))
                    // 每张显影完立即通知 UI 刷新缩略图（三页一致），不等全部传输完成
                    com.filmlens.nicolink.camera.CameraSession.onEditThumbSaved?.invoke()
                    com.filmlens.nicolink.film.DiagLog.mark("autoDevelop: done $displayName")
                } catch (t: Throwable) {
                    com.filmlens.nicolink.film.DiagLog.exception("autoDevelop", t)
                } finally {
                    pending--
                    updateProgress(active = pending > 0, currentName = "", remaining = pending)
                }
            }
        }
    }

    private fun updateProgress(active: Boolean, currentName: String, remaining: Int) {
        com.filmlens.nicolink.camera.CameraSession.updateAutoDevelopProgress(
            com.filmlens.nicolink.camera.CameraSession.AutoDevelopProgress(active, currentName, remaining)
        )
    }

    private fun isRawName(name: String): Boolean {
        val n = name.lowercase()
        return listOf("dng", "nef", "nrw", "arw", "srf", "sr2", "cr2", "cr3", "crw", "raf", "orf", "rw2", "pef")
            .any { n.endsWith(".$it") }
    }
}
