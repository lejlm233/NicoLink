package com.filmlens.nicolink.film

/**
 * 显影参数目录 —— 数据驱动，对齐 SpektraLab 的 6 个 tab。
 *
 * 每个 [ParamSpec] 的 key 就是引擎 wire 字段名（SpektraLab MainActivity.java
 * delta 构造里的 key），render 时由 FilmEngine.paramsJson 原样透传给 native。
 */

/** 参数控件类型 */
enum class ParamKind {
    /** 浮点滑块 */
    SLIDER,
    /** 开关 */
    TOGGLE,
    /** 枚举单选 */
    CHOICE,
    /** 可交互曲线编辑器（5 通道，走 setViewCurves） */
    CURVE,
    /** 颜色轮（4 区间，走 setViewAdjust 的 cb* 槽位） */
    COLOR_WHEEL,
    /** 裁切提案面板（比例卡片 + 可拖拽框 + 应用；提案式不实时渲染） */
    CROP_PROPOSAL,
    /** 90° 旋转动作按钮组（↺/↻/归零） */
    ROTATE_QUARTER,
    /** 物理尺幅卡片条（10 片门横滚，点卡片联动 film_format_mm + overscan_format） */
    FORMAT_CARD,
}

/** 曲线控制点（归一化 0..1） */
data class CurvePoint(val x: Float, val y: Float)

/** 颜色轮状态：hue=色相角度(0..360)，saturation=半径(0..1)，luminance=明亮度(-1..1) */
data class ColorWheelState(val hue: Float = 0f, val saturation: Float = 0f, val luminance: Float = 0f)

/** 裁切框（归一化 0..1，x/y 左上，w/h 宽高） */
data class CropRect(val x: Float = 0f, val y: Float = 0f, val w: Float = 1f, val h: Float = 1f)

/** 裁切比例卡片：label 显示名，ratio=宽/高（0=原始不裁）；gate=对应片门格式（空=不改），filmMm=对应尺幅（0=不改） */
data class CropRatio(val label: String, val ratio: Float, val gate: String = "", val filmMm: Float = 0f)

/**
 * 裁切比例清单：原始 + 权威 10 片门(EDGE_LABELS/EDGE_IDS/EDGE_MM) + 3 竖幅(PORTRAIT_RATIOS/PORTRAIT_GATES)
 *
 * 【决定性】gate 必须是权威精确 ID，不能用笼统的 "135"/"120"/""：
 * 开片边时引擎要求画面宽高比 == overscan_format 对应 gate 的宽高比（EDGE_RATIOS），
 * 不匹配会 `spk_render failed (-5): overscan: frame is not the <gate>'s shape` → 渲染失败回退原图。
 * 权威 commitPendingCrop（MainActivity.java:6309）gate 同步不依赖 overscanOn：
 * "crop first, then turn rebate on → gate kept 135 for 4:3 picture → render failed"。
 */
val CROP_RATIOS: List<CropRatio> = listOf(
    CropRatio("原始", 0f),
    // 10 横幅片门：gate=EDGE_IDS，ratio=EDGE_RATIOS，filmMm=EDGE_MM
    CropRatio("135全画幅", 36.25f / 24.3f, "135", 36f),
    CropRatio("135半格", 24f / 18f, "135_half", 24f),
    CropRatio("135 XPan", 65f / 24f, "135_xpan", 65f),
    CropRatio("645", 56f / 41.5f, "120_645", 56f),
    CropRatio("6×6", 1f, "120_6x6", 56f),
    CropRatio("6×7", 69.5f / 56f, "120_6x7", 69.5f),
    CropRatio("6×8", 76f / 56f, "120_6x8", 76f),
    CropRatio("6×9", 84f / 56f, "120_6x9", 84f),
    CropRatio("6×12", 112f / 56f, "120_6x12", 112f),
    CropRatio("6×17", 168f / 56f, "120_6x17", 168f),
    // 3 竖幅：gate=PORTRAIT_GATES，filmMm 取对应横幅 gate 的 EDGE_MM
    CropRatio("2:3", 2f / 3f, "135", 36f),
    CropRatio("3:4", 3f / 4f, "135_half", 24f),
    CropRatio("4:5", 4f / 5f, "120_6x7", 69.5f),
)

