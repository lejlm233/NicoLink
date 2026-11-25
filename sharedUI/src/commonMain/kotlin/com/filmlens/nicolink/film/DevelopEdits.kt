package com.filmlens.nicolink.film

import android.content.Context
import org.json.JSONObject

/**
 * 单张照片的显影编辑状态（持久化，按 displayName 索引）。
 *
 * 与编辑缩略图配套：缩略图存「图长什么样」，这里存「用什么参数调出来的」：
 * - 重进显影页恢复 filmId/paperId/wire，避免参数全部回到默认；
 * - 详情页导出/分享按自己的照片取参数，避免 A 图参数串到 B 图；
 * - 跨 App 重启仍有效（存 SharedPreferences JSON）。
 */
data class EditState(
    val filmId: String,
    val paperId: String,
    val wire: Map<String, Any>,
    val updatedAt: Long,
)

object DevelopEdits {
    private const val PREFS = "nicolink_develop_edits"
    private const val KEY = "edits"

    fun loadAll(context: Context): Map<String, EditState> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyMap()
        return try {
            val root = JSONObject(raw)
            val out = mutableMapOf<String, EditState>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val o = root.optJSONObject(name) ?: continue
                out[name] = EditState(
                    filmId = o.optString("filmId", ""),
                    paperId = o.optString("paperId", ""),
                    wire = WireJson.fromJson(o.optJSONObject("wire") ?: JSONObject()),
                    updatedAt = o.optLong("updatedAt", 0L),
                )
            }
            out
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    fun load(context: Context, name: String): EditState? = loadAll(context)[name]

    /** 新增/覆盖一张照片的编辑状态，不影响其它照片 */
    fun save(context: Context, name: String, state: EditState) {
        val all = loadAll(context).toMutableMap()
        all[name] = state
        writeAll(context, all)
    }

    fun delete(context: Context, name: String) {
        val all = loadAll(context).toMutableMap()
        if (all.remove(name) == null) return
        writeAll(context, all)
    }

    private fun writeAll(context: Context, all: Map<String, EditState>) {
        val root = JSONObject()
        for ((name, s) in all) {
            val o = JSONObject()
            o.put("filmId", s.filmId)
            o.put("paperId", s.paperId)
            o.put("updatedAt", s.updatedAt)
            o.put("wire", WireJson.toJson(s.wire))
            root.put(name, o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, root.toString()).apply()
    }
}
