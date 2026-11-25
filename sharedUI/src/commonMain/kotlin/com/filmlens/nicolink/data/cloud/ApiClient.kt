package com.filmlens.nicolink.data.cloud

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

// ============================================================
// ApiClient —— NicoLink 专用 uniCloud 云对象 URL 化 REST 客户端
//
// 从 lazyfilm ApiClient 裁剪而来：APP_ID 改 nicolink，云对象路径前缀
// nicolink-auth，砍掉支付宝相关调用。协议与 takeone/lazyfilm 完全一致
// （POST JSON + device-id / X-App-Id / uni-id-token 三头）。
// ============================================================
object ApiClient {

    /** 主域（云函数 URL 化自定义域，自定义域不占全平台共享限流池） */
    const val BASE_URL = "https://api.takeoneai.cn/api"

    /** 备用域（云空间默认域，主域备案/网络异常时兜底） */
    const val FALLBACK_BASE_URL = "https://env-00jy6tbeqscv.dev-hz.cloudbasefunction.cn/api"

    /** 多应用身份头：nicolink 空间，与 lazyfilm / takeone 完全隔离 */
    const val APP_ID = "nicolink"

    /** NicoLink 专属登录云对象 URL 化路径前缀 */
    const val AUTH = "nicolink-auth"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // ── 设备标识：首启生成 UUID 持久化（独立 prefs，不与 lazyfilm 串用） ──

    private const val DEVICE_PREFS = "nicolink_device"

    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(DEVICE_PREFS, Context.MODE_PRIVATE)
        prefs.getString("device_id", null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString("device_id", id).apply()
        return id
    }

    /** 设备展示名（user_devices 表登记用） */
    fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    // ── HTTP ──

    /**
     * POST /<path>（相对 BASE_URL），body 为 JSON 字符串。
     * 成功返回响应体文本；HTTP 非 2xx 且无响应体时抛错，有响应体时交由上层解析 errCode。
     */
    suspend fun post(
        context: Context,
        path: String,
        body: String,
        token: String? = null,
        baseUrl: String = BASE_URL,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (baseUrl.isBlank()) throw IllegalStateException("云端服务未配置")
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/$path")
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("device-id", deviceId(context))
                .header("X-App-Id", APP_ID)
                .apply { if (!token.isNullOrBlank()) header("uni-id-token", token) }
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful && text.isBlank()) throw IllegalStateException("网络错误（HTTP ${resp.code}）")
                text
            }
        }.onFailure { e ->
            android.util.Log.e("NicoLinkApi", "POST $path 失败: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }
}
