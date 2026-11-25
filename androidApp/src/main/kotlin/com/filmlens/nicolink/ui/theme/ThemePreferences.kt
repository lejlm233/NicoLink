package com.filmlens.nicolink.ui.theme

import android.content.Context

/**
 * 主题持久化 — Android SharedPreferences
 * 在 MainActivity.onCreate 中读取，设置页切换时写回。
 */
object ThemePreferences {
    private const val PREFS_NAME = "nicolink_theme"
    private const val KEY_THEME_MODE = "theme_mode"

    fun getMode(context: Context): ThemeMode {
        val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, ThemeMode.SYSTEM.value) ?: ThemeMode.SYSTEM.value
        return ThemeMode.entries.find { it.value == name } ?: ThemeMode.SYSTEM
    }

    fun setMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.value)
            .apply()
    }
}
