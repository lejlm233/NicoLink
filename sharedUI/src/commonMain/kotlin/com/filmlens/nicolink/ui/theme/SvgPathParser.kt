package com.filmlens.nicolink.ui.theme

import androidx.compose.ui.graphics.vector.PathBuilder
import kotlin.math.*

/**
 * SVG path 解析器 — 支持 M/L/C/S/Q/T/A/H/V/Z 全命令（绝对/相对坐标）
 */
fun PathBuilder.parseSvgPath(pathData: String) {
    // SVG 数字允许紧凑写法：3.813.845 = 3.813 与 .845、1-2 = 1 与 -2。
    // 用正则直接提取命令字母与完整数字，按空白拆分会在 Float.parseFloat 崩溃
    // （NumberFormatException: multiple points，支付宝图标 path 实测踩中）
    val tokens = Regex("[MLHVCSQTAZmlhvcsqtaz]|[-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?")
        .findAll(pathData)
        .map { it.value }
        .toList()

    var cmd = ' '
    var i = 0
    var cx = 0f; var cy = 0f
    var sx = 0f; var sy = 0f
    var cpX = 0f; var cpY = 0f   // 上一曲线第二/控制点（S/T 平滑反射基准）
    var hasCurve = false

    fun next(): Float = tokens[i++].toFloat()

    // PathBuilder 无二次贝塞尔 API：Q(P0,C,P1) 恒等转换为三次曲线，
    // C1 = P0 + 2/3(C-P0)，C2 = P1 + 2/3(C-P1)
    fun quadTo(x1: Float, y1: Float, ex: Float, ey: Float) {
        curveTo(
            cx + (x1 - cx) * 2f / 3f, cy + (y1 - cy) * 2f / 3f,
            ex + (x1 - ex) * 2f / 3f, ey + (y1 - ey) * 2f / 3f,
            ex, ey,
        )
    }

    while (i < tokens.size) {
        val t = tokens[i]
        val c = t[0]
        if (c in "MLHVCSQTAZmlhvcsqtaz") {
            cmd = c; i++
        }

        when (cmd) {
            'M' -> { cx = next(); cy = next(); moveTo(cx, cy); sx = cx; sy = cy; hasCurve = false }
            'm' -> { cx += next(); cy += next(); moveTo(cx, cy); sx = cx; sy = cy; hasCurve = false }
            'L' -> { cx = next(); cy = next(); lineTo(cx, cy); hasCurve = false }
            'l' -> { cx += next(); cy += next(); lineTo(cx, cy); hasCurve = false }
            'H' -> { cx = next(); lineTo(cx, cy); hasCurve = false }
            'h' -> { cx += next(); lineTo(cx, cy); hasCurve = false }
            'V' -> { cy = next(); lineTo(cx, cy); hasCurve = false }
            'v' -> { cy += next(); lineTo(cx, cy); hasCurve = false }
            'C' -> {
                val x1 = next(); val y1 = next()
                val x2 = next(); val y2 = next()
                cx = next(); cy = next()
                curveTo(x1, y1, x2, y2, cx, cy)
                cpX = x2; cpY = y2; hasCurve = true
            }
            'c' -> {
                val dx1 = next(); val dy1 = next()
                val dx2 = next(); val dy2 = next()
                val dx = next(); val dy = next()
                curveTo(cx+dx1, cy+dy1, cx+dx2, cy+dy2, cx+dx, cy+dy)
                cpX = cx + dx2; cpY = cy + dy2
                cx += dx; cy += dy; hasCurve = true
            }
            // S/s 平滑三次贝塞尔：首控制点 = 当前点关于上一曲线控制点的反射
            'S' -> {
                val x2 = next(); val y2 = next()
                val ex = next(); val ey = next()
                val x1 = if (hasCurve) 2 * cx - cpX else cx
                val y1 = if (hasCurve) 2 * cy - cpY else cy
                curveTo(x1, y1, x2, y2, ex, ey)
                cpX = x2; cpY = y2
                cx = ex; cy = ey; hasCurve = true
            }
            's' -> {
                val dx2 = next(); val dy2 = next()
                val dx = next(); val dy = next()
                val x1 = if (hasCurve) 2 * cx - cpX else cx
                val y1 = if (hasCurve) 2 * cy - cpY else cy
                curveTo(x1, y1, cx + dx2, cy + dy2, cx + dx, cy + dy)
                cpX = cx + dx2; cpY = cy + dy2
                cx += dx; cy += dy; hasCurve = true
            }
            'Q' -> {
                val x1 = next(); val y1 = next()
                cx = next(); cy = next()
                quadTo(x1, y1, cx, cy)
                cpX = x1; cpY = y1; hasCurve = true
            }
            'q' -> {
                val dx1 = next(); val dy1 = next()
                val dx = next(); val dy = next()
                quadTo(cx + dx1, cy + dy1, cx + dx, cy + dy)
                cpX = cx + dx1; cpY = cy + dy1
                cx += dx; cy += dy; hasCurve = true
            }
            'T' -> {
                val ex = next(); val ey = next()
                val x1 = if (hasCurve) 2 * cx - cpX else cx
                val y1 = if (hasCurve) 2 * cy - cpY else cy
                quadTo(x1, y1, ex, ey)
                cpX = x1; cpY = y1
                cx = ex; cy = ey; hasCurve = true
            }
            't' -> {
                val dx = next(); val dy = next()
                val x1 = if (hasCurve) 2 * cx - cpX else cx
                val y1 = if (hasCurve) 2 * cy - cpY else cy
                quadTo(x1, y1, cx + dx, cy + dy)
                cpX = x1; cpY = y1
                cx += dx; cy += dy; hasCurve = true
            }
            'A' -> {
                val rx = abs(next()); val ry = abs(next())
                val rot = next(); val large = next().toInt(); val sweep = next().toInt()
                val nx = next(); val ny = next()
                arcTo(rx, ry, rot, large != 0, sweep != 0, cx, cy, nx, ny)
                cx = nx; cy = ny; hasCurve = false
            }
            // a 相对弧：终点相对当前点（支付宝图标实有 a 命令）
            'a' -> {
                val rx = abs(next()); val ry = abs(next())
                val rot = next(); val large = next().toInt(); val sweep = next().toInt()
                val dx = next(); val dy = next()
                val nx = cx + dx; val ny = cy + dy
                arcTo(rx, ry, rot, large != 0, sweep != 0, cx, cy, nx, ny)
                cx = nx; cy = ny; hasCurve = false
            }
            'Z', 'z' -> { close(); cx = sx; cy = sy; hasCurve = false }
            else -> i++ // 未知 token 兜底跳过，防止原地死循环
        }
    }
}