/** 物理尺幅卡片（权威 EDGE_LABELS/EDGE_IDS/EDGE_MM）：label 显示名，mm 物理尺幅，id 片门格式 */
data class FormatCard(val label: String, val mm: Float, val id: String)

/** 尺度页 10 片门卡片条，对齐权威 buildFormatPanel */
val FORMAT_CARDS: List<FormatCard> = listOf(
    FormatCard("135全画幅", 36f, "135"),
    FormatCard("135半格", 24f, "135_half"),
    FormatCard("135 XPan", 65f, "135_xpan"),
    FormatCard("645", 56f, "120_645"),
    FormatCard("6×6", 56f, "120_6x6"),
    FormatCard("6×7", 69.5f, "120_6x7"),
    FormatCard("6×8", 76f, "120_6x8"),
    FormatCard("6×9", 84f, "120_6x9"),
    FormatCard("6×12", 112f, "120_6x12"),
    FormatCard("6×17", 168f, "120_6x17"),
)

/**
 * @param key      wire 字段名
 * @param label    中文显示名
 * @param short    短标签（旋钮字母，仅展示用）
 * @param kind     控件类型
 * @param def      默认值
 * @param min      滑块最小 / 枚举索引最小
 * @param max      滑块最大 / 枚举索引最大
 * @param step     滑块步进
 * @param note     中文说明（滑动时展示）
 * @param choices  枚举选项 display 名（kind=CHOICE 时用）
 * @param choiceValues 枚举选项对应的 wire 值（kind=CHOICE 时用）
 */
data class ParamSpec(
    val key: String,
    val label: String,
    val short: String = "",
    val kind: ParamKind,
    val def: Any,
    val min: Float = 0f,
    val max: Float = 1f,
    val step: Float = 0.01f,
    val note: String = "",
    val choices: List<String> = emptyList(),
    val choiceValues: List<Any> = emptyList(),
    val scale: Float = 1f, // UI 值 → wire 值的倍率（如 0.04）
    val format: String = "%.2f",
    /** 属于哪个子页（对应 ParamTab.subTabs 的索引）；-1=所有子页都显示 */
    val subTab: Int = -1,
    /** true=该参数走 view 调色通道（setViewAdjust/setViewCurves），不进 paramsJson wire */
    val isView: Boolean = false,
    /** true=专业参数，仅在「专业模式」开启时显示（普通模式隐藏，降低认知负担） */
    val advanced: Boolean = false,
)

/** 默认曲线：5 通道，每通道 [(0,0),(1,1)] 两点 */
fun defaultCurves(): List<List<CurvePoint>> = List(5) { listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)) }

/** 默认颜色轮：4 区间全 0 */
fun defaultColorWheels(): List<ColorWheelState> = List(4) { ColorWheelState() }

/** 一个 tab 的定义 */
data class ParamTab(
    val index: Int,
    val label: String,
    val subTabs: List<String> = emptyList(),
    val params: List<ParamSpec> = emptyList(),
)

private fun slider(
    key: String, label: String, short: String, def: Float,
    min: Float, max: Float, step: Float, note: String,
    scale: Float = 1f, format: String = "%.2f",
    subTab: Int = -1, isView: Boolean = false,
) = ParamSpec(key, label, short, ParamKind.SLIDER, def, min, max, step, note, scale = scale, format = format,
    subTab = subTab, isView = isView)

private fun toggle(
    key: String, label: String, short: String, def: Boolean, note: String = "",
    subTab: Int = -1,
) = ParamSpec(key, label, short, ParamKind.TOGGLE, def, note = note, subTab = subTab)

private fun choice(
    key: String, label: String, def: Any, choices: List<String>, values: List<Any>, note: String = "",
    subTab: Int = -1,
) = ParamSpec(key, label, "", ParamKind.CHOICE, def, choices = choices, choiceValues = values, note = note, subTab = subTab)

/** 测光方法（胶片 tab 测光面板） */
val METER_CHOICES = listOf("关闭", "平衡", "保护高光", "保留暗部", "中心", "中心加权", "平均", "中位数")
val METER_VALUES = listOf("", "balanced", "protect_highlights", "protect_shadows", "center", "center_weighted", "average", "median")

/** 配光（冲印 tab） */
val PRINTING_CHOICES = listOf("自动", "暖调", "冷调")
val PRINTING_VALUES = listOf("", "warm", "cool")

