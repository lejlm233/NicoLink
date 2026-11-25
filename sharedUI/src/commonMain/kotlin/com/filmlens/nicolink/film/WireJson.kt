package com.filmlens.nicolink.film

import org.json.JSONArray
import org.json.JSONObject

/**
 * wire Map <-> JSONObject 的类型安全序列化。
 *
 * 历史 bug：DevelopEdits / FilmPresets 用 `v.toString()` 存 List，
 * 导致 viewCurves(List<List<CurvePoint>>) / viewColorWheels(List<ColorWheelState>)
 * 读回变成 String，applyViewAdjust 的 cast 失败 → 曲线/颜色轮全部丢失。
 * 这里按 key 还原成强类型 List，兼容旧数据（旧 String 读回仍是 String，走默认曲线兜底）。
 */
object WireJson {
    fun toJson(wire: Map<String, Any>): JSONObject = JSONObject().apply {
        for ((k, v) in wire) put(k, encode(v))
    }

    fun fromJson(obj: JSONObject): Map<String, Any> {
        val wire = mutableMapOf<String, Any>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            wire[k] = decode(k, obj.get(k))
        }
        return wire
    }

    private fun encode(v: Any): Any = when (v) {
        is Boolean, is Number, is String -> v
        is List<*> -> JSONArray().apply { v.forEach { put(encodeItem(it)) } }
        else -> v.toString()
    }

    private fun encodeItem(v: Any?): Any = when (v) {
        is Boolean, is Number, is String -> v
        is List<*> -> JSONArray().apply { v.forEach { put(encodeItem(it)) } }
        is CurvePoint -> JSONObject().apply {
            put("x", v.x.toDouble())
            put("y", v.y.toDouble())
        }
        is ColorWheelState -> JSONObject().apply {
            put("hue", v.hue.toDouble())
            put("saturation", v.saturation.toDouble())
            put("luminance", v.luminance.toDouble())
        }
        else -> v.toString()
    }

    private fun decode(key: String, v: Any): Any = when (v) {
        is Boolean, is Number, is String -> v
        is JSONArray -> when (key) {
            "viewCurves" -> decodeCurves(v)
            "viewColorWheels" -> decodeWheels(v)
            else -> (0 until v.length()).map { decode("", v.get(it)) }
        }
        else -> v.toString()
    }

    private fun decodeCurves(arr: JSONArray): List<List<CurvePoint>> =
        (0 until arr.length()).map { ch ->
            val pts = arr.optJSONArray(ch) ?: JSONArray()
            (0 until pts.length()).mapNotNull { i ->
                when (val p = pts.opt(i)) {
                    is JSONArray -> CurvePoint(p.optDouble(0).toFloat(), p.optDouble(1).toFloat())
                    is JSONObject -> CurvePoint(p.optDouble("x").toFloat(), p.optDouble("y").toFloat())
                    else -> null
                }
            }
        }

    private fun decodeWheels(arr: JSONArray): List<ColorWheelState> =
        (0 until arr.length()).map { i ->
            val o = arr.optJSONObject(i) ?: JSONObject()
            ColorWheelState(
                hue = o.optDouble("hue").toFloat(),
                saturation = o.optDouble("saturation").toFloat(),
                luminance = o.optDouble("luminance").toFloat(),
            )
        }
}