/**
 * SVG 椭圆弧（A 命令）的正确实现：端点参数 → 中心参数转换，再按椭圆旋转拆成 1~2 段三次贝塞尔。
 * 参考 W3C SVG 实现说明（F.6.5）：https://www.w3.org/TR/SVG/implnote.html#ArcConversionEndpointToCenter
 */
private fun PathBuilder.arcTo(rxParam: Float, ryParam: Float, angleDeg: Float, large: Boolean, sweep: Boolean, x0: Float, y0: Float, x1: Float, y1: Float) {
    if (x0 == x1 && y0 == y1) return // 退化：起终点相同，无弧

    val phi = Math.toRadians(angleDeg.toDouble())
    val cosPhi = cos(phi); val sinPhi = sin(phi)

    // 1) 把起终点变换到以原点为中心、椭圆轴对齐坐标系
    val dx2 = (x0 - x1) / 2f
    val dy2 = (y0 - y1) / 2f
    val x1p = cosPhi * dx2 + sinPhi * dy2
    val y1p = -sinPhi * dx2 + cosPhi * dy2

    // 2) 半径校正：若半径过小无法连接起终点，按比例放大
    var rx = abs(rxParam).toDouble()
    var ry = abs(ryParam).toDouble()
    val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if (lambda > 1.0) {
        val s = sqrt(lambda)
        rx *= s; ry *= s
    }

    // 3) 计算中心（相对坐标 x1p',y1p' 周围的椭圆中心）
    val rx2 = rx * rx; val ry2 = ry * ry
    val num = rx2 * ry2 - rx2 * y1p * y1p - ry2 * x1p * x1p
    val den = rx2 * y1p * y1p + ry2 * x1p * x1p
    val coef = sqrt((max(0.0, num) / den).coerceAtLeast(0.0)) * (if (large == sweep) -1.0 else 1.0)
    val cxp = coef * (rx * y1p / ry)
    val cyp = coef * (-ry * x1p / rx)

    // 中心变换回原坐标系
    val cx = cosPhi * cxp - sinPhi * cyp + (x0 + x1) / 2f
    val cy = sinPhi * cxp + cosPhi * cyp + (y0 + y1) / 2f

    // 4) 角度参数化：起止角与扫掠角
    fun angle(ux: Double, uy: Double): Double {
        val a = acos((ux / rx).coerceIn(-1.0, 1.0))
        return if (uy >= 0) a else -a
    }
    val ux = (x1p - cxp) / rx
    val uy = (y1p - cyp) / ry
    val vx = (-x1p - cxp) / rx
    val vy = (-y1p - cyp) / ry
    var t1 = angle(ux, uy)
    var dt = angle(vx, vy) - t1
    // 规范扫掠角到正确方向与 large 标志
    if (!sweep && dt > 0) dt -= 2 * PI
    else if (sweep && dt < 0) dt += 2 * PI

    // 5) 用三次贝塞尔逼近：按 90° 分段（误差 <0.03%）
    val n = ceil(abs(dt) / (PI / 2)).toInt().coerceAtLeast(1)
    val step = dt / n
    val k = 4.0 / 3.0 * tan(step / 4.0)
    for (j in 0 until n) {
        val t = t1 + j * step
        val t2 = t + step
        fun point(a: Double): Pair<Double, Double> {
            val ca = cos(a); val sa = sin(a)
            val px = cx + cosPhi * rx * ca - sinPhi * ry * sa
            val py = cy + sinPhi * rx * ca + cosPhi * ry * sa
            return px to py
        }
        fun deriv(a: Double): Pair<Double, Double> {
            val ca = cos(a); val sa = sin(a)
            val dx = -cosPhi * rx * sa - sinPhi * ry * ca
            val dy = -sinPhi * rx * sa + cosPhi * ry * ca
            return dx to dy
        }
        val (px, py) = point(t); val (p2x, p2y) = point(t2)
        val (d1x, d1y) = deriv(t); val (d2x, d2y) = deriv(t2)
        curveTo(
            (px + k * d1x).toFloat(), (py + k * d1y).toFloat(),
            (p2x - k * d2x).toFloat(), (p2y - k * d2y).toFloat(),
            p2x.toFloat(), p2y.toFloat(),
        )
    }
}
