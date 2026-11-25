package com.filmlens.nicolink.ui.shared.liquid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 液态玻璃总开关。
 * 当前**默认启用**（App.kt 中状态恒为 true，设置页开关暂时隐藏）；
 * 各页面的「Liquid 控件 / 原生控件」分支结构原样保留，原生实现完整存在于
 * else 分支备用。恢复用户可选时：App.kt 状态改回读 SharedPreferences，
 * 并取消 SettingsScreen 中开关行的注释。
 */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

/** 液态玻璃是否启用 */
@Composable
fun isLiquidGlassEnabled(): Boolean = LocalLiquidGlassEnabled.current

/**
 * 液态玻璃控件的「亮/暗主题」来源。
 *
 * 玻璃控件的表面色（FAFAFA / 121212）必须跟随 **App 主题**（LocalLazyFilmColors，
 * 由 App 内观片台/暗房设置决定），而不是 isSystemInDarkTheme()（系统暗色）——
 * 否则「App 亮色 + 系统暗色」时控件会误用暗色表面，亮色界面里呈灰色。
 * null=未提供，控件回退系统暗色判断（保持官方原行为）。
 */
val LocalLiquidGlassLightTheme = staticCompositionLocalOf { null as Boolean? }

/** 玻璃控件当前是否应使用亮色主题表面（优先 App 主题，未提供则回退系统暗色取反） */
@Composable
fun isLiquidGlassLightTheme(): Boolean =
    LocalLiquidGlassLightTheme.current ?: !androidx.compose.foundation.isSystemInDarkTheme()
