package com.filmlens.nicolink.film

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 临时诊断落盘（M3 根因定位用，定位完可删除）。
 *
 * 写到 App 专属外部存储的 Download/nicolink-diag/ 目录，无需存储权限，
 * 可用 `adb pull /sdcard/Android/data/com.filmlens.nicolink/files/Download/nicolink-diag/` 取出。
 *
 * 每一步同步写盘：native 崩溃会直接杀进程，异步日志会丢掉最关键的一步。
 */
object DiagLog {
    private const val DIR_NAME = "nicolink-diag"
    private val lock = Any()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var dir: File? = null

    /** 在 FilmEngine 初始化时调用一次；返回诊断目录。 */
    fun init(context: Context): File {
        val app = context.applicationContext ?: context
        val parent = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(app.filesDir, "Download")
        val d = File(parent, DIR_NAME).also { it.mkdirs() }
        dir = d
        return d
    }

    fun crashPath(context: Context): String = File(init(context), "native-crash.txt").absolutePath

    /** 追加一行带时间戳/线程名的记录，立即落盘。 */
    fun mark(step: String) {
        val d = dir ?: return
        synchronized(lock) {
            try {
                val line = "${fmt.format(Date())} [${Thread.currentThread().name}] $step\n"
                File(d, "trace.log").appendText(line)
            } catch (_: Throwable) {
            }
        }
    }

    /** 覆盖写一个文件（params / 引擎实际收到的参数等）。 */
    fun write(name: String, content: String) {
        val d = dir ?: return
        synchronized(lock) {
            try {
                File(d, name).writeText(content)
            } catch (_: Throwable) {
            }
        }
    }

    /** 记录异常完整堆栈。 */
    fun exception(where: String, t: Throwable) {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        mark("!! EXCEPTION in $where: $t\n${sw.toString()}")
    }
}
