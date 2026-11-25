package com.filmlens.nicolink.ui.shared.liquid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.runtimeShaderEffect
import kotlin.math.roundToInt

/**
 * 观片台长按放大镜（液态玻璃镜片）。
 *
 * 结构：黑色镜框（圆盘）+ 内嵌圆形「液态玻璃」镜片 + 镜缘高光弧 + 镜框外一圈软阴影。
 * 镜片不是简单把背景放大——它用 backdrop 库把**镜头下方的屏幕内容**（胶片记录层
 * filmBackdrop 叠底色）采进纹理，再由自定义 AGSL 着色器做三件事：
 *  1. 以镜心为中心放大 [LiquidMagnifierSpec.Zoom] 倍（取样半径 = 盘内半径 / zoom）；
 *  2. 盘缘一圈把**镜外**内容压缩着卷进来（取样半径从 盘内/zoom 渐变到 R + edgeBend），
 *     这就是玻璃边缘的折射带（真放大镜镜缘也是把周围视野压进来）；
 *  3. 折射带内按径向分离 RGB（[LiquidMagnifierSpec.Dispersion]），模拟玻璃对不同波长的折射差。
 * 这三步都在 GPU 上次采样完成，没有额外离屏栅格化，不引入模糊。
 *
 * 外投影是**自绘**的（`Brush.radialGradient` 软阴影，圆心向下偏一点做出方向性光照）：
 * 平台的 `Modifier.shadow` 在暗房黑底上基本看不见，也没法做方向性；自绘还能跟着出镜/收镜
 * 动画一起缩放。为此组件容器四周留了 [LiquidMagnifierMetrics.shadowPadPx] 的余量，
 * 调用方给的 [center] 仍按**镜心**算，容器左上角由本组件自己减掉这部分。
 *
 * 与 app 其他玻璃件一致：`drawBackdrop(shape = CircleShape)`，shape 同时用于裁剪与
 * SDF 角半径。
 *
 * **缩放动画（出镜/收镜）走库的 `layerBlock`，不能在外层套 graphicsLayer：**
 * 采样区按「节点本地方位」1:1 映射，任何未告知库的缩放都会让采样与绘制错位
 * （错位量 ≈ (1-s)·该点到变换锚点的距离）。`layerBlock` 是库自己声明的口子：
 * `LayerBackdrop` 会按同一组 scale 反向补一次（`InverseLayerScope`），而
 * `localPositionOf` 又会把节点自身 layer 的变换算进去（`NodeCoordinator.toParentPosition`
 * → `layer.mapOffset`），两者正好抵消 —— 镜片几何按锚点缩放、镜内采到的背景纹丝不动
 * （和真放大镜「镜片变大，看到的东西不会跟着跑」一致）。
 */

/** 放大镜外观/光学参数（集中一处，按实机观感微调） */
object LiquidMagnifierSpec {
    /** 玻璃镜片直径（不含镜框） */
    val GlassDiameter: Dp = 152.dp
    /** 黑色镜框宽度（镜片外增加的一圈） */
    val RingWidth: Dp = 9.dp
    /** 放大倍率 */
    const val Zoom: Float = 2.2f
    /** 边缘折射带宽度（沿半径方向） */
    val EdgeWidth: Dp = 22.dp
    /** 边缘向外卷取量：镜缘取样半径 = R + 本值，需要 backdrop padding 覆盖 */
    val EdgeBend: Dp = 24.dp
    /** 采样外扩余量：padding 在 [EdgeBend] + [Dispersion] 之上再留一点，避免镜缘取样贴到纹理边界被 clamp */
    val SampleSlack: Dp = 8.dp
    /** 边缘色散强度（折射带内 RGB 的**径向**分离量，R 向外 / B 向内各这么多） */
    val Dispersion: Dp = 4.dp
    /** 镜框与长按点的间距（镜心落在长按点上方，避免被手指挡住） */
    val FingerGap: Dp = 18.dp
    /** 镜框外投影：向外晕开的距离（自绘软阴影，做出「镜片浮在胶片上」的立体感） */
    val ShadowBlur: Dp = 14.dp
    /** 镜框外投影向下偏移（光从左上来 → 阴影偏下，有方向性才像立体） */
    val ShadowOffsetY: Dp = 3.dp
    /**
     * 外投影贴镜框处的不透明度（往外快速衰减到 0）。
     * 只调浓度、不动 [ShadowBlur] 与 [ShadowOffsetY]，所以「浮起」的立体感保留、
     * 只是不再糊成一圈黑：0.46 在亮底片上峰值压暗 32%，0.20 实测只压暗 7.5%
     * （晕开宽度不变，r ≥ 镜框 + ShadowBlur 处两版逐像素相同）。
     */
    const val ShadowAlpha: Float = 0.20f
}

