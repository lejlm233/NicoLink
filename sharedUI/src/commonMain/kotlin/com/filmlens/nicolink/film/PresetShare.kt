package com.filmlens.nicolink.film

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.json.JSONObject

/**
 * 预设分享码编解码。
 *
 * 格式：`NLK3|中文名|胶片id|相纸id|<delta 的 Base64>`
 * - 中文名明文（"中文就中文解析"），`|` 在名里替换成全角 `｜`。
 * - 胶片/相纸 id 也用 base62 序号单字符代替（如 kodak_portra_400→某单字符），
 *   新条目（catalog 更新后不在表内）用 `~完整id` 兜底。
 * - delta 只存"与默认值不同"的参数，且参数名用 1~2 字符短 id 代替（base62 序号），
 *   大幅缩短码长。复杂类型（viewCurves/viewColorWheels）仍走 WireJson 强类型序列化。
 * - 不兼容旧 NLK1/NLK2 码（尚未发布）。
 */
@OptIn(ExperimentalEncodingApi::class)
object PresetShare {
    private const val PREFIX = "NLK3"
    private const val SEP = '|'
    private const val SEP_FULLWIDTH = '｜'
    private const val FALLBACK_MARK = '~'
    const val MAX_NAME_LENGTH = 20

    private const val B62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    /** 动态写入键（不在 PARAM_TABS 的 def 里，但会出现在预设 wire 中） */
    private val EXTRA_KEYS = listOf(
        "geometry_crop_x", "geometry_crop_y", "geometry_crop_w", "geometry_crop_h",
        "overscan_format", "film_format_mm", "geometry_quarter_turns",
        "date_imprint_data_text", "preview_long_edge",
    )

    /**
     * 稳定顺序：defaultWire() 的键（PARAM_TABS 顺序）+ 动态键。
     * 注意：发布后新增参数请追加到最后一个 tab 的末尾，否则 id 会漂移导致旧码失效。
     * 未收录的键用 `~完整键名` 兜底，保证新参数也能往返。
     */
    private val CANONICAL_KEYS: List<String> = (defaultWire().keys + EXTRA_KEYS).distinct()
    private val KEY_TO_ID: Map<String, String> = CANONICAL_KEYS.mapIndexed { i, k -> k to base62(i) }.toMap()
    private val ID_TO_KEY: Map<String, String> = KEY_TO_ID.entries.associate { (k, v) -> v to k }

    private fun base62(n: Int): String {
        if (n == 0) return "0"
        val sb = StringBuilder()
        var x = n
        while (x > 0) {
            sb.append(B62[x % 62])
            x /= 62
        }
        return sb.reverse().toString()
    }

    private fun idFor(key: String): String = KEY_TO_ID[key] ?: (FALLBACK_MARK + key)
    private fun keyFor(id: String): String =
        if (id.startsWith(FALLBACK_MARK)) id.substring(1) else (ID_TO_KEY[id] ?: id)

