package com.filmlens.nicolink.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmlens.nicolink.camera.CameraSession
import com.filmlens.nicolink.ui.AppNav
import com.filmlens.nicolink.ui.shared.GlassIconButton
import com.filmlens.nicolink.ui.shared.GlassPage
import com.filmlens.nicolink.ui.theme.AppIcons
import com.filmlens.nicolink.ui.theme.LocalLazyFilmColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 调试页：设置 → 关于 → 软件版本号长按 5 秒进入。
 *
 * 专门给 AI / 开发者排查用：展示连接与协议日志（[CameraSession.logs]）和能力探测报告
 * （[CameraSession.capabilitiesReport]），并提供：
 * - 输出日志：复制到剪贴板 + 保存一份到外部专属目录 debug/（adb 可直接 pull）
 * - 清空日志：[CameraSession.clearLogs]
 * - 能力探测：[CameraSession.probeCapabilities]（无副作用读取 DeviceInfo 声明）
 *
 * 日志同时由 CameraSession 落盘到 cacheDir/nicolink_debug.log（超 400KB 自动截断）。
 */
@Composable
fun DebugScreen(nav: AppNav) {
    val colors = LocalLazyFilmColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val logs = CameraSession.logs
    val capReport = CameraSession.capabilitiesReport
    var toast by mutableStateOf<String?>(null)

    // 输出日志：剪贴板 + 外部存储文件（adb pull 可拉）
    val exportLogs: () -> Unit = {
        val text = logs.joinToString("\n")
        clipboard.setText(AnnotatedString(text))
        val dir = File(context.getExternalFilesDir(null), "debug").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(dir, "nicolink_debug_$stamp.log")
        runCatching { file.writeText(text) }
        toast = if (file.exists()) {
            "已复制并保存：Android/data/${context.packageName}/files/debug/${file.name}"
        } else {
            "已复制到剪贴板（文件保存失败）"
        }
    }

    GlassPage(
        topBar = { backdrop ->
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(AppIcons.ArrowLeft, "返回", backdrop, onClick = { nav.pop() })
                Spacer(Modifier.width(12.dp))
                Text("调试日志", color = colors.textPrimary, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            }
        },
        content = {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 80.dp, bottom = 32.dp),
            ) {
                if (toast != null) {
                    Text(toast!!, color = colors.brandLight1, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.bgCard)
                            .padding(12.dp))
                    Spacer(Modifier.height(10.dp))
                }

                // 操作区
                Text("操作", color = colors.textPrimary, fontSize = 15.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("输出日志", color = colors.brandLight1, fontSize = 13.sp,
                        modifier = Modifier.clickable(onClick = exportLogs).padding(vertical = 8.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("清空日志", color = colors.textSecondary, fontSize = 13.sp,
                        modifier = Modifier.clickable {
                            CameraSession.clearLogs()
                            toast = "日志已清空"
                        }.padding(vertical = 8.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("能力探测", color = colors.brandLight1, fontSize = 13.sp,
                        modifier = Modifier.clickable {
                            CameraSession.probeCapabilities()
                            toast = "已触发能力探测（需先连接相机）"
                        }.padding(vertical = 8.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "日志文件：Android/data/${context.packageName}/files/debug/（adb pull 可拉取）\n" +
                        "实时镜像：cacheDir/nicolink_debug.log（run-as 可读，超 400KB 截断）",
                    color = colors.textTertiary, fontSize = 11.sp,
                )

                Spacer(Modifier.height(18.dp))
                Text("能力探测报告", color = colors.textPrimary, fontSize = 15.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.bgCard)
                        .padding(12.dp)
                ) {
                    if (capReport.isEmpty()) {
                        Text("尚未探测，连接相机后点「能力探测」", color = colors.textTertiary, fontSize = 11.sp)
                    } else {
                        capReport.lineSequence().forEach { line ->
                            Text(line, color = colors.textSecondary, fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 1.dp))
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text("调试日志（${logs.size} 条）", color = colors.textPrimary, fontSize = 15.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.bgCard)
                        .padding(12.dp)
                ) {
                    if (logs.isEmpty()) {
                        Text("暂无日志", color = colors.textTertiary, fontSize = 11.sp)
                    } else {
                        logs.forEach { line ->
                            Text(line, color = colors.textSecondary, fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 1.dp))
                        }
                    }
                }
            }
        },
    )
}
