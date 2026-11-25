package com.filmlens.nicolink.film

import androidx.compose.runtime.compositionLocalOf

/** 显影引擎桥接的 CompositionLocal（由 App 注入 androidApp 的实现） */
val LocalFilmBridge = compositionLocalOf<FilmBridge?> { null }

/**
 * 「无相纸」哨兵值：相纸目录里没有真实对应项，UI 上表示未选择相纸/未显影。
 * 引擎收到该值时会 fallback 到默认相纸（见 FilmEngine.paramsJson），保证渲染不失败。
 */
const val PAPER_NONE = "__none__"

/**
 * 「原图」哨兵值：胶片槽位选中"原图"（未选具体胶片）时使用。
 * 引擎收到该值会 fallback 到基准胶片（见 FilmEngine.paramsJson），保证渲染/导出不失败，
 * 但 UI 上胶片始终停留在"原图"，不会被自动改成 Kodak Portra 400。
 */
const val FILM_NONE = "__none__"

/**
 * 显影引擎桥接契约（纯 Kotlin，放在 commonMain，供 DevelopScreen 使用）。
 *
 * 具体实现在 androidApp 模块（[AndroidFilmBridge]），通过 [LocalFilmBridge] 注入，
 * 这样 GPL 引擎（app.spektralab.engine 包 + .so）不进入 commonMain，物理隔离。
 */
interface FilmBridge {
    fun isReady(): Boolean
    /** 胶片清单（id 传给引擎，name 展示） */
    fun films(): List<StockOption>
    /** 相纸清单 */
    fun papers(): List<StockOption>
    /** 渲染预览，返回 PNG 字节；失败返回 null */
    fun render(photoUri: String, filmId: String, paperId: String, params: FilmParams): ByteArray?
    /** 全分辨率导出到文件，返回显示路径；失败抛异常。format=jpg/tiff/dng，quality 仅 jpg 生效（1-100） */
    fun export(photoUri: String, filmId: String, paperId: String, params: FilmParams, format: String = "jpg", quality: Int = 95): String

    /** 原图导出：不做任何修改，直接把 App 目录的原始图片复制到相册 DCIM/NicoLink，返回显示路径 */
    fun exportOriginal(photoUri: String, displayName: String): String
    /** 释放某张照片缓存的会话（非破坏性编辑复位/离开时用）；photoUri 为 null 释放全部 */
    fun release(photoUri: String? = null)
    /** 渲染成功后保存派生缩略图（非破坏性：原图不动），供主页显示调节后样子 */
    fun saveEditThumb(photoUri: String, displayName: String, pngBytes: ByteArray)
    /** 恢复原图：删除派生缩略图 */
    fun clearEditThumb(displayName: String)

    /**
     * 胶片卡片真实效果缩略图：用引擎把自带样片按各胶片渲染后落盘的 JPEG 字节。
     * 未生成好返回 null（UI 显示占位），[ensureFilmThumbs] 会在后台逐张生成。
     */
    fun filmThumb(filmId: String): ByteArray? = null

    /**
     * 触发生成全部胶片的真实效果缩略图（后台、幂等，已生成的跳过）。
     * [onProgress] 回调 (已生成数, 总数) 供 UI 刷新。
     */
    fun ensureFilmThumbs(films: List<StockOption>, onProgress: (Int, Int) -> Unit = { _, _ -> }) {}
}

/** 胶片/相纸选项 */
data class StockOption(
    val id: String,
    val name: String,
    val isPositive: Boolean = false,
    /** 该胶片声明的配套相纸（权威 stock_catalog.json films[].targetPrint）；仅胶片有，相纸为空 */
    val targetPrint: String = "",
)

/**
 * 显影参数（UI → 引擎）。
 *
 * 用 [wire] 承载任意引擎 wire 字段（完整字典见 SpektraLab MainActivity delta 构造），
 * 键为 wire 名（如 "print_exposure"、"halation_amount"），值为 String/Number/Boolean。
 * 这样 UI 层加参数无需改契约，直接往 map 里塞即可，也保持 GPL 引擎隔离在 androidApp。
 */
data class FilmParams(
    val wire: Map<String, Any> = emptyMap(),
    // 兼容旧字段（DevelopScreen 演进期保留）
    val autoExposure: Boolean = true,
    val exposureEv: Float = 0f,
    val grain: Boolean = true,
    val halation: Boolean = true,
    /** 分级预览长边：拖动时传较小值（快），松手传 1200（清）；null=用 wire 的值 */
    val previewLongEdge: Int? = null,
) {
    /** 取 wire 值，缺省返回 default */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String, default: T): T = (wire[key] as? T) ?: default
}