    /**
     * 胶片/相纸 id 也短码化：完整 id（如 kodak_portra_400=16 字符）→ base62 序号单字符。
     * 顺序取自 stock_catalog.json；发布后新增胶片/相纸请追加到列表末尾，否则旧码会错位。
     * 不在列表里的新条目用 `~完整id` 兜底，保证 catalog 更新后仍能往返。
     */
    private val FILM_IDS = listOf(
        "cinestill_800t", "fujifilm_c200", "fujifilm_neopan_acros_100_ii", "fujifilm_pro_400h",
        "fujifilm_provia_100f", "fujifilm_velvia_100", "fujifilm_xtra_400", "ilford_hp5_plus_400",
        "kodak_ektachrome_100", "kodak_ektar_100", "kodak_gold_200", "kodak_kodachrome_64",
        "kodak_portra_160", "kodak_portra_400", "kodak_portra_800", "kodak_portra_800_push1",
        "kodak_portra_800_push2", "kodak_tmax_100", "kodak_tri_x_400", "kodak_ultramax_400",
        "kodak_verita_200d", "kodak_vision3_200t", "kodak_vision3_250d", "kodak_vision3_500t",
        "kodak_vision3_50d", "__none__", // FILM_NONE（原图）
    )
    private val PAPER_IDS = listOf(
        "fujifilm_crystal_archive_typeii", "ilford_multigrade_iv_rc", "kodak_ektacolor_edge",
        "kodak_endura_premier", "kodak_portra_endura", "kodak_supra_endura", "kodak_ultra_endura",
        "kodak_2383", "kodak_2393", "__none__", // PAPER_NONE（无相纸）
    )
    private val FILM_TO_ID: Map<String, String> = FILM_IDS.mapIndexed { i, id -> id to base62(i) }.toMap()
    private val ID_TO_FILM: Map<String, String> = FILM_TO_ID.entries.associate { (k, v) -> v to k }
    private val PAPER_TO_ID: Map<String, String> = PAPER_IDS.mapIndexed { i, id -> id to base62(i) }.toMap()
    private val ID_TO_PAPER: Map<String, String> = PAPER_TO_ID.entries.associate { (k, v) -> v to k }

    private fun filmToId(id: String): String = FILM_TO_ID[id] ?: (FALLBACK_MARK + id)
    private fun idToFilm(s: String): String =
        if (s.startsWith(FALLBACK_MARK)) s.substring(1) else (ID_TO_FILM[s] ?: s)
    private fun paperToId(id: String): String = PAPER_TO_ID[id] ?: (FALLBACK_MARK + id)
    private fun idToPaper(s: String): String =
        if (s.startsWith(FALLBACK_MARK)) s.substring(1) else (ID_TO_PAPER[s] ?: s)

    fun encode(preset: Preset): String {
        val safeName = (if (preset.name.length > MAX_NAME_LENGTH) preset.name.take(MAX_NAME_LENGTH) else preset.name)
            .replace(SEP, SEP_FULLWIDTH)
        val defaults = defaultWire()
        // 只保留与默认值不同的键
        val delta = preset.wire.filter { (k, v) -> !defaults.containsKey(k) || defaults[k] != v }
        // 先用完整 key 走 WireJson（保证 viewCurves/viewColorWheels 强类型），再把 key 换成短 id
        val fullJson = WireJson.toJson(delta)
        val shortJson = JSONObject()
        val it = fullJson.keys()
        while (it.hasNext()) {
            val k = it.next()
            shortJson.put(idFor(k), fullJson.get(k))
        }
        val payload = Base64.UrlSafe.encode(shortJson.toString().encodeToByteArray())
        return listOf(PREFIX, safeName, filmToId(preset.filmId), paperToId(preset.paperId), payload)
            .joinToString(SEP.toString())
    }

    fun decode(text: String): Preset? {
        val trimmed = text.trim()
        if (!trimmed.startsWith(PREFIX + SEP)) return null
        return try {
            val parts = trimmed.split(SEP)
            if (parts.size != 5) return null
            val name = parts[1].replace(SEP_FULLWIDTH, SEP)
            val film = idToFilm(parts[2])
            val paper = idToPaper(parts[3])
            if (name.isEmpty() || name.length > MAX_NAME_LENGTH || film.isEmpty() || paper.isEmpty()) return null
            val bytes = Base64.UrlSafe.decode(parts[4])
            val shortJson = JSONObject(String(bytes, Charsets.UTF_8))
            // 短 id 还原成完整 key 后再交给 WireJson（保证复杂类型强类型解码）
            val fullJson = JSONObject()
            val it = shortJson.keys()
            while (it.hasNext()) {
                val id = it.next()
                fullJson.put(keyFor(id), shortJson.get(id))
            }
            val delta = WireJson.fromJson(fullJson)
            val wire = defaultWire().toMutableMap().apply { putAll(delta) }
            Preset(name = name, filmId = film, paperId = paper, wire = wire, createdAt = 0L)
        } catch (_: Throwable) {
            null
        }
    }
}
