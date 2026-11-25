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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmlens.nicolink.ui.AppNav
import com.filmlens.nicolink.ui.shared.GlassIconButton
import com.filmlens.nicolink.ui.shared.GlassPage
import com.filmlens.nicolink.ui.theme.AppIcons
import com.filmlens.nicolink.ui.theme.LocalLazyFilmColors

/**
 * 开源许可页：对齐 lazyfilm 设置页「开源许可」的卡片列表样式。
 * 只列依赖与许可信息，点击条目用系统浏览器打开项目主页。
 */
@Composable
fun LicensesScreen(nav: AppNav) {
    val colors = LocalLazyFilmColors.current
    val uriHandler = LocalUriHandler.current
    val libs = listOf(
        LicenseItem("Coil 3", "Apache-2.0", "图片加载与缓存", "https://github.com/coil-kt/coil"),
        LicenseItem("Zoomable", "Apache-2.0", "KMP 手势缩放", "https://github.com/usuiat/Zoomable"),
        LicenseItem("backdrop / shapes", "Apache-2.0", "液态玻璃控件", "https://github.com/kyant0/backdrop"),
        LicenseItem("Media3 / ExoPlayer", "Apache-2.0", "App 内 .mov/.mp4 播放", "https://developer.android.com/media/media3"),
        LicenseItem("kotlinx.serialization", "Apache-2.0", "序列化", "https://github.com/Kotlin/kotlinx.serialization"),
        LicenseItem("SpektraLab Engine", "GPL-3.0-or-later", "显影引擎 .so 与 JNI 门面（动态加载隔离）", "https://github.com/SAINTTaiYi/SpektraLab-Android"),
        LicenseItem("LibRaw", "GPLv3 系列", "RAW 解码", "https://www.libraw.org/"),
        LicenseItem("Spektrafilm 数据", "CC BY-SA 4.0", "胶片 profile / LUT，禁止单独转售", "https://spektrafilm.com/"),
    )
    GlassPage(
        topBar = { backdrop ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(AppIcons.ArrowLeft, "返回", backdrop, onClick = { nav.pop() })
                Spacer(Modifier.width(12.dp))
                Text("开源许可", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        },
        content = {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 80.dp, bottom = 32.dp),
            ) {
                Text(
                    "本应用使用了以下开源项目，感谢开源社区的贡献。",
                    color = colors.textSecondary, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(colors.bgCard),
                ) {
                    libs.forEachIndexed { index, lib ->
                        if (index > 0) {
                            androidx.compose.material3.HorizontalDivider(color = colors.bgDivider)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { runCatching { uriHandler.openUri(lib.url) } }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(AppIcons.Cog, contentDescription = null, tint = colors.brandLight1,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(lib.name, color = colors.textPrimary, fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium)
                                Text(lib.desc, color = colors.textTertiary, fontSize = 11.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(lib.license, color = colors.brandLight1, fontSize = 11.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(AppIcons.ChevronRight, contentDescription = null,
                                tint = colors.iconSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "显影引擎以动态加载/模块隔离方式集成，连接层与 UI 层保持独立；" +
                        "胶片数据禁止单独转售。上游署名见设置页「关于」。",
                    color = colors.textTertiary, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        },
    )
}

private data class LicenseItem(val name: String, val license: String, val desc: String, val url: String)