/** 放大镜几何度量（px）——供调用点算镜心位置与边界夹取 */
@Immutable
class LiquidMagnifierMetrics(
    /** 玻璃镜片半径（px） */
    val glassRadius: Float,
    /** 含黑框的外半径（px） */
    val outerRadius: Float,
    /** 镜框与手指的间距（px） */
    val fingerGap: Float,
    /** 采样外扩量（px）：shader 的 padding 口径（含折射带 + 色散 + 余量） */
    val padPx: Float,
    /** 外投影预留（px）：组件容器四周要留出这么多，否则自绘的软阴影会被容器裁掉 */
    val shadowPadPx: Float,
) {
    /** 镜心相对长按点的偏移（上方） */
    val centerOffsetY: Float get() = outerRadius + fingerGap
}

/**
 * 采样外扩量（px）：shader 的 padding 与调用点的边界夹取共用同一口径，避免两者不一致。
 * 要把 [LiquidMagnifierSpec.Dispersion] 一起算进去——色散在 `sr` 之上再沿径向外推一圈，
 * 不预留就会让最外层取样超出纹理边界被 clamp（表现为镜缘一圈脏边）。
 */
internal fun liquidMagnifierPadPx(density: Density): Float =
    with(density) {
        (LiquidMagnifierSpec.EdgeBend + LiquidMagnifierSpec.Dispersion + LiquidMagnifierSpec.SampleSlack).toPx()
    }

/** 按当前 density 换算放大镜几何（记住即可，不随帧重算） */
@Composable
fun rememberLiquidMagnifierMetrics(): LiquidMagnifierMetrics {
    val density = LocalDensity.current
    return remember(density) { metricsFor(density) }
}

private fun metricsFor(density: Density): LiquidMagnifierMetrics {
    val glassRadius = with(density) { LiquidMagnifierSpec.GlassDiameter.toPx() } / 2f
    val ring = with(density) { LiquidMagnifierSpec.RingWidth.toPx() }
    return LiquidMagnifierMetrics(
        glassRadius = glassRadius,
        outerRadius = glassRadius + ring,
        fingerGap = with(density) { LiquidMagnifierSpec.FingerGap.toPx() },
        padPx = liquidMagnifierPadPx(density),
        shadowPadPx = with(density) {
            (LiquidMagnifierSpec.ShadowBlur + LiquidMagnifierSpec.ShadowOffsetY).toPx()
        },
    )
}

/**
 * 放大镜 AGSL。uniform 约定与库自带折射着色器一致：
 * `size` = 镜片尺寸、`offset` = -padding（`coord + offset` 即镜片内坐标），
 * `content` = 库注入的背景 sampler（比镜片外扩 padding px）。
 */
private const val MAGNIFIER_SHADER = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float zoom;
uniform float edgeWidth;
uniform float edgeBend;
uniform float dispersion;

