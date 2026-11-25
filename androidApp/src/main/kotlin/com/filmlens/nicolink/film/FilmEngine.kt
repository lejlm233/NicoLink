package com.filmlens.nicolink.film

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import app.spektralab.engine.SpektraLabEngine
import com.filmlens.nicolink.film.ColorWheelState
import com.filmlens.nicolink.film.CurvePoint
import com.filmlens.nicolink.film.FilmParams
import com.filmlens.nicolink.film.StockOption
import com.filmlens.nicolink.film.VIEW_PARAM_KEYS
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * GPLv3 引擎的封装/隔离层。
 *
 * 注意：native JNI 符号绑定包名 `app.spektralab.engine.SpektraLabEngine`，
 * 门面类必须保持这个包名（已放在 androidApp/src/main/java/app/spektralab/engine/）。
 * 这个 Kotlin 文件是我们自己的调用代码，只通过门面访问引擎，不引入 GPL 到业务包。
 */
object FilmEngine {
    private const val TAG = "NicoLinkFilm"
    private const val ASSET_DIR = "spektralab/resources"
    /** 「无相纸」/正片直接扫描时给引擎的默认相纸（目录中的有效 id） */
    private const val DEFAULT_PAPER = "kodak_portra_endura"
    /** 「原图」状态给引擎的基准胶片（目录中的有效 id），仅用于让引擎不报错 */
    private const val DEFAULT_FILM = "kodak_portra_400"

    @Volatile
    private var engine: SpektraLabEngine? = null
    private val lock = Any()

    /** 会话缓存：同一张照片解码一次，后续只 setParams 重渲染（避免每次重解 24MB RAW）。 */
    private data class SessionEntry(
        val session: SpektraLabEngine.Session,
        val raw: Boolean,
        /** RAW 源临时文件：会话关闭时一并删除，避免每次渲染泄漏一份完整 RAW 副本 */
        val tempFile: java.io.File? = null,
    )
    private val sessions = mutableMapOf<String, SessionEntry>()

    /**
     * 会话缓存上限：每个 RAW 会话持有一份 ~22MB 临时源文件，无上限会累积到数 GB。
     * 用 LinkedHashMap 的插入顺序做 FIFO 淘汰（mutableMapOf 即 LinkedHashMap），
     * 淘汰时 closeEntry 会删掉对应临时文件。4 个足够覆盖来回切换的几张照片。
     */
    private const val MAX_SESSIONS = 4

    private fun evictOldestSession() {
        while (sessions.size > MAX_SESSIONS) {
            val oldest = sessions.keys.firstOrNull() ?: break
            sessions.remove(oldest)?.let { closeEntry(it) }
            DiagLog.mark("evict: closed oldest session $oldest, remaining=${sessions.size}")
        }
    }

    /** 正片（反转片）id 集合：权威 SpektraLab 对正片发 scan_film=true（直接扫描不走相纸）。
     *  从 stock_catalog.json 的 type=positive 读取，ensureEngine 时加载一次。 */
    private var positiveStockIds: Set<String> = emptySet()
    /** 胶片 id→显示名（权威 edgeTextFor(stock) 从目录名派生片边文字） */
    private var filmNames: Map<String, String> = emptyMap()

    /** 懒加载：prepare → 解压资源 → create（幂等） */
    private fun ensureEngine(context: Context): SpektraLabEngine {
        engine?.let { return it }
        return synchronized(lock) {
            engine?.let { return@synchronized it }
            DiagLog.init(context)
            DiagLog.mark("engine: begin")
            val crashFile = DiagLog.crashPath(context)
            val rc = SpektraLabEngine.installCrashHandler(crashFile)
            DiagLog.mark("engine: crashHandler rc=$rc path=$crashFile")
            val libDir = context.applicationInfo.nativeLibraryDir
            SpektraLabEngine.prepare(libDir)
            DiagLog.mark("engine: prepare OK, buildInfo=${SpektraLabEngine.buildInfo()}")
            Log.i(TAG, "prepare OK, buildInfo=${SpektraLabEngine.buildInfo()}")
            val resDir = ensureResources(context)
            DiagLog.mark("engine: resources OK, resDir=${resDir.absolutePath}, files=${resDir.listFiles()?.size}")
            val e = SpektraLabEngine.create(resDir.absolutePath)
            engine = e
            DiagLog.mark("engine: create OK, gpu=${e.capabilities()}")
            Log.i(TAG, "create OK, gpu=${e.capabilities()}")
            // 加载正片 id 集合（权威 isPositiveStock → scan_film=true）+ 胶片显示名（片边文字）
            runCatching {
                val arr = loadCatalog(context).getJSONArray("films")
                val names = mutableMapOf<String, String>()
                val positive = mutableSetOf<String>()
                (0 until arr.length()).forEach { i ->
                    val o = arr.getJSONObject(i)
                    val id = o.getString("id")
                    names[id] = o.optString("name", id)
                    if (o.optString("type", "") == "positive") positive.add(id)
                }
                filmNames = names
                positiveStockIds = positive
            }.onFailure {
                DiagLog.mark("engine: load catalog FAILED ${it.message}")
            }
            DiagLog.mark("engine: positive stocks=${positiveStockIds.size} $positiveStockIds")
            e
        }
    }

    fun buildInfo(context: Context): String {
        ensureEngine(context)
        return SpektraLabEngine.buildInfo()
    }

    /** 从 assets 的 stock_catalog.json 读胶片/相纸清单 */
    private fun loadCatalog(context: Context): JSONObject {
        val resDir = ensureResources(context)
        val file = File(resDir, "stock_catalog.json")
        return JSONObject(file.readText())
    }

