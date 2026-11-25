package com.filmlens.nicolink.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * LazyFilm 配色设计令牌
 * 所有颜色按层级和语义组织，双实例（暗/亮）
 *
 * 品牌色系：
 *   主色     #1A956B  — 翡翠绿
 *   深色①    #082B1F  — 最深底/卡片
 *   深色②    #0B3E2D  — 弹窗/面板
 *   浅色①    #39DCA4  — 高光
 *   浅色②    #8FECCC  — 次要高光
 *   对比色   #FFF143  — 重点标注
 *   警告色   #F20C00  — 删除/错误
 */
data class LazyFilmColors(
    val bgPrimary: Color,
    val bgElevated: Color,
    val bgCard: Color,
    val bgSurface: Color,
    val bgControl: Color,
    val bgHover: Color,
    val bgDivider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val textInverse: Color,
    val borderDefault: Color,
    val borderStrong: Color,
    val borderDivider: Color,
    val iconPrimary: Color,
    val iconSecondary: Color,
    val iconDisabled: Color,
    // Brand
    val brandPrimary: Color,
    val brandDark1: Color,
    val brandDark2: Color,
    val brandLight1: Color,
    val brandLight2: Color,
    val brandContrast: Color,
    val brandDanger: Color,
    // Semantic
    val semanticOverlay: Color = Color(0x99000000),
    val viewfinderBackground: Color = Color(0xFF000000),
) {
    companion object {
        /** 暗色模式 — 暗房沉浸 */
        val Dark = LazyFilmColors(
            bgPrimary = Color(0xFF1C1C1C),
            bgElevated = Color(0xFF0B3E2D),
            bgCard = Color(0xFF082B1F),
            bgSurface = Color(0xFF222222),
            bgControl = Color(0xFF2A2A2A),
            bgHover = Color(0xFF333333),
            bgDivider = Color(0xFF2D2D2D),
            textPrimary = Color(0xFFF6F7F9),
            textSecondary = Color(0xFF8FECCC),
            textTertiary = Color(0xFF999999),
            textDisabled = Color(0xFF555555),
            textInverse = Color(0xFF1C1C1C),
            borderDefault = Color(0xFF333333),
            borderStrong = Color(0xFF0B3E2D),
            borderDivider = Color(0xFF2D2D2D),
            iconPrimary = Color(0xFFF6F7F9),
            iconSecondary = Color(0xFF8FECCC),
            iconDisabled = Color(0xFF444444),
            brandPrimary = Color(0xFF1A956B),
            brandDark1 = Color(0xFF082B1F),
            brandDark2 = Color(0xFF0B3E2D),
            brandLight1 = Color(0xFF39DCA4),
            brandLight2 = Color(0xFF8FECCC),
            brandContrast = Color(0xFFFFF143),
            brandDanger = Color(0xFFF20C00),
        )

        /** 亮色模式 — 观片台 */
        val Light = LazyFilmColors(
            bgPrimary = Color(0xFFEFF0EA),
            bgElevated = Color(0xFFEFF0EA),
            bgCard = Color(0xFFF6FAF8),
            bgSurface = Color(0xFFE8F5EF),
            bgControl = Color(0xFFD4EDE3),
            bgHover = Color(0xFFC5E6D8),
            bgDivider = Color(0xFFC5E6D8),
            textPrimary = Color(0xFF1C1C1C),
            textSecondary = Color(0xFF0B3E2D),
            textTertiary = Color(0xFF777777),
            textDisabled = Color(0xFFAAAAAA),
            textInverse = Color(0xFFFFFFFF),
            borderDefault = Color(0xFFB8D9C8),
            borderStrong = Color(0xFF0B3E2D),
            borderDivider = Color(0xFFC5E6D8),
            iconPrimary = Color(0xFF1C1C1C),
            iconSecondary = Color(0xFF777777),
            iconDisabled = Color(0xFFCCCCCC),
            brandPrimary = Color(0xFF1A956B),
            brandDark1 = Color(0xFF082B1F),
            brandDark2 = Color(0xFF0B3E2D),
            brandLight1 = Color(0xFF39DCA4),
            brandLight2 = Color(0xFF8FECCC),
            brandContrast = Color(0xFFFFF143),
            brandDanger = Color(0xFFF20C00),
            // 亮色遮罩更浅
            semanticOverlay = Color(0x4C000000),
        )
    }
}
