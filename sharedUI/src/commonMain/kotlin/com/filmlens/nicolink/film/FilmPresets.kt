package com.filmlens.nicolink.film

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 调色预设：跨照片复用参数。存 SharedPreferences（JSON 数组），跨 App 重启持久化。
 * 应用预设 = 用保存的 filmId/paperId/wire 覆盖当前照片的所有参数。
 */
data class Preset(
    val name: String,
    val filmId: String,
    val paperId: String,
    val wire: Map<String, Any>,
    val createdAt: Long,
)

object FilmPresets {
    private const val PREFS = "nicolink_presets"
    private const val KEY_LIST = "presets"

    fun load(context: Context): List<Preset> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<Preset>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                // 用 WireJson 还原类型（含 viewCurves/viewColorWheels 的强类型 List）
                val wire = WireJson.fromJson(o.optJSONObject("wire") ?: JSONObject())
                out += Preset(
                    name = o.optString("name", ""),
                    filmId = o.optString("filmId", ""),
                    paperId = o.optString("paperId", ""),
                    wire = wire,
                    createdAt = o.optLong("createdAt", 0L),
                )
            }
            out
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun save(context: Context, presets: List<Preset>) {
        val arr = JSONArray()
        for (p in presets) {
            val o = JSONObject()
            o.put("name", p.name)
            o.put("filmId", p.filmId)
            o.put("paperId", p.paperId)
            o.put("createdAt", p.createdAt)
            o.put("wire", WireJson.toJson(p.wire))
            arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIST, arr.toString()).apply()
    }

    /** 新增预设（同名覆盖），返回更新后的列表 */
    fun add(context: Context, preset: Preset): List<Preset> {
        val list = load(context).filter { it.name != preset.name }.toMutableList()
        list.add(0, preset)
        save(context, list)
        return list
    }

    fun delete(context: Context, name: String): List<Preset> {
        val list = load(context).filter { it.name != name }
        save(context, list)
        return list
    }
}