    fun films(context: Context): List<StockOption> {
        val arr = loadCatalog(context).getJSONArray("films")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            StockOption(
                id,
                o.getString("name"),
                o.optString("type", "") == "positive",
                o.optString("targetPrint", ""), // 权威选胶片联动相纸用（pickFilmCard:4007-4008）
            )
        }
    }

    fun papers(context: Context): List<StockOption> {
        val arr = loadCatalog(context).getJSONArray("prints")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            StockOption(o.getString("id"), o.getString("name"))
        }
    }

    /**
     * 构造引擎 params JSON（扁平 wire 字段）。
     *
     * 完整照抄 SpektraLab MainActivity.applyParamsTo (line13450-13626) 的权威 delta。
     * 关键：所有字段必须显式发送——引擎对缺失字段有自己的默认，但那套默认与
     * SpektraLab 期望的不一致（尤其 JPG 的 input_cctf_decoding / scan_film），
     * 缺字段会让画面异常、表现为"参数不生效"。
     *
     * @param isRaw 是否 RAW 源（决定 input_color_space / input_cctf_decoding）
     */
    private fun paramsJson(
        filmId: String, paperId: String, p: FilmParams, isRaw: Boolean,
        imprintText: String = "", imprintDataText: String = "", imprintTextB: String = "",
    ): String {
        val o = JSONObject()
        // ── 基础：输入/输出色彩管线（JPG 必须 sRGB + cctf 解码，否则线性/非线性错乱）
        // 「原图」哨兵值不在胶片目录里，fallback 到基准胶片，避免引擎拿到无效 film_stock 渲染失败。
        o.put("film_stock", if (filmId == FILM_NONE) DEFAULT_FILM else filmId)
        // 「无相纸」哨兵值不在相纸目录里，fallback 到默认相纸，避免引擎拿到无效 print_stock 渲染失败。
        o.put("print_stock", if (paperId == PAPER_NONE) DEFAULT_PAPER else paperId)
        o.put("input_color_space", if (isRaw) "ProPhoto RGB" else "sRGB")
        o.put("input_cctf_decoding", !isRaw)
        o.put("output_color_space", "sRGB")
        // 正片（反转片）走"直接扫描"管线不经相纸，权威 isPositiveStock→scan_film=true（MainActivity 31905）。
        // 权威还有第二个条件：无相纸哨兵（PAPER_SCAN="__scan__"）也走扫描，负片+无相纸同样不经相纸。
        // NicoLink 的「无相纸」哨兵是 PAPER_NONE，等价权威 PAPER_SCAN。
        o.put("scan_film", filmId in positiveStockIds || paperId == PAPER_NONE)
        // 分级预览：拖动时用引擎下限 800（快），松手/最终渲染用 1200（清）。
        // 引擎拒绝 <800，故下限 800；旧代码把 480 抬到 800 导致拖动与最终都是 800，分级失效。
        // 导出走 export() 全分辨率，不受此影响。
        val edge = p.previewLongEdge ?: (p.wire["preview_long_edge"] as? Number)?.toInt() ?: 1200
        o.put("preview_long_edge", edge.coerceAtLeast(800))

        // ── 测光（对齐权威 App 实测行为：新照片默认关闭测光，JPG/RAW 皆然）
        // 源码 defaultLook 本意 RAW 默认 protect_highlights，但 openImage 里 applyLook
        // 在 isRawFile 赋值之前调用（11597 vs 11605），首次导入时 isRawFile=false → 关闭。
        // 实测确认（点"重置当前页"才变保护高光，证实时序 bug）。故默认测光=关闭，
        // 与用户实际看到的 SpektraLab 效果一致。用户在测光 tab 仍可手动开启。
        // 权威 applyParamsTo：auto_exposure=!meter.isEmpty()，method=meter 为空则 balanced。
        val wireMeter = (p.wire["auto_exposure_method"] as? String) ?: ""
        val meter = wireMeter
        o.put("auto_exposure", meter.isNotEmpty())
        o.put("auto_exposure_method", if (meter.isEmpty()) "balanced" else meter)
        o.put("exposure_compensation_ev", p.exposureEv.toDouble())

        // ── SpektraLab 全套默认（逐字段照抄权威 applyParamsTo + 成员变量默认值）
        // 关键：引擎对缺失字段走自己的默认，与 SpektraLab 发的默认不同，故即使 UI 不暴露也全发。
        // per-stock 逻辑（黑白片 grain/色相修正）见文件末尾辅助函数。
        o.put("print_exposure", 1.0)
        o.put("grain_amount", maxOf(0.0, minOf(2.0, ownGrain(filmId))))
        o.put("grain_sublayers_active", !isMonochromeStock(filmId))
        o.put("halation_amount", 1.0)
        o.put("halation_boost_ev", 0.0)
        o.put("halation_scatter_amount", 1.0)
        o.put("halation_scatter_scale", 1.0) // 1 + 11*scatterRadius(默认0)
        o.put("halation_additive", false)    // 权威 halationAmount>1.01；wire 覆盖后按实际值重算（见末尾定稿）
        // 注意：这些字段 JNI 接口要 JSON boolean（实测 'antihalation_removed' expects bool），
        // 虽然权威 Java 内部是 1f/0f 浮点。UI 是滑条（0=关/非0=开），这里映射成 boolean。
        o.put("halation_active", true)
        o.put("antihalation_removed", false)
        o.put("grain_active", true)
        o.put("dir_couplers_active", true)   // 权威恒 true，强度由 amount 旋钮决定（见末尾定稿强制回写）
        o.put("dir_couplers_amount", 1.0)    // 权威 couplersOn 0..2 默认 1；UI 已改为滑条
        o.put("scanner_white_correction", false)
        o.put("glare_active", true)
        o.put("glare_amount", 1.0)
        o.put("scanner_lens_blur", 0.0)
        o.put("halation_headroom_ev", 4.0)   // halationHeadroom=4f
        o.put("density_curve_gamma", 1.0)
        o.put("digital_intermediate", false)
        o.put("output_cctf_encoding", true)  // outputCctf=true
        o.put("hue_correction", wireHueCorrection(filmId))
        o.put("scanner_black_correction", false) // scannerBlack=0f
        o.put("extended_dynamic_range", false)   // edrOn=0f
        o.put("camera_filter", "")
        o.put("scene_latitude_active", false) // highlightRoom=shadowRoom=0 → 关闭
        o.put("scene_latitude_norm", "power")
        o.put("scene_latitude_highlight_knee", 1.0) // wireHighlightKnee() 默认
        o.put("scene_latitude_highlight_room", 0.0)
        o.put("scene_latitude_shadow_knee", -2.0)   // wireShadowKnee() 默认
        o.put("scene_latitude_shadow_room", 0.0)
        o.put("scene_latitude_rolloff", 2.0)        // shadowRolloff=2f
        o.put("scene_latitude_max_lift", 4.0)       // maxLift=4f
        o.put("film_format_mm", 36.0)
        o.put("lens_blur_um", 0.0)
        o.put("geometry_crop_x", 0.0)
        o.put("geometry_crop_y", 0.0)
        o.put("geometry_crop_w", 1.0)
        o.put("geometry_crop_h", 1.0)
        o.put("geometry_rotation_deg", 0.0)  // -geometryRotation(默认0)
        o.put("geometry_quarter_turns", 0)
        o.put("geometry_flip_h", false)
        o.put("geometry_flip_v", false)
        o.put("m_filter_shift", 0.0)
        o.put("y_filter_shift", 0.0)
        o.put("filter_shift_scale", 1.0)
        o.put("preflash_exposure", 0.0)      // preflash*0.04, preflash=0
        // 片边/齿孔：权威默认全发（即使 overscan_active=false，缺字段也会走引擎默认而不同）
        o.put("overscan_active", false)
        o.put("overscan_format", "135")
        o.put("overscan_edge_text", "")
        o.put("overscan_fog", 1.0)           // edgeFog=1f
        o.put("overscan_leaks", 0.0)         // edgeLeaks=0f
        o.put("overscan_frame_number", 0.0)  // frameNumber=0f
        o.put("overscan_mode", "strip")      // edgeMode
        o.put("overscan_gate", "auto")       // edgeGate
        o.put("overscan_holes", "white")     // edgeHoles
        o.put("overscan_carrier", "none")    // edgeCarrier
        o.put("overscan_f_number", 0.0)      // edgeFNumber=0f
        o.put("overscan_camera_seed", 1)     // cameraSeed=1
        o.put("overscan_frame_seed", 1)      // frameSeed=1
        // 日期水印：权威默认全发（dateOn=false 时 text 为空串）。
        // date_imprint_text 取照片 EXIF 拍摄日期（由调用方传入），SpektraLab 风格如 "'26 11 14"。
        o.put("date_imprint_active", false)
        o.put("date_imprint_text", imprintText)
        o.put("date_imprint_style", "lcd")
        o.put("date_imprint_text_b", imprintTextB)
        o.put("date_imprint_data_text", imprintDataText)
        o.put("date_imprint_placement", "frame")
        o.put("date_imprint_corner", "br")
        o.put("date_imprint_inset_x", 3.0)
        o.put("date_imprint_inset_y", 2.4)
        o.put("date_imprint_size", 1.0)
        o.put("date_imprint_ev", 5.5)   // 权威默认 5.5（L546/2435），3.5 是被迁移的旧值

        // ── UI wire 覆盖（用户调过的参数走这里，键为 wire 名）
        p.wire.forEach { (k, v) ->
            when (k) {
                // view 调色通道参数不进 paramsJson —— 由 applyViewAdjust 构建 double[] 调引擎
                in VIEW_PARAM_KEYS -> Unit
                // 预览长边由 FilmParams.previewLongEdge 统一决定（上面已 coerceAtLeast(800)），
                // 旧预设里可能残留该键，不能被原始值覆盖否则引擎拒渲染 <800
                "preview_long_edge" -> Unit
                // hue_correction：权威 wireHueCorrection(stock)=不可信表→0，否则用户值。
                // 这里用 filmId 判定，避免色相表错误的胶片被加上色相修正。
                "hue_correction" -> o.put(k, if (hueFixUnsupported(filmId)) 0.0 else when (v) {
                    is Boolean -> if (v) 1.0 else 0.0
                    is Number -> v.toDouble()
                    else -> 1.0
                })
                // grain_amount：权威 wireGrain = clamp(用户滑块 × ownGrain(stock), 0, 2)
                "grain_amount" -> o.put(k, maxOf(0.0, minOf(2.0,
                    (v as? Number)?.toDouble()?.times(ownGrain(filmId)) ?: ownGrain(filmId))))
                // grain_sublayers_active：权威 !isMonochromeStock（黑白片走单层颗粒）
                "grain_sublayers_active" -> o.put(k, !isMonochromeStock(filmId))
                // 引擎只接受合法测光方法名（balanced/center/…），空串会直接抛异常。
                // 用上面算好的 meter（默认关闭→balanced 占位但 auto_exposure=false），
                // 忽略 wire 里的空串，否则会覆盖成 balanced 占位值。
                "auto_exposure_method" -> o.put(k, if (meter.isEmpty()) "balanced" else meter)
                // auto_exposure 由测光方法是否为空唯一决定（权威无独立开关），忽略 wire 里的值，
                // 否则 UI 曾写入的独立 auto_exposure 会与 method 不一致。
                "auto_exposure" -> Unit
                // 引擎要求 str（white/black）；旧版 UI 曾把它定义成 Boolean 默认值 true，
                // 这里归一化，兼容历史持久化的 Boolean 值。
                "overscan_holes" -> o.put(k, when (v) {
                    is Boolean -> if (v) "white" else "black"
                    is String -> v
                    else -> "white"
                })
                // overscan_edge_text：权威 edgeTextOn 布尔（不直接发引擎）→ q(edgeTextOn ? edgeTextFor(stock) : "")。
                // 我们的 UI 用 Boolean toggle 承载，这里转成字符串文本/空串。
                "overscan_edge_text" -> o.put(k, when (v) {
                    is Boolean -> if (v) edgeTextFor(filmId) else ""
                    is String -> v
                    else -> ""
                })
                // geometry_rotation_deg：权威 f("%.3f", -geometryRotation) 取负（屏幕顺时针为正，引擎相反）
                "geometry_rotation_deg" -> o.put(k, -((v as? Number)?.toDouble() ?: 0.0))
                // geometry_quarter_turns：引擎要求 int（权威 line13182 数字字面量 turns；
                // line13571 的 Integer.toString 是另一套 delta 账本路径，别被误导）
                "geometry_quarter_turns" -> o.put(k, when (v) {
                    is Number -> v.toInt()
                    is String -> v.toIntOrNull() ?: 0
                    else -> 0
                })
                // 这几项引擎 JNI 接口要 JSON boolean（实测传 Double 会报 "... expects bool" 导致渲染失败）。
                // 这里把值归一化成 boolean，并兼容历史 boolean 持久化值。
                // 光晕/颗粒/眩光的 active 已不单独暴露，改由下方按强度滑条统一决定。
                // dir_couplers_active 已从 UI 移除（改为 dir_couplers_amount 滑条，active 恒 true），
                // 旧预设残留的 dir_couplers_active 在此忽略，末尾定稿强制回写 true。
                "antihalation_removed", "digital_intermediate", "output_cctf_encoding" ->
                    o.put(k, when (v) {
                        is Boolean -> v
                        is Number -> v.toDouble() > 0.0
                        else -> false
                    })
                // 旧预设残留的 dir_couplers_active 键忽略（active 恒 true，强度走 dir_couplers_amount）
                "dir_couplers_active" -> Unit
                // 「拍摄数据」开关（我们自己的 UI key）：开→写入 EXIF 拍摄数据，关→空串
                "date_imprint_data_active" -> {
                    val on = when (v) {
                        is Boolean -> v
                        is Number -> v.toDouble() > 0.0
                        else -> false
                    }
                    o.put("date_imprint_data_text", if (on) imprintDataText else "")
                }
                else -> when (v) {
                    is Boolean -> o.put(k, v)
                    is Number -> o.put(k, v)
                    is String -> o.put(k, v)
                }
            }
        }
        // ── 光晕/颗粒/眩光：active 由强度滑条决定（对齐 SpektraLab「一个滑条，0 即关闭」）
        // 强度为 0 → active=false（引擎不计算）；强度>0 → active=true。
        // 必须在 wire 覆盖之后统一写，避免遍历顺序影响。
        o.put("halation_active", o.optDouble("halation_amount", 1.0) > 0.0)
        o.put("grain_active", o.optDouble("grain_amount", 1.0) > 0.0)
        o.put("glare_active", o.optDouble("glare_amount", 1.0) > 0.0)
        // halation_additive：权威 halationAmount>1.01（MainActivity L31916），非独立开关。
        val halationAmount = o.optDouble("halation_amount", 1.0)
        o.put("halation_additive", halationAmount > 1.01)
        // dir_couplers：权威 active 恒 true，强度由 amount 旋钮（0..2，默认1）决定（L31920-31921）。
        // UI 已从 active toggle 改为 amount 滑条；旧预设残留的 dir_couplers_active 一律忽略。
        o.put("dir_couplers_active", true)

        // ── scene_latitude：权威 sceneLatitudeActive()（L26159-26166）+ wire 公式（L26118-26156）
        // NicoLink 高光/暗部保护滑条 0..1（0=关）；权威 highlightRoom 0..3（默认1）。
        // active 由 room>0.01 派生；knee/room 由 wire 公式重算，避免"滑条动了但引擎没开"的死参数。
        val hlRoom = o.optDouble("scene_latitude_highlight_room", 0.0).coerceIn(0.0, 3.0)
        val shRoom = o.optDouble("scene_latitude_shadow_room", 0.0).coerceIn(0.0, 3.0)
        o.put("scene_latitude_active", hlRoom > 0.01 || shRoom > 0.01)
        o.put("scene_latitude_highlight_knee", if (hlRoom <= 0.01) 1.0 else 1.0 - 0.5 * (hlRoom / 3.0))
        o.put("scene_latitude_shadow_knee", if (shRoom <= 0.01) -2.0 else -2.0 + 1.7 * (shRoom / 3.0))
        o.put("scene_latitude_highlight_room", if (hlRoom <= 0.01) 0.0 else 2.9 - (hlRoom / 3.0) * 0.7)
        o.put("scene_latitude_shadow_room", if (shRoom <= 0.01) 0.0 else shRoom)

        // ── film_format_mm：权威 wireFormatMm()（L26104）片边开时由 gate 强制，否则 mm+gate 矛盾
        // （预览可能正常但导出乱飞）。CROP_RATIOS 含 gate→mm 映射（与权威 EDGE_MM 一致）。
        if (o.optBoolean("overscan_active", false)) {
            val gate = o.optString("overscan_format", "135")
            val gateMm = CROP_RATIOS.firstOrNull { it.gate == gate && it.filmMm > 0f }?.filmMm ?: 36.0f
            if (gateMm > 0f) o.put("film_format_mm", gateMm.toDouble())
        }

        // ── EDR：权威 edrOn>0.5 && paper!="ilford_multigrade_iv_rc"（L31935-31941），
        // 否则 spk_open 失败。用户开了 EDR 但选 ilford 相纸时必须强制关。
        if (paperId == "ilford_multigrade_iv_rc") o.put("extended_dynamic_range", false)

        // ── 胶片/相纸最后定稿（UI 不得覆盖，保持一致）
        o.put("film_stock", if (filmId == FILM_NONE) DEFAULT_FILM else filmId)
        o.put("print_stock", if (paperId == PAPER_NONE) DEFAULT_PAPER else paperId)
        // 色彩管线：权威 applyParamsTo 完全按 isRaw 硬编码，UI 不可覆盖。
        // 强制回写以确保 RAW 一定是 ProPhoto RGB、JPG 一定是 sRGB（与 SpektraLab 一致）。
        o.put("input_color_space", if (isRaw) "ProPhoto RGB" else "sRGB")
        o.put("input_cctf_decoding", !isRaw)
        o.put("output_color_space", "sRGB")
        val json = o.toString()
        // 诊断：落盘发出的完整参数（含类型），用于定位 hue_correction 等字段类型错误
        DiagLog.write("params-sent.json", json)
        DiagLog.mark("params: built ${json.length}B, isRaw=$isRaw, hue_correction=${o.opt("hue_correction")}(${o.opt("hue_correction")?.javaClass?.simpleName}), scan_film=${o.opt("scan_film")}")
        return json
    }

    /**
     * 应用"光学"tab 的渲染后调色（SpektraLab pushViewAdjust/pushViewCurves）。
     *
     * 权威 line9844 注释明确：光学参数从不进引擎 params，而是渲染后由
     * setViewAdjust(double[25]) + setViewCurves(double[80]) 填充 g_adjust，
     * 被 copyArgbRange 消费。这是"光学全部无效"的根因——之前我们把这些字段
     * 塞进 paramsJson，引擎根本不认。
     *
     * setViewAdjust 是全局静态的，会被所有后续 copyArgbRange 应用，所以每次
     * 渲染前都要重设（含默认值），切换照片/恢复原图靠 UI 传默认 wire 复位。
     */
    private fun applyViewAdjust(p: FilmParams) {
        try {
            val w = p.wire
            fun f(key: String): Double = (w[key] as? Number)?.toDouble() ?: 0.0
            // 权威 viewAdjustValues()（L19654-19686）是 double[66]：
            // 0-24 基础 + 25-48 HSL 八段 + 49-52 锐化/半径/蒙版/纹理 + 53-65 局部 dodge/burn。
            // 旧代码只发 25 → HSL/锐化/纹理/局部调整全缺（引擎读越界或忽略）。UI 暂无这些控件，
            // 填中性默认（spotOn=0 关闭局部调整），但预设若带这些键也能还原。
            val adj = DoubleArray(66)
            adj[0] = f("viewTemperature")
            adj[1] = f("viewTint")
            adj[2] = f("viewExposure")
            adj[3] = f("viewContrast")
            adj[4] = f("viewBrightness")
            adj[5] = f("viewSaturation")
            adj[6] = f("viewHighlights")
            adj[7] = f("viewShadows")
            adj[8] = f("viewBlack")
            adj[9] = f("viewWhite")
            adj[10] = f("viewVignette")
            adj[11] = 50.0 // VIEW_VIGNETTE_MID 常量（权威 line1452）
            // [12-23] 4 区间颜色轮：每区间 hue/sat/lum
            @Suppress("UNCHECKED_CAST")
            val wheels = (w["viewColorWheels"] as? List<ColorWheelState>) ?: emptyList()
            val lumBoost = f("cbLum") // 分级明亮度滑块，叠加到每个区间的 lum
            for (i in 0 until 4) {
                val cw = wheels.getOrNull(i) ?: ColorWheelState()
                adj[12 + i * 3] = cw.hue.toDouble()
                adj[13 + i * 3] = cw.saturation.toDouble()
                adj[14 + i * 3] = cw.luminance.toDouble() + lumBoost
            }
            adj[24] = f("viewVibrance")
            // 25-48 HSL 八段：色相区间 红0/橙30/黄60/绿120/青180/蓝240/紫280/品红320
            for (i in 0 until 8) {
                adj[25 + i] = f("viewHslHue$i")
                adj[33 + i] = f("viewHslSat$i")
                adj[41 + i] = f("viewHslLum$i")
            }
            // 49-52 锐化/半径/蒙版/纹理（半径默认 1.0 中性；sharpen=0 时无影响）
            adj[49] = f("viewSharpen")
            adj[50] = if (w.containsKey("viewSharpenRadius")) f("viewSharpenRadius") else 1.0
            adj[51] = f("viewSharpenMask")
            adj[52] = f("viewTexture")
            // 53-65 局部 dodge/burn（步长3布局：53 spotOn, 54 x, 57 y, 60 r, 63 ev；其余槽位空）
            adj[53] = f("viewSpotOn")
            adj[54] = f("viewSpotX")
            adj[57] = f("viewSpotY")
            adj[60] = f("viewSpotR")
            adj[63] = f("viewSpotEv")
            SpektraLabEngine.setViewAdjust(adj)

            // 曲线：5 通道 × 8 控制点 × (x,y)，不足槽位 x=-1,y=0（权威 pushViewCurves line5153）
            @Suppress("UNCHECKED_CAST")
            val curves = (w["viewCurves"] as? List<List<CurvePoint>>) ?: emptyList()
            // 权威 pushViewCurves：不足槽位 packed[at]=-1, packed[at+1]=0
            val packed = DoubleArray(80) { i -> if (i % 2 == 0) -1.0 else 0.0 }
            for (ch in 0 until 5) {
                val pts = curves.getOrNull(ch) ?: listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
                val base = ch * 16
                for (i in 0 until minOf(8, pts.size)) {
                    packed[base + i * 2] = pts[i].x.toDouble()
                    packed[base + i * 2 + 1] = pts[i].y.toDouble()
                }
            }
            SpektraLabEngine.setViewCurves(packed)
            DiagLog.mark("view: applied exp=${adj[2]} con=${adj[3]} sat=${adj[5]} vig=${adj[10]} " +
                "cw0[h=${adj[12]},s=${adj[13]},l=${adj[14]}] cw1[h=${adj[15]},s=${adj[16]},l=${adj[17]}] " +
                "vib=${adj[24]} curves0pts=${curves.getOrNull(0)?.size ?: 0} curves1pts=${curves.getOrNull(1)?.size ?: 0}")
        } catch (t: Throwable) {
            DiagLog.exception("view-adjust", t)
        }
    }

    // ── 与 SpektraLab 权威源码一致的 per-stock 逻辑（MainActivity.java）
    /** 黑白胶片（4 种）：grain 走单层模型、色相修正表不可信 */
    private fun isMonochromeStock(stock: String): Boolean = stock in setOf(
        "kodak_tri_x_400", "ilford_hp5_plus_400", "kodak_tmax_100", "fujifilm_neopan_acros_100_ii"
    )

    /** 各黑白胶片自身的颗粒强度（ownGrain） */
    private fun ownGrain(stock: String): Double = when (stock) {
        "kodak_tri_x_400" -> 1.70
        "ilford_hp5_plus_400" -> 1.49
        "kodak_tmax_100" -> 0.74
        "fujifilm_neopan_acros_100_ii" -> 0.64
        else -> 1.0
    }

    /** 这些胶片的色相表不可信，权威强制 hue_correction=0 */
    private fun hueFixUnsupported(stock: String): Boolean = stock in setOf(
        "kodak_vision3_200t", "kodak_vision3_250d", "kodak_vision3_500t",
        "kodak_vision3_50d", "cinestill_800t", "kodak_verita_200d", "fujifilm_pro_400h"
    )

    /** 上线的 hue_correction：不可信表→0，否则用权威默认 1.0 */
    private fun wireHueCorrection(stock: String): Double = if (hueFixUnsupported(stock)) 0.0 else 1.0

    /**
     * 片边文字：权威 edgeTextFor(stock)（L26279-26300）——从胶片目录显示名派生，
     * 只保留 ASCII 可打印字符（去掉"· 中文"后缀），空则 KODAK，最后转大写。
     */
    private fun edgeTextFor(stock: String): String {
        val name = filmNames[stock] ?: stock
        val ascii = name.filter { it.code in 0x20..0x7E }.trim()
        return (ascii.ifEmpty { "KODAK" }).uppercase()
    }

    /**
     * 渲染预览，返回 PNG 字节。
     * 会话复用：同一张照片首次解码后缓存 Session，参数变化只 setParams + 重渲染，
     * 不再重新解码 24MB RAW（这是"卡"的根源）。引擎内部 RENDER_LOCK 保证单线程，
     * 这里再套 synchronized 防止并发调用。
     */
    fun renderPreview(
        context: Context,
        photoUri: Uri,
        filmId: String,
        paperId: String,
        params: FilmParams,
    ): ByteArray = synchronized(lock) {
        val e = ensureEngine(context)
        val key = photoUri.toString()
        DiagLog.mark("render: begin uri=$key film=$filmId paper=$paperId")
        try {
            val raw = isRaw(context, photoUri)
            DiagLog.mark("render: isRaw=$raw")
            val json = paramsJson(filmId, paperId, params, raw, imprintText(context, photoUri), imprintDataText(context, photoUri))
            applyViewAdjust(params) // 光学调色层：setViewAdjust/setViewCurves（渲染后处理）

            // 命中缓存：复用 session，仅更新参数；未命中：打开源并缓存（只解码一次）
            val entry: SessionEntry
            val session: SpektraLabEngine.Session
            val cachedEntry = sessions[key]
            if (cachedEntry != null && cachedEntry.raw == raw) {
                DiagLog.mark("render: session CACHE HIT, setParams only")
                session = cachedEntry.session
                session.setParams(json)
                entry = cachedEntry
            } else {
                // raw 类型变了（同 uri 不太可能），关掉旧会话再重建
                cachedEntry?.let { closeEntry(it) }
                val (opened, tempFile) = openSource(context, e, photoUri, json, raw)
                session = opened
                entry = SessionEntry(session, raw, tempFile)
                sessions[key] = entry
                evictOldestSession()
                DiagLog.mark("render: session OPENED + cached (size=${sessions.size})")
            }

            // SpektraLab 预览用 "live" tier（最快，滑块实时响应），不是 "preview"
            DiagLog.mark("render: renderBegin(live) call")
            val dims = session.renderBegin("live")
            DiagLog.mark("render: renderBegin returned dims=${dims.contentToString()}")
            val w = dims[0]; val h = dims[1]
            if (w <= 0 || h <= 0) {
                throw IllegalStateException("renderBegin 返回无效尺寸: ${dims.contentToString()}")
            }
            val argb = IntArray(w * h)
            DiagLog.mark("render: copyArgbRange call ${w}x${h}")
            val copied = session.copyArgbRange(argb, 0, h)
            DiagLog.mark("render: copyArgbRange returned $copied / expected ${w * h}")
            if (copied != w * h) {
                throw IllegalStateException("copyArgbRange 返回 $copied，期望 ${w * h}")
            }
            DiagLog.mark("render: renderEnd call")
            session.renderEnd()
            DiagLog.mark("render: renderEnd OK")

            val bmp = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888)
            val out = ByteArrayOutputStream()
            // JPEG 编码比 PNG 快很多（预览图不透明，无损视觉差异）；saveEditThumb 存 .jpg 正好兼容
            bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
            bmp.recycle()
            DiagLog.mark("render: JPEG ${out.size()}B, DONE (cached=${sessions.size})")
            Log.i(TAG, "renderPreview OK ${w}x${h} jpeg=${out.size()}B")
            out.toByteArray()
        } catch (t: Throwable) {
            DiagLog.exception("render", t)
            // 会话可能已损坏：移除缓存，下次重建
            sessions.remove(key)?.let { closeEntry(it) }
            throw t
        }
    }

    /** 释放会话（非破坏性编辑复位/照片切换时用），photoUri 为 null 释放全部 */
    fun release(photoUri: String? = null) = synchronized(lock) {
        if (photoUri == null) {
            sessions.values.forEach { closeEntry(it) }
            sessions.clear()
            DiagLog.mark("release: all sessions closed")
        } else {
            sessions.remove(photoUri)?.let { closeEntry(it) }
            DiagLog.mark("release: closed $photoUri, remaining=${sessions.size}")
        }
    }

    /** 全分辨率导出到 DCIM/NicoLink（媒体库，免存储权限），返回显示路径。format=jpg/tiff/dng */
    fun export(
        context: Context,
        photoUri: Uri,
        filmId: String,
        paperId: String,
        params: FilmParams,
        format: String = "jpg",
        quality: Int = 95,
    ): String = synchronized(lock) {
        val e = ensureEngine(context)
        val fmt = format.lowercase()
        val (ext, mime) = when (fmt) {
            "tiff" -> "tiff" to "image/tiff"
            "dng" -> "dng" to "image/x-adobe-dng"
            else -> "jpg" to "image/jpeg"
        }
        DiagLog.mark("export: begin film=$filmId paper=$paperId format=$fmt quality=$quality")
        var session: SpektraLabEngine.Session? = null
        var tempFile: java.io.File? = null
        val name = "nicolink_${filmId}_${System.currentTimeMillis()}.$ext"
        try {
            val raw = isRaw(context, photoUri)
            val json = paramsJson(filmId, paperId, params, raw, imprintText(context, photoUri), imprintDataText(context, photoUri))
            applyViewAdjust(params) // 光学调色层：导出也要带上渲染后调色
            val (opened, tmp) = openSource(context, e, photoUri, json, raw)
            session = opened
            tempFile = tmp
            DiagLog.mark("export: session opened, calling export($fmt, full)")

            // 对齐权威 exportImage：导出前 pin 测光 EV，保证导出亮度与预览一致。
            // 权威逻辑（MainActivity.java 15595-15649）：测光开时 pin，测光关时 clear。
            // EV 来自会话 solve("both").solved_params.exposure_compensation_ev。
            pinExportMeter(session, json)

            // 引擎 JNI 只能写真实文件路径，先导出到 App 私有 cache 临时文件
            val tmpDir = File(context.cacheDir, "export_tmp")
            tmpDir.mkdirs()
            val tmpFile = File(tmpDir, name)
            val info = when (fmt) {
                "tiff" -> session.exportTiff(tmpFile.absolutePath, "full")
                "dng" -> session.exportDng(tmpFile.absolutePath, "full")
                // 权威 exportImage：exportJpeg(path, 95, 512, "full")，stripRows=512 分条降内存峰值
                else -> session.exportJpeg(tmpFile.absolutePath, quality.coerceIn(1, 100), 512, "full")
            }
            DiagLog.mark("export: $fmt returned ${info.contentToString()} tmp=${tmpFile.absolutePath}")
            runCatching { File("${tmpFile.absolutePath}.prog").delete() } // 引擎 JNI 内部进度文件

            val displayPath: String
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+：MediaStore 写公共 DCIM/NicoLink，无需任何存储权限
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/NicoLink")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("MediaStore insert failed")
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        tmpFile.inputStream().use { it.copyTo(out) }
                    } ?: throw IllegalStateException("openOutputStream failed")
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (t: Throwable) {
                    resolver.delete(uri, null, null) // 写入失败则删掉待定条目
                    throw t
                }
                displayPath = "${Environment.DIRECTORY_DCIM}/NicoLink/$name"
            } else {
                // Android 9-：RELATIVE_PATH 不可用，退回 App 专属目录（此时需要旧权限，但本 minSdk24 设备极少）
                val outDir = File(context.getExternalFilesDir("NicoLink"), "exports")
                outDir.mkdirs()
                val dest = File(outDir, name)
                tmpFile.copyTo(dest, overwrite = true)
                displayPath = dest.absolutePath
            }
            runCatching { tmpFile.delete() } // 清理临时文件
            DiagLog.mark("export: DONE path=$displayPath")
            Log.i(TAG, "export $fmt OK ${info.contentToString()} path=$displayPath")
            displayPath
        } catch (t: Throwable) {
            DiagLog.exception("export", t)
            throw t
        } finally {
            try { session?.close() } finally { tempFile?.delete() }
            DiagLog.mark("export: finally closed")
        }
    }

    /**
     * 对齐权威 exportImage 的测光 EV pin（MainActivity.java 15595-15649）：
     * - 测光开（auto_exposure=true 且 method 非空）→ solve("both") 读
     *   solved_params.exposure_compensation_ev，sane(!NaN && |ev|<=8) 则 pin，否则回退 0 并 pin；
     * - 测光关 → clearPinAutoExposureEv，让引擎自己决定。
     *
     * 目的：live tier 预览测出的 EV 与 full tier 导出可能不同（权威注释：full decode 上
     * meter 偶尔 clamp 导致整片白）。显式 pin 预览算出的 EV，保证导出与预览亮度一致。
     */
    private fun pinExportMeter(session: SpektraLabEngine.Session, json: String) {
        try {
            val params = JSONObject(json)
            val meter = params.optString("auto_exposure_method", "")
            val autoExposure = params.optBoolean("auto_exposure", false)
            val meteringOn = autoExposure && meter.isNotEmpty()
            if (!meteringOn) {
                session.clearPinAutoExposureEv()
                DiagLog.mark("export-meter: cleared (autoExposure=$autoExposure meter='$meter')")
                return
            }
            var ev = Double.NaN
            try {
                val solved = JSONObject(session.solve("both"))
                val sp = solved.optJSONObject("solved_params")
                val by = solved.optJSONObject("exposure_ev_by_method")
                // 优先 solved_params.exposure_compensation_ev（引擎实际应用值）
                if (sp != null && sp.has("exposure_compensation_ev")) {
                    ev = sp.optDouble("exposure_compensation_ev", Double.NaN)
                }
                // 回退：exposure_ev_by_method[meter]
                if (ev.isNaN() && by != null && by.has(meter)) {
                    ev = by.optDouble(meter, Double.NaN)
                }
            } catch (t: Throwable) {
                DiagLog.mark("export-meter: solve failed ${t.javaClass.simpleName}: ${t.message}")
            }
            val sane = !ev.isNaN() && kotlin.math.abs(ev) <= 8.0
            if (!sane) ev = 0.0
            session.pinAutoExposureEv(ev)
            DiagLog.mark("export-meter: method=$meter ev=$ev sane=$sane pinned")
        } catch (t: Throwable) {
            DiagLog.exception("export-meter", t)
        }
    }

    /**
     * 打开输入流。App 专属目录的照片是 file:// URI，ContentResolver 对它 openInputStream
     * 会 EACCES（file:// 不经 content provider）；直接走 File。content://（MediaStore）才用
     * ContentResolver。
     */
    private fun openInput(context: Context, uri: Uri): java.io.InputStream {
        if (uri.scheme == "file") {
            val path = uri.path ?: throw java.io.FileNotFoundException("file URI 无 path: $uri")
            return File(path).inputStream()
        }
        return context.contentResolver.openInputStream(uri)
            ?: throw java.io.FileNotFoundException("无法打开: $uri")
    }

    /** 判断是 RAW（扩展名优先，mime 兜底），RAW 走 openRawSession，JPG 走 openSession */
    private fun isRaw(context: Context, uri: Uri): Boolean {
        // file://（App 专属目录）按扩展名判断；ContentResolver.getType 对 file:// 返回 null
        val name = if (uri.scheme == "file") (uri.path?.let { File(it).name } ?: "").lowercase() else ""
        val rawExts = listOf("dng", "nef", "nrw", "arw", "srf", "sr2", "cr2", "cr3", "crw", "raf", "orf", "rw2", "pef")
        if (rawExts.any { name.endsWith(".$it") }) return true
        val mime = context.contentResolver.getType(uri)?.lowercase() ?: ""
        return mime.contains("dng") || mime.contains("nef") || mime.contains("arw") ||
            mime.contains("cr2") || mime.contains("cr3") || mime.contains("raf") ||
            mime.contains("raw") || mime.contains("heic") || mime.contains("heif")
    }

    /**
     * 读取照片拍摄日期，格式化成 SpektraLab 风格 "'26 11 14"（年两位/月/日）。
     * 优先 EXIF DateTimeOriginal / DateTimeDigitized / DateTime，失败回退到文件修改时间。
     * 这是日期水印 date_imprint_text 的内容来源——没有它，开启水印也印不出任何文字。
     */
    fun imprintText(context: Context, uri: Uri): String {
        var raw: String? = null
        try {
            openInput(context, uri).use { input ->
                val exif = android.media.ExifInterface(input)
                raw = exif.getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(android.media.ExifInterface.TAG_DATETIME_DIGITIZED)
                    ?: exif.getAttribute(android.media.ExifInterface.TAG_DATETIME)
            }
        } catch (t: Throwable) {
            DiagLog.mark("imprintText: EXIF read failed $t")
        }
        val (yy, mm, dd) = if (raw != null) {
            // EXIF 格式 "2026:11:14 10:30:00"
            val p = raw!!.split(" ")
            val d = p.getOrNull(0)?.split(":")
            Triple(d?.getOrNull(0), d?.getOrNull(1), d?.getOrNull(2))
        } else {
            val cal = java.util.Calendar.getInstance().apply {
                // 回退：文件 lastModified（file:// 直接取，content:// 用当前时间兜底）
                val ms = if (uri.scheme == "file") (uri.path?.let { File(it).lastModified() } ?: 0L) else 0L
                if (ms > 0) timeInMillis = ms
            }
            Triple(
                cal.get(java.util.Calendar.YEAR).toString(),
                (cal.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0'),
                cal.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0'),
            )
        }
        val y2 = yy?.takeLast(2)?.padStart(2, '0') ?: "00"
        return "'$y2 ${mm?.padStart(2, '0') ?: "00"} ${dd?.padStart(2, '0') ?: "00"}"
    }

    /**
     * 读取 EXIF 拍摄参数，格式化成 SpektraLab「拍摄数据」风格（如 "50mm f/2.8 1/125s ISO100"）。
     * 供 date_imprint_data_text 使用；无 EXIF 返回空串。
     */
    fun imprintDataText(context: Context, uri: Uri): String {
        return try {
            openInput(context, uri).use { input ->
                val exif = android.media.ExifInterface(input)
                val parts = mutableListOf<String>()
                // 焦距可能是 "175/10" 这种分数，转成小数
                exif.getAttribute(android.media.ExifInterface.TAG_FOCAL_LENGTH)?.let { raw ->
                    val mm = parseRational(raw)
                    if (mm != null) parts.add("${if (mm % 1f == 0f) mm.toInt().toString() else String.format("%.1f", mm)}mm")
                }
                exif.getAttribute(android.media.ExifInterface.TAG_F_NUMBER)?.let { parts.add("f/$it") }
                // 曝光时间 "0.0333" 转成 "1/30s" 更易读
                exif.getAttribute(android.media.ExifInterface.TAG_EXPOSURE_TIME)?.let { raw ->
                    val sec = raw.toFloatOrNull()
                    if (sec != null && sec > 0) {
                        parts.add(if (sec >= 1f) "${sec.toInt()}s" else "1/${(1f / sec).toInt()}s")
                    }
                }
                exif.getAttribute("ISOSpeedRatings")?.let { parts.add("ISO$it") }
                parts.joinToString(" ")
            }
        } catch (t: Throwable) {
            DiagLog.mark("imprintDataText: EXIF read failed $t")
            ""
        }
    }

    /** 解析 EXIF 有理数（如 "175/10" 或 "3.5"）为浮点 */
    private fun parseRational(raw: String): Float? = try {
        if (raw.contains("/")) {
            val p = raw.split("/")
            p[0].toFloat() / p[1].toFloat()
        } else raw.toFloat()
    } catch (_: Throwable) { null }

    /** 打开源：RAW 经 LibRaw，JPG 解码成 RGB float32 direct buffer */
    private fun openSource(
        context: Context,
        e: SpektraLabEngine,
        uri: Uri,
        json: String,
        raw: Boolean,
    ): Pair<SpektraLabEngine.Session, java.io.File?> {
        if (raw) {
            // LibRaw 需要文件系统路径；content:// 先复制到 cache
            val tmp = File(context.cacheDir, "source_${System.nanoTime()}_${java.util.UUID.randomUUID().toString().take(8)}")
            try {
                openInput(context, uri).use { input ->
                    tmp.outputStream().use { out -> input.copyTo(out) }
                }
                DiagLog.mark("openSource: RAW copied size=${tmp.length()} path=${tmp.absolutePath}")
                DiagLog.mark("openSource: openRawSession call")
                val s = e.openRawSession(tmp.absolutePath, json)
                DiagLog.mark("openSource: openRawSession OK")
                Log.i(TAG, "openRawSession path=${tmp.absolutePath}")
                // 临时文件随会话一起缓存，会话关闭时删除（见 closeEntry）
                return s to tmp
            } catch (t: Throwable) {
                tmp.delete()
                throw t
            }
        }

        // JPG/PNG：解码缩放后转 RGB float32 little-endian
        val source = BitmapFactory.decodeStream(openInput(context, uri))
            ?: throw IllegalArgumentException("无法解码图片: $uri")
        DiagLog.mark("openSource: JPG decoded ${source.width}x${source.height}")
        val maxDim = 800 // 预览输入上限（=引擎预览长边下限），控制解码耗时与内存
        val scale = (maxDim.toFloat() / maxOf(source.width, source.height)).coerceAtMost(1f)
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(source, w, h, true) else source
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val buf = ByteBuffer.allocateDirect(w * h * 3 * 4).order(ByteOrder.nativeOrder())
        for (argb in pixels) {
            buf.putFloat(((argb shr 16) and 0xFF) / 255f)
            buf.putFloat(((argb shr 8) and 0xFF) / 255f)
            buf.putFloat((argb and 0xFF) / 255f)
        }
        buf.position(0)
        if (bmp !== source) bmp.recycle()
        source.recycle()

        DiagLog.mark("openSource: JPG ${w}x${h} buf=${buf.capacity()}B, openSession call")
        Log.i(TAG, "openSession ${w}x${h} ch=3")
        val session = e.openSession(buf, w, h, 3)
        DiagLog.mark("openSource: openSession OK, setParams call")
        session.setParams(json)
        DiagLog.mark("openSource: setParams OK")
        return session to null
    }

    /** 关闭会话并删除其 RAW 临时文件（统一收口，避免遗漏） */
    private fun closeEntry(entry: SessionEntry) {
        try { entry.session.close() } finally { entry.tempFile?.delete() }
    }

    /** 把 assets 里的资源解压到 filesDir，native 只能读文件系统路径。 */
    private fun ensureResources(context: Context): File {
        val out = File(context.filesDir, ASSET_DIR)
        if (!out.exists() || out.listFiles().isNullOrEmpty()) {
            copyAssets(context, ASSET_DIR, out)
        }
        return out
    }

    private fun copyAssets(context: Context, from: String, to: File) {
        val entries = context.assets.list(from) ?: return
        to.mkdirs()
        for (name in entries) {
            val src = "$from/$name"
            val dst = File(to, name)
            val children = context.assets.list(src)
            if (!children.isNullOrEmpty()) {
                copyAssets(context, src, dst)
            } else {
                context.assets.open(src).use { input ->
                    dst.outputStream().use { out -> input.copyTo(out) }
                }
            }
        }
    }
}
