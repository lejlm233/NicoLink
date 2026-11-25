package com.filmlens.nicolink.ui

import android.content.Context

/** 轻量 UI 偏好（主页 Tab 记忆等），跨 App 重启持久化 */
object UiPrefs {
    private const val PREFS = "nicolink_ui"
    private const val KEY_LAST_TAB = "last_tab"
    private const val KEY_CONNECT_MODE = "connect_mode"
    private const val KEY_EXPORT_FORMAT = "export_format"
    private const val KEY_EXPORT_QUALITY = "export_quality"
    private const val KEY_AUTO_CONNECT = "auto_connect_camera"
    private const val KEY_AUTO_DEVELOP_ENABLED = "auto_develop_enabled"
    private const val KEY_AUTO_DEVELOP_PRESET = "auto_develop_preset"
    private const val KEY_AUTO_DEVELOP_JPG = "auto_develop_jpg"
    private const val KEY_AUTO_DEVELOP_RAW = "auto_develop_raw"
    private const val KEY_KEEP_ALIVE = "keep_alive_enabled"
    private const val KEY_LIQUID_GLASS = "liquid_glass_enabled"
    private const val KEY_CAMERA_CLOUD_SYNC = "camera_cloud_sync_enabled"
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    var lastTab: Int
        get() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getInt(KEY_LAST_TAB, 0) ?: 0
        set(value) {
            context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putInt(KEY_LAST_TAB, value)?.apply()
        }

    /**
     * 相机陈列架云端同步授权：默认关闭（false）。
     * 用户明确同意后才上传相机信息到云端；未授权时 App 不做任何相机数据上报。
     */
    var cameraCloudSyncEnabled: Boolean
        get() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getBoolean(KEY_CAMERA_CLOUD_SYNC, false) ?: false
        set(value) {
            context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putBoolean(KEY_CAMERA_CLOUD_SYNC, value)?.apply()
        }

    /** 连接模式：ap = 相机开热点（浏览照片推荐）；sta = 相机连手机热点 */
    var connectMode: String
        get() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getString(KEY_CONNECT_MODE, null) ?: "sta"
        set(value) {
            context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putString(KEY_CONNECT_MODE, value)?.apply()
        }

    /** 上次导出格式：jpg / tiff / dng */
    var exportFormat: String
        get() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getString(KEY_EXPORT_FORMAT, null) ?: "jpg"
        set(value) {
            context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putString(KEY_EXPORT_FORMAT, value)?.apply()
        }

    /** 上次导出 JPG 质量（1-100，默认95） */
    var exportQuality: Int
        get() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getInt(KEY_EXPORT_QUALITY, 95) ?: 95
        set(value) {
            context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putInt(KEY_EXPORT_QUALITY, value)?.apply()
        }

    /** 自动连接上次相机：默认开（与原有行为一致），关闭后连接页不再自动重连 */
    var autoConnectCamera: Boolean
        get() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getBoolean(KEY_AUTO_CONNECT, true) ?: true
        set(value) {
            context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putBoolean(KEY_AUTO_CONNECT, value)?.apply()
        }

    /** 自动显影总开关：开启后新落盘的 JPG/RAW 自动应用选定预设 */
    var autoDevelopEnabled: Boolean
        get() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getBoolean(KEY_AUTO_DEVELOP_ENABLED, false) ?: false
        set(value) {
            context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putBoolean(KEY_AUTO_DEVELOP_ENABLED, value)?.apply()
        }

    /** 自动显影选用的预设名（FilmPresets 中的 name） */
    var autoDevelopPreset: String
        get() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getString(KEY_AUTO_DEVELOP_PRESET, "") ?: ""
        set(value) {
            context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putString(KEY_AUTO_DEVELOP_PRESET, value)?.apply()
        }

    /** 自动显影是否处理 JPG（默认 true） */
    var autoDevelopJpg: Boolean
        get() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getBoolean(KEY_AUTO_DEVELOP_JPG, true) ?: true
        set(value) {
            context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putBoolean(KEY_AUTO_DEVELOP_JPG, value)?.apply()
        }

    /** 自动显影是否处理 RAW（默认 false，RAW 渲染较慢） */
    var autoDevelopRaw: Boolean
        get() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getBoolean(KEY_AUTO_DEVELOP_RAW, false) ?: false
        set(value) {
            context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putBoolean(KEY_AUTO_DEVELOP_RAW, value)?.apply()
        }

    /** 液态玻璃总开关（默认 true）：关闭后控件不再折射内容，退化为实色界面 */
    var liquidGlassEnabled: Boolean
        get() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getBoolean(KEY_LIQUID_GLASS, true) ?: true
        set(value) {
            context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putBoolean(KEY_LIQUID_GLASS, value)?.apply()
        }

    /** 保活通知开关（默认 true）：前台服务常驻通知，防止后台断开相机连接 */
    var keepAliveEnabled: Boolean
        get() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getBoolean(KEY_KEEP_ALIVE, true) ?: true
        set(value) {
            context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putBoolean(KEY_KEEP_ALIVE, value)?.apply()
        }
}