/** 6 个 tab 的完整参数目录 */
val PARAM_TABS: List<ParamTab> = listOf(
    // ── 0 胶片：品牌分类 + 测光 + 相纸（胶片/相纸选择在 DevelopScreen 单独处理；这里放测光）
    ParamTab(
        index = 0,
        label = "胶片",
        params = listOf(
            choice("auto_exposure_method", "测光方法", "", METER_CHOICES, METER_VALUES,
                "自动曝光算法；关闭则不自动测光"),
            slider("exposure_compensation_ev", "曝光补偿", "EV", 0f, -3f, 3f, 0.1f,
                "在自动曝光基础上叠加的曝光补偿", format = "%.1f"),
        ),
    ),
    // ── 1 冲印：26 旋钮 + 顶部开关（对齐 SpektraLab 冲印 tab）
    ParamTab(
        index = 1,
        label = "冲印",
        params = listOf(
            slider("print_exposure", "放大曝光", "E", 1f, 0.25f, 4f, 0.05f, "越右越暗；1.0 为中性"),
            // 对齐 SpektraLab 冲印 E/H/S 子页：高光保护/暗部保护（引擎 scene_latitude_* 字段，重算胶片）
            slider("scene_latitude_highlight_room", "高光保护", "H", 0f, 0f, 1f, 0.01f, "把高光折进相纸肩部；拉得越高压得越狠"),
            slider("scene_latitude_shadow_room", "暗部保护", "S", 0f, 0f, 1f, 0.01f, "把暗部从趾部提起来；拉得越高暗部越亮"),
            slider("halation_amount", "光晕", "○", 1f, 0f, 4f, 0.1f, "强光红晕；1 是胶片本来量，滑到 0 关闭光晕"),
            slider("halation_boost_ev", "光晕增强", "+", 0f, 0f, 8f, 0.1f, "光晕强度提升档数"),
            slider("halation_scatter_amount", "乳剂散射", "~", 1f, 0f, 1f, 0.01f, "乳剂层光散射量"),
            slider("grain_amount", "颗粒", "G", 1f, 0f, 2f, 0.05f, "银盐颗粒强度（×胶片自身颗粒强度）；滑到 0 关闭颗粒"),
            slider("glare_amount", "眩光", "✳", 1f, 0f, 12f, 0.1f, "相纸表面光晕，抬黑位降对比；滑到 0 关闭眩光"),
            slider("lens_blur_um", "镜头模糊", "◌", 0f, 0f, 200f, 1f, "镜头光学模糊，单位微米，在胶片之前"),
            slider("y_filter_shift", "黄滤镜", "Y", 0f, -1f, 1f, 0.05f, "放大机黄滤镜偏移"),
            slider("m_filter_shift", "品红滤镜", "M", 0f, -1f, 1f, 0.05f, "放大机品红滤镜偏移"),
            slider("filter_shift_scale", "滤镜强度", "C", 1f, 1f, 60f, 1f, "滤镜偏移的尺度"),
            slider("preflash_exposure", "预闪", "⚡", 0f, 0f, 1f, 0.01f, "印相前均匀曝光，压暗部对比", scale = 0.04f),
            slider("density_curve_gamma", "浓度曲线", "γ", 1f, 0.2f, 4f, 0.05f, "胶片密度曲线伽马；0.6 发灰发亮，1.35 偏红", format = "%.3f"),
            slider("scanner_lens_blur", "扫描仪模糊", "S", 0f, 0f, 20f, 0.5f, "扫描环节镜头模糊"),
            slider("halation_headroom_ev", "光晕余量", "R", 4f, 0f, 8f, 0.1f, "光晕计算的高光余量"),
            // 光晕/颗粒/眩光的开关已并入各自的强度滑条（滑到 0 = 关闭），不再单独列 active 控件。
            // 这几个是纯布尔（无独立强度滑条），用真开关 toggle；引擎 JNI 要 boolean，见 FilmEngine。
            toggle("antihalation_removed", "去抗光晕", "⊘", false, "去除抗光晕层（实测变化 <0.2%）"),
            // 权威 dir_couplers_active 恒 true，强度由 amount 旋钮（0..2，默认1）决定（L31920-31921）。
            // 旧版是 active toggle（语义颠倒：关掉反而没强度概念），改为强度滑条。
            slider("dir_couplers_amount", "层间抑制", "⧉", 1f, 0f, 2f, 0.05f, "显影层间耦合抑制强度；0=无，1=胶片本来量"),
            toggle("digital_intermediate", "数字中间片", "I", false, "跳过光学印相走数码中间片"),
            toggle("output_cctf_encoding", "输出编码", "E", true, "关=相纸编码，开=显示器编码"),
            toggle("extended_dynamic_range", "动态范围EDR", "", false, "扩展场景动态范围"),
            choice("scene_latitude_norm", "保护基准", "power",
                listOf("幂律 power", "y", "max"), listOf("power", "y", "max"),
                "保护曲线的归一化方式（引擎合法值 power/y/max）"),
        ),
    ),
    // ── 2 光学：对齐 SpektraLab 的 5 页（亮度/颜色/效果/曲线/分级），全部走 view 调色通道
    //    注意：view* 参数不进 paramsJson，由 FilmEngine 构建 double[25]/double[80] 调引擎
    //    setViewAdjust/setViewCurves（渲染后调色，权威 line9844 注释）。这是"光学无效"的根治。
    ParamTab(
        index = 2,
        label = "光学",
        subTabs = listOf("亮度", "颜色", "效果", "曲线", "分级"),
        params = listOf(
            // 亮度页（权威 buildTonePage 4638）
            slider("viewExposure", "曝光", "E", 0f, -3f, 3f, 0.05f, "整体曝光（渲染后调色）", subTab = 0, isView = true),
            slider("viewContrast", "对比度", "C", 0f, -100f, 100f, 1f, "明暗对比", format = "%.0f", subTab = 0, isView = true),
            slider("viewBrightness", "亮度", "B", 0f, -100f, 100f, 1f, "整体亮度", format = "%.0f", subTab = 0, isView = true),
            slider("viewHighlights", "高光", "H", 0f, -100f, 100f, 1f, "提亮/压暗高光", format = "%.0f", subTab = 0, isView = true),
            slider("viewShadows", "阴影", "S", 0f, -100f, 100f, 1f, "提亮/压暗阴影", format = "%.0f", subTab = 0, isView = true),
            slider("viewBlack", "黑场", "K", 0f, 0f, 50f, 0.5f, "黑场电平", format = "%.1f", subTab = 0, isView = true),
            slider("viewWhite", "白场", "W", 0f, 0f, 50f, 0.5f, "白场电平", format = "%.1f", subTab = 0, isView = true),
            // 颜色页（权威 buildColourPage 4681）
            slider("viewTemperature", "色温", "T", 0f, -100f, 100f, 1f, "冷↔暖（渲染后调色）", format = "%.0f", subTab = 1, isView = true),
            slider("viewTint", "色调", "T", 0f, -100f, 100f, 1f, "绿↔品红", format = "%.0f", subTab = 1, isView = true),
            slider("viewSaturation", "饱和度", "S", 0f, -100f, 100f, 1f, "整体饱和度", format = "%.0f", subTab = 1, isView = true),
            slider("viewVibrance", "自然饱和度", "V", 0f, -100f, 100f, 1f, "只提低饱和色，保护肤色", format = "%.0f", subTab = 1, isView = true),
            // 效果页（权威 buildEffectsPage 4923）
            slider("viewVignette", "暗角", "V", 0f, -100f, 100f, 1f, "四角压暗（渲染后调色）", format = "%.0f", subTab = 2, isView = true),
            // 曲线页（权威 buildCurvesPage 4930）：5 通道 × 控制点
            ParamSpec("viewCurves", "色调曲线", "", ParamKind.CURVE, defaultCurves(), subTab = 3, isView = true,
                note = "5 通道曲线；点空白加点，拖动调形"),
            // 分级页（权威 buildGradingPage 4702）
            ParamSpec("viewColorWheels", "颜色轮", "", ParamKind.COLOR_WHEEL, defaultColorWheels(), subTab = 4, isView = true,
                note = "4 区间调色：外圈色相/内圈饱和度"),
            slider("cbLum", "分级明亮度", "L", 0f, -1f, 1f, 0.01f, "当前区间的明亮度", subTab = 4, isView = true),
        ),
    ),
    // ── 3 构图：合并原「尺度」+「构图」两页（权威里二者共享 overscanFormat/filmFormatMm 同一份状态）
    //    裁切与片门尺度必须联动：选片门自动开片边并从原图整幅重裁，否则画面比例≠gate 比例 → 渲染失败。
    //    裁切是提案式：比例卡片 + 主图可拖拽框，按"应用"才写 wire 触发渲染。
    //    90°旋转/翻转是即时动作，走 wire geometry_* 字段。
    ParamTab(
        index = 3,
        label = "构图",
        subTabs = listOf("裁切", "尺幅", "旋转"),
        params = listOf(
            // 裁切页：提案面板（比例卡片 + 拖拽框 + 应用），def 仅占位，实际用 DevelopScreen 的 cropPending
            ParamSpec("cropProposal", "裁切", "", ParamKind.CROP_PROPOSAL, 0f, subTab = 0, isView = true,
                note = "选比例 → 拖框 → 应用；应用前不渲染"),
            // 尺幅页：物理尺幅卡片条（10 片门），与裁切共用单一 applyGate setter
            ParamSpec("format_card", "物理尺幅", "", ParamKind.FORMAT_CARD, 0, subTab = 1, isView = true,
                note = "选片门：自动开片边并从原图重裁；裁切/旋转在相邻子页"),
            // 旋转页：90° 动作 + 角度滑块 + 翻转
            ParamSpec("rotateQuarter", "90°旋转", "", ParamKind.ROTATE_QUARTER, 0f, subTab = 2, isView = true,
                note = "⇆左转90° / ⇄右转90° / 归零"),
            slider("geometry_rotation_deg", "旋转角度", "", 0f, -45f, 45f, 0.6f, "拖画面转动；松手吸附水平（引擎范围 -45~45）", subTab = 2),
            toggle("geometry_flip_h", "水平翻转", "", false, "水平镜像")
                .copy(subTab = 2),
            toggle("geometry_flip_v", "垂直翻转", "", false, "垂直镜像")
                .copy(subTab = 2),
        ),
    ),
    // ── 4 边框：overscan + 日期打印（主要影响导出）
    ParamTab(
        index = 4,
        label = "边框",
        subTabs = listOf("片边", "日期"),
        params = listOf(
            // ── 片边页（buildEdgePage）：对齐权威 edge* 字段，默认全与权威一致
            toggle("overscan_active", "开启片边", "", false, "绘制片边/齿孔（全尺寸导出更明显）", subTab = 0),
            toggle("overscan_edge_text", "片边文字", "", true,
                "片边印厂标文字（权威 edgeTextOn 默认 true；开=随胶片名写文本/关=空串）", subTab = 0),
            choice("overscan_mode", "片边模式", "strip",
                listOf("整条 strip", "窄边 filed"), listOf("strip", "filed"),
                "整条/窄边（权威 MODE_IDS）", subTab = 0),
            choice("overscan_gate", "片门形状", "auto",
                listOf("自动", "方角", "圆角", "带耳", "带肩", "踢角"),
                listOf("auto", "square", "rounded", "eared", "shouldered", "kicked"),
                "片门角形（权威 GATE_IDS）", subTab = 0),
            choice("overscan_holes", "齿孔", "white",
                listOf("白齿孔(扫描)", "黑齿孔(暗房)"), listOf("white", "black"),
                "齿孔颜色（权威 HOLES_IDS）", subTab = 0),
            choice("overscan_carrier", "片夹", "none",
                listOf("无", "黑色片夹", "开放"), listOf("none", "black", "open"),
                "片夹样式（权威 CARRIER_IDS）", subTab = 0),
            slider("overscan_fog", "片基雾", "", 1f, 0f, 4f, 0.05f, "片基雾化强度", subTab = 0),
            slider("overscan_leaks", "漏光", "", 0f, 0f, 4f, 0.05f, "边缘漏光", subTab = 0),
            slider("overscan_frame_number", "张数", "", 0f, 0f, 99f, 1f, "片边张号，0=随机", format = "%.0f", subTab = 0),
            slider("overscan_f_number", "光圈值", "", 0f, 0f, 64f, 0.5f, "片边 f 值，0=未知", format = "%.1f", subTab = 0),
            slider("overscan_camera_seed", "相机种子", "", 1f, 0f, 100f, 1f, "随机种子（相机）", format = "%.0f", subTab = 0),
            slider("overscan_frame_seed", "帧种子", "", 1f, 0f, 100f, 1f, "随机种子（帧）", format = "%.0f", subTab = 0),
            // ── 日期页（buildDatePage）：对齐权威 date_imprint* 字段
            toggle("date_imprint_active", "开启日期水印", "", false, "在边框打印日期/数据", subTab = 1),
            slider("date_imprint_inset_x", "横向内缩", "", 3f, 0f, 200f, 0.5f, "距边缘 X", format = "%.1f", subTab = 1),
            slider("date_imprint_inset_y", "纵向内缩", "", 2.4f, 0f, 200f, 0.5f, "距边缘 Y", format = "%.1f", subTab = 1),
            slider("date_imprint_size", "字号", "", 1f, 0.4f, 8f, 0.1f, "日期字号", subTab = 1),
            slider("date_imprint_ev", "亮度 EV", "", 5.5f, -2f, 8f, 0.1f, "印字亮度补偿（权威默认 5.5；3.5 是已迁移旧值）", subTab = 1),
            choice("date_imprint_placement", "位置", "frame",
                listOf("画面内", "片边"), listOf("frame", "rebate"),
                "日期印在画面内或片边（权威 PLACE_IDS）", subTab = 1),
            choice("date_imprint_corner", "角落", "br",
                listOf("右下", "左下", "右上", "左上"), listOf("br", "bl", "tr", "tl"),
                "四角位置（权威 CORNER_IDS）", subTab = 1),
            choice("date_imprint_style", "样式", "lcd",
                listOf("LCD七段", "点阵", "拍摄数据"), listOf("lcd", "dots", "data"),
                "日期字体样式（权威 STYLE_IDS）", subTab = 1),
            toggle("date_imprint_data_active", "拍摄数据", "", false,
                "同时印出 EXIF 拍摄参数（光圈/快门/ISO/焦距）", subTab = 1),
        ),
    ),
)