half4 main(float2 coord) {
    float2 n = coord + offset;
    float2 halfSize = size * 0.5;
    float2 c = n - halfSize;
    float r = length(c);
    float R = min(halfSize.x, halfSize.y);

    float2 dir = float2(0.0, 0.0);
    if (r > 0.001) {
        dir = c / r;
    }

    // 边缘因子：0 = 盘心，1 = 盘缘；只在最外一圈生效
    float e = smoothstep(max(R - edgeWidth, 0.0), R, r);

    // 盘内：取样半径 = r / zoom（放大）；盘缘：向外卷取到 R + edgeBend（折射带）
    float sr = mix(r / zoom, R + edgeBend, e * e);

    float2 tex = halfSize + dir * sr - offset;

    if (dispersion <= 0.0) {
        return content.eval(tex);
    }
    // 色散：镜缘按径向分离 RGB，模拟玻璃对不同波长的折射差
    float d = dispersion * e;
    half4 col = content.eval(tex);
    col.r = content.eval(tex + dir * d).r;
    col.b = content.eval(tex - dir * d).b;
    return col;
}
"""

/**
 * 放大镜叠加层。调用方按屏幕坐标给 [center]（**镜片中心**，px），本组件自行按
 * 外半径换算左上角摆放位置。
 *
 * @param backdrop 背景采样源（观片台传 liquidBackdrop：胶片记录层 + 灯箱底色）
 * @param center 镜片中心（父容器坐标 px）
 * @param scale 出镜/收镜动画缩放，以镜片中心为锚点；1 = 原始大小。镜框、
 *   镜片、高光三层各自缩放，锚点重合（镜片是 align(Center) 的，三者同心）。
 *
 *   ⚠️ 缩放**不改变倍率**：屏幕半径 s·r 处的像素采到的是胶片半径 s·(r/zoom) 处的内容
 *   （层的缩放对「几何」和「内容」同时生效），比值恒为 [LiquidMagnifierSpec.Zoom]。
 *   所以镜片从小到大始终是 2.2×，变大只是把更大的范围卷进镜内（视野变宽），
 *   与真放大镜把镜片移近/拿远时「倍率不变、看到更多」观感一致。
 *   这点在模拟器上实测过：s=0.56/0.83/0.87/0.97/1.0 五档 NCC 峰值 z 都落在 2.2。
 */
@Composable
fun LiquidMagnifier(
    backdrop: Backdrop,
    center: Offset,
    metrics: LiquidMagnifierMetrics,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val density = LocalDensity.current
    val outerDp = with(density) { (metrics.outerRadius * 2f).toDp() }
    val glassDp = with(density) { (metrics.glassRadius * 2f).toDp() }
    val shadowPadDp = with(density) { metrics.shadowPadPx.toDp() }
    val edgeWidthPx = with(density) { LiquidMagnifierSpec.EdgeWidth.toPx() }
    val edgeBendPx = with(density) { LiquidMagnifierSpec.EdgeBend.toPx() }
    val dispersionPx = with(density) { LiquidMagnifierSpec.Dispersion.toPx() }
    val shadowBlurPx = with(density) { LiquidMagnifierSpec.ShadowBlur.toPx() }
    val shadowOffsetYPx = with(density) { LiquidMagnifierSpec.ShadowOffsetY.toPx() }
    val ringStrokePx = with(density) { 1.5.dp.toPx() }
    // 自绘外投影的颜色：贴镜框处最浓，往外衰减（比平台 shadow() 更可控——暗房黑底上
    // 平台阴影看不见，也没法做成「偏下」的方向性光照）
    val shadowPeak = Color.Black.copy(alpha = LiquidMagnifierSpec.ShadowAlpha)
    val shadowMid = Color.Black.copy(alpha = LiquidMagnifierSpec.ShadowAlpha * 0.42f)

    Box(
        modifier
            .offset {
                IntOffset(
                    (center.x - metrics.outerRadius - metrics.shadowPadPx).roundToInt(),
                    (center.y - metrics.outerRadius - metrics.shadowPadPx).roundToInt(),
                )
            }
            .size(outerDp + shadowPadDp * 2)
    ) {
        // ⓪ 外投影：比镜框大一圈的软阴影，圆心向下偏 [LiquidMagnifierSpec.ShadowOffsetY]
        //    ——镜片读作「浮在胶片上方」，光从左上来。缩放跟着动画一起走（挂同一 scale）。
        Canvas(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            val r = metrics.outerRadius
            val halo = r + shadowBlurPx
            val hx = size.width / 2f
            val hy = size.height / 2f + shadowOffsetYPx
            // 镜框外缘在渐变里的位置；内侧（被镜框盖住的部分）保持透明，避免边缘露硬线
            val inner = (r / halo).coerceIn(0f, 0.98f)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        inner * 0.86f to Color.Transparent,
                        inner to shadowPeak,
                        inner + (1f - inner) * 0.42f to shadowMid,
                        1f to Color.Transparent,
                    ),
                    center = Offset(hx, hy),
                    radius = halo,
                ),
                radius = halo,
                center = Offset(hx, hy),
            )
        }

        // ① 黑色镜框（投影由上面的 ⓪ 负责，不再叠平台 shadow）
        Box(
            Modifier
                .size(outerDp)
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(Color(0xFF0D0D0E), CircleShape)
        )

        // ② 液态玻璃镜片：采样背景 + 放大 + 边缘折射 + 色散
        //    缩放交给 layerBlock（库会反补，采样不错位），不要读成「顺手加个 graphicsLayer」
        Box(
            Modifier
                .size(glassDp)
                .align(Alignment.Center)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        // padding 供镜缘向外取样；须 ≥ edgeBend + dispersion，着色器据此换算镜内坐标
                        val pad = liquidMagnifierPadPx(this)
                        padding = pad
                        runtimeShaderEffect("LazyMagnifier", MAGNIFIER_SHADER, "content") {
                            setFloatUniform("size", size.width, size.height)
                            setFloatUniform("offset", -pad, -pad)
                            setFloatUniform("zoom", LiquidMagnifierSpec.Zoom)
                            setFloatUniform("edgeWidth", edgeWidthPx)
                            setFloatUniform("edgeBend", edgeBendPx)
                            setFloatUniform("dispersion", dispersionPx)
                        }
                    },
                    highlight = null,
                    shadow = null,
                    layerBlock = {
                        scaleX = scale
                        scaleY = scale
                    },
                )
        )

        // ③ 镜片立体感：内圈暗环（玻璃厚度）+ 左上高光弧 + 右下弱反射
        Canvas(
            Modifier
                .size(outerDp)
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            val r = metrics.glassRadius
            val cx = size.width / 2f
            val cy = size.height / 2f
            // 内圈暗环：贴着镜缘一圈渐深的阴影，读作玻璃厚度
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.82f to Color.Transparent,
                        1.0f to Color(0x40000000),
                    ),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
            )
            // 左上高光弧（主光）
            val inset = ringStrokePx * 2f
            drawArc(
                color = Color(0x66FFFFFF),
                startAngle = 186f,
                sweepAngle = 76f,
                useCenter = false,
                topLeft = Offset(cx - r + inset, cy - r + inset),
                size = Size((r - inset) * 2f, (r - inset) * 2f),
                style = Stroke(width = ringStrokePx * 2f, cap = StrokeCap.Round),
            )
            // 右下弱反射（补光，避免整圈死黑）
            drawArc(
                color = Color(0x33FFFFFF),
                startAngle = 6f,
                sweepAngle = 52f,
                useCenter = false,
                topLeft = Offset(cx - r + inset, cy - r + inset),
                size = Size((r - inset) * 2f, (r - inset) * 2f),
                style = Stroke(width = ringStrokePx, cap = StrokeCap.Round),
            )
        }
    }
}
