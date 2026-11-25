package com.filmlens.nicolink.ui.theme

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * 触感反馈 — 在 Compose clickable onClick 中调用
 * 使用 View.performHapticFeedback，CONFIRM 比 KEYBOARD_TAP 震感明显
 *
 * 用法:
 *   onClick = {
 *       hapticClick(view, hapticEnabled)
 *       handleClick()
 *   }
 */
fun hapticClick(view: View, enabled: Boolean) {
    if (enabled) {
        try {
            view.performHapticFeedback(
                HapticFeedbackConstants.CONFIRM,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
        } catch (_: Exception) {}
    }
}

/**
 * 机身化轻触反馈（对齐 lazyfilm 617 的 vibrateTap/vibrateStep ≈ 20ms）：
 * 快门/金属拨杆/模式切换用，比 CONFIRM 更短更脆。
 */
fun vibrateTap(view: View, enabled: Boolean) {
    if (enabled) {
        try {
            view.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
        } catch (_: Exception) {}
    }
}