/**
 * 专业参数 key 集合：普通模式下隐藏，仅「专业模式」开启时显示。
 * 选取原则：默认开启且关掉会变坏的输出/物理仿真项（用户不该动）、术语生僻或效果微弱的物理旋钮。
 * 集中在此按 key 标记，避免改动每个 slider/toggle 定义。
 */
val ADVANCED_PARAM_KEYS: Set<String> = setOf(
    // 冲印：默认开的底层物理项（关掉会破坏画面，普通用户无需知道）
    "antihalation_removed", "dir_couplers_active", "digital_intermediate",
    "output_cctf_encoding", "extended_dynamic_range", "scene_latitude_norm",
    // 冲印：术语生僻/效果微弱的物理旋钮
    "halation_boost_ev", "halation_scatter_amount", "lens_blur_um",
    "y_filter_shift", "m_filter_shift", "filter_shift_scale", "preflash_exposure",
    "density_curve_gamma", "scanner_lens_blur", "halation_headroom_ev",
    // 光学：曲线/颜色轮/分级属专业调色
    "viewCurves", "viewColorWheels", "cbLum",
    // 边框：片边细节与日期排版的微调项
    "overscan_edge_text", "overscan_mode", "overscan_gate", "overscan_holes",
    "overscan_carrier", "overscan_fog", "overscan_leaks", "overscan_frame_number",
    "overscan_f_number", "overscan_camera_seed", "overscan_frame_seed",
    "date_imprint_inset_x", "date_imprint_inset_y", "date_imprint_size",
    "date_imprint_ev", "date_imprint_placement", "date_imprint_corner",
    "date_imprint_style", "date_imprint_data_active",
)

/** 参数是否在专业模式下才显示 */
fun ParamSpec.isAdvanced(): Boolean = key in ADVANCED_PARAM_KEYS || advanced

/** 默认 wire 值：从 PARAM_TABS 收集所有 def，转成 Map<String,Any>（含 view 键，由 FilmEngine 分流） */
fun defaultWire(): Map<String, Any> = buildMap {
    PARAM_TABS.forEach { tab ->
        tab.params.forEach { spec ->
            put(spec.key, spec.def)
        }
    }
}

/** 所有走 view 调色通道的键（渲染后调色，不进 paramsJson）。FilmEngine 据此分流。 */
val VIEW_PARAM_KEYS: Set<String> = PARAM_TABS.flatMap { it.params }.filter { it.isView }.map { it.key }.toSet()
