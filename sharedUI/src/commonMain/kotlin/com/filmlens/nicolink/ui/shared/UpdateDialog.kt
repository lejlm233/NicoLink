package com.filmlens.nicolink.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmlens.nicolink.data.cloud.AppLatestVersion
import com.filmlens.nicolink.ui.theme.LocalLazyFilmColors

// ── 发现新版本弹窗（主页静默检查 / 设置页手动检查共用） ──
// 视觉对齐 NicoLink 现有弹层（深色遮罩 + bgElevated 圆角卡片 + brandPrimary 主按钮）：
// 主链接缺失时退用备用链接，两者都空则按钮退化为「知道了」；
// force=true 时没有「以后再说」、且关闭手势/点击遮罩均不生效。

@Composable
fun UpdateDialog(
    latest: AppLatestVersion,
    currentVersionName: String,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalLazyFilmColors.current
    val primaryUrl = latest.downloadUrl.ifBlank { latest.downloadUrlBackup }
    val hasUrl = primaryUrl.isNotBlank()
    val hasBackup = latest.downloadUrl.isNotBlank() && latest.downloadUrlBackup.isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!latest.force) onDismiss() },
        containerColor = colors.bgElevated,
        shape = RoundedCornerShape(20.dp),
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "发现新版本",
                    fontSize = 12.sp,
                    letterSpacing = 3.sp,
                    color = colors.brandLight1,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (latest.versionName.isNotBlank()) "v${latest.versionName}" else "新版本来了",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                if (currentVersionName.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "当前 v$currentVersionName",
                        fontSize = 12.sp,
                        color = colors.textTertiary,
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (latest.notes.isNotBlank()) {
                    Text(
                        latest.notes,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                } else if (!hasUrl) {
                    Text(
                        "暂未提供下载地址，请留意后续通知。",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                }
                // 主按钮：品牌绿底，白字
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(colors.brandPrimary, RoundedCornerShape(23.dp))
                        .clickable {
                            if (hasUrl) onOpenUrl(primaryUrl) else onDismiss()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when {
                            latest.force -> "立即更新"
                            hasUrl -> "去更新"
                            else -> "知道了"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textInverse,
                    )
                }
                // 备用下载：弱化的文字入口（主链接还在时才单独出现）
                if (hasBackup) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "备用下载",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.clickable { onOpenUrl(latest.downloadUrlBackup) },
                    )
                }
                if (!latest.force) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "以后再说",
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                        modifier = Modifier.clickable { onDismiss() },
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}