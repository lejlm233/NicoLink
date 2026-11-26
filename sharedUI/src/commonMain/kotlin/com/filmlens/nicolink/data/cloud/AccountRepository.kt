package com.filmlens.nicolink.data.cloud

import android.content.Context
import kotlinx.serialization.encodeToString

// ============================================================
// AccountRepository —— NicoLink 账号仓库
// 邮箱验证码注册 + 账号密码登录 + 邮箱绑定/改密/找回 + 会话持久化 + 续期 + 热力同步。
// 无支付宝、无 VIP、无支付、无邀请。存储用独立 SharedPreferences（nicolink_account），
// 与 lazyfilm 的 lazyfilm_account 完全隔离。
// ============================================================

/** 邮箱验证码场景 */
object EmailScene {
    const val REGISTER = "register"
    const val BIND_EMAIL = "bindEmail"
    const val RESET_PASSWORD = "resetPassword"
}

class AccountRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 本地登录态（token 存在即视为已登录，真实有效性以云端 checkToken 为准） */
    val isLoggedIn: Boolean get() = !prefs.getString(KEY_TOKEN, null).isNullOrBlank()
    val nickname: String get() = prefs.getString(KEY_NICKNAME, null).orEmpty()
    val avatar: String get() = prefs.getString(KEY_AVATAR, null).orEmpty()
    val uid: String get() = prefs.getString(KEY_UID, null).orEmpty()
    val email: String get() = prefs.getString(KEY_EMAIL, null).orEmpty()
    val hasPassword: Boolean get() = prefs.getBoolean(KEY_HAS_PASSWORD, false)
    val token: String? get() = prefs.getString(KEY_TOKEN, null)

    /**
     * 邮箱验证码注册（注册即登录）。
     * @return 用户昵称
     * @throws IllegalStateException 失败时消息可直接展示给用户
     */
    suspend fun register(email: String, code: String, password: String): String {
        val res = try {
            ApiClient.post(
                context,
                "${ApiClient.AUTH}/emailRegister",
                ApiClient.json.encodeToString(
                    EmailRegisterRequest(
                        email = email.trim(),
                        code = code.trim(),
                        password = password,
                        deviceName = ApiClient.deviceName(),
                    )
                ),
            ).getOrThrow().let { ApiClient.json.decodeFromString<LoginResponse>(it) }
        } catch (e: Exception) {
            android.util.Log.e("NicoLinkAccount", "register 异常: ${e.javaClass.simpleName}: ${e.message}", e)
            throw IllegalStateException("网络错误：${e.message ?: "请重试"}")
        }
        if (res.errCode != 0 || res.token.isNullOrBlank()) {
            throw IllegalStateException(describe(res.errMsg, res.detail, "注册失败，请重试"))
        }
        saveSession(res.token, res.tokenExpired ?: 0L, res.userInfo)
        return res.userInfo?.nickname.orEmpty().ifBlank { "NicoLink 用户" }
    }

    /**
     * 账号密码登录（邮箱或用户名 + 密码）。
     * 云端连续 5 次错密锁 15 分钟。
     * @return 用户昵称
     * @throws IllegalStateException 失败时消息可直接展示给用户
     */
    suspend fun login(username: String, password: String): String {
        val res = try {
            ApiClient.post(
                context,
                "${ApiClient.AUTH}/accountLogin",
                ApiClient.json.encodeToString(
                    AccountLoginRequest(
                        username = username.trim(),
                        password = password,
                        deviceName = ApiClient.deviceName(),
                    )
                ),
            ).getOrThrow().let { ApiClient.json.decodeFromString<LoginResponse>(it) }
        } catch (_: Exception) {
            throw IllegalStateException("网络错误，请重试")
        }
        if (res.errCode != 0 || res.token.isNullOrBlank()) {
            throw IllegalStateException(describe(res.errMsg, res.detail, "登录失败，请重试"))
        }
        saveSession(res.token, res.tokenExpired ?: 0L, res.userInfo)
        return res.userInfo?.nickname.orEmpty().ifBlank { "NicoLink 用户" }
    }

    /**
     * 启动静默续期：checkToken 通过且临近过期时云端换发 newToken。所有失败均静默。
     */
    suspend fun renewTokenIfNeeded() {
        // 测试版（Canary）的 mock 账号不走云端校验，否则假 token 会被 checkToken 判定失效并清空
        if (com.filmlens.nicolink.data.MockData.useMockAccount(context)) return
        val current = token ?: return
        try {
            val res = ApiClient.post(context, "${ApiClient.AUTH}/checkToken", "{}", token = current)
                .getOrThrow()
                .let { ApiClient.json.decodeFromString<CheckTokenResponse>(it) }
            when {
                res.errCode != 0 -> clearSession()
                !res.newToken.isNullOrBlank() -> {
                    prefs.edit()
                        .putString(KEY_TOKEN, res.newToken)
                        .putLong(KEY_TOKEN_EXPIRED, res.tokenExpired ?: 0L)
                        .apply()
                }
            }
        } catch (_: Exception) {
            // 静默：网络不可用时不打扰用户
        }
    }

    /** 登出：通知云端注销 token（失败不阻塞），清本地会话 */
    suspend fun logout() {
        try {
            ApiClient.post(context, "${ApiClient.AUTH}/logout", "{}", token = token)
        } catch (_: Exception) { /* 本地清理不受影响 */ }
        clearSession()
    }

    /**
     * 发送邮箱验证码（scene 见 [EmailScene]）。
     * 云端限流：同邮箱 10min/1 封、1h/5 封、24h/10 封，另有 IP/全局上限；
     * 命中限流时 errMsg 带剩余秒数，可直接展示。
     * @return 验证码有效期（秒）
     */
    suspend fun sendEmailCode(email: String, scene: String): Int {
        val res = try {
            ApiClient.post(
                context,
                "${ApiClient.AUTH}/sendEmailCode",
                ApiClient.json.encodeToString(
                    SendEmailCodeRequest(email = email.trim(), scene = scene)
                ),
                token = token.takeIf { scene == EmailScene.BIND_EMAIL },
            ).getOrThrow().let { ApiClient.json.decodeFromString<SendEmailCodeResponse>(it) }
        } catch (_: Exception) {
            throw IllegalStateException("网络错误，请重试")
        }
        if (res.errCode != 0) {
            throw IllegalStateException(res.errMsg.ifBlank { "验证码发送失败，请重试" })
        }
        return res.expiresIn
    }

    /** 绑定/更换登录邮箱（需登录 + 邮箱验证码） */
    suspend fun bindEmail(email: String, code: String): String {
        val res = try {
            ApiClient.post(
                context,
                "${ApiClient.AUTH}/bindEmail",
                ApiClient.json.encodeToString(
                    BindEmailRequest(email = email.trim(), code = code.trim())
                ),
                token = token,
            ).getOrThrow().let { ApiClient.json.decodeFromString<BindEmailResponse>(it) }
        } catch (_: Exception) {
            throw IllegalStateException("网络错误，请重试")
        }
        if (res.errCode != 0) {
            throw IllegalStateException(res.errMsg.ifBlank { "绑定失败，请重试" })
        }
        prefs.edit().putString(KEY_EMAIL, res.email).apply()
        return res.email
    }

    /** 设置/修改密码（从未设过直接设置；已有密码须携带旧密码） */
    suspend fun setPassword(oldPassword: String?, newPassword: String) {
        val res = try {
            ApiClient.post(
                context,
                "${ApiClient.AUTH}/setPassword",
                ApiClient.json.encodeToString(
                    SetPasswordRequest(
                        password = newPassword,
                        oldPassword = oldPassword?.takeIf { it.isNotEmpty() },
                    )
                ),
                token = token,
            ).getOrThrow().let { ApiClient.json.decodeFromString<SimpleResponse>(it) }
        } catch (_: Exception) {
            throw IllegalStateException("网络错误，请重试")
        }
        if (res.errCode != 0) {
            throw IllegalStateException(res.errMsg.ifBlank { "设置失败，请重试" })
        }
        prefs.edit().putBoolean(KEY_HAS_PASSWORD, true).apply()
    }

    /** 忘记密码：凭邮箱验证码重设（免登录），成功后旧 token 全部失效 */
    suspend fun resetPassword(email: String, code: String, newPassword: String) {
        val res = try {
            ApiClient.post(
                context,
                "${ApiClient.AUTH}/resetPassword",
                ApiClient.json.encodeToString(
                    ResetPasswordRequest(
                        email = email.trim(),
                        code = code.trim(),
                        password = newPassword,
                    )
                ),
            ).getOrThrow().let { ApiClient.json.decodeFromString<SimpleResponse>(it) }
        } catch (_: Exception) {
            throw IllegalStateException("网络错误，请重试")
        }
        if (res.errCode != 0) {
            throw IllegalStateException(describe(res.errMsg, res.detail, "重设失败，请重试"))
        }
    }

    /** 申请注销账号（30 天恢复期，成功后强制登出） */
    suspend fun deleteAccount() {
        val res = try {
            ApiClient.post(context, "${ApiClient.AUTH}/deleteAccount", "{}", token = token)
                .getOrThrow().let { ApiClient.json.decodeFromString<SimpleResponse>(it) }
        } catch (_: Exception) {
            throw IllegalStateException("网络错误，请重试")
        }
        if (res.errCode != 0) {
            throw IllegalStateException(res.errMsg.ifBlank { "注销失败，请重试" })
        }
        clearSession()
    }

    /**
     * 热力图同步：推送未同步增量 → 返回云端近 400 天全量（含其他设备）。
     * 未登录/失败时返回 null，由调用方保留本地增量待下次。
     */
    suspend fun syncHeatmap(days: Map<String, Int>): HeatmapSyncResponse? {
        if (com.filmlens.nicolink.data.MockData.useMockAccount(context)) return null
        return try {
        val current = token ?: return null
        ApiClient.post(
            context,
            "${ApiClient.AUTH}/syncHeatmap",
            ApiClient.json.encodeToString(HeatmapSyncRequest(days)),
            token = current,
        ).getOrThrow().let { ApiClient.json.decodeFromString<HeatmapSyncResponse>(it) }
            .takeIf { it.errCode == 0 }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 同步相机收藏：上报本机已连接的相机（云端 upsert），返回该用户全量收藏。
     * 收藏性质——只记录"用过什么设备"，与本机连接配置无关。未登录返回 null。
     */
    suspend fun syncCameras(cameras: List<SavedCameraDto>): List<SavedCameraDto>? {
        if (com.filmlens.nicolink.data.MockData.useMockAccount(context)) return null
        return try {
            val current = token ?: return null
            ApiClient.post(
                context,
                "${ApiClient.AUTH}/syncCameras",
                ApiClient.json.encodeToString(CameraSyncRequest(cameras)),
                token = current,
            ).getOrThrow().let { ApiClient.json.decodeFromString<CameraSyncResponse>(it) }
                .takeIf { it.errCode == 0 }?.cameras
        } catch (_: Exception) {
            null
        }
    }

    /** 移除一条相机收藏（仅云端记录，不动本机 CameraStore 连接配置）。serial 优先作身份键 */
    suspend fun removeCamera(cameraId: String, serial: String = ""): Boolean = try {
        val current = token ?: return false
        ApiClient.post(
            context,
            "${ApiClient.AUTH}/removeCamera",
            ApiClient.json.encodeToString(CameraRemoveRequest(cameraId, serial)),
            token = current,
        ).getOrThrow().let { ApiClient.json.decodeFromString<SimpleResponse>(it) }.errCode == 0
    } catch (_: Exception) {
        false
    }

    /** 刷新缓存的昵称/头像（与 saveSession 写同一组 key，登录态不受影响） */
    fun updateProfile(nickname: String, avatar: String) {
        prefs.edit()
            .putString(KEY_NICKNAME, nickname)
            .putString(KEY_AVATAR, avatar)
            .apply()
    }

    private fun saveSession(token: String, expiredAt: Long, user: CloudUserInfo?) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_TOKEN_EXPIRED, expiredAt)
            .putString(KEY_UID, user?._id.orEmpty())
            .putString(KEY_NICKNAME, user?.nickname.orEmpty())
            .putString(KEY_AVATAR, user?.avatar.orEmpty())
            .putString(KEY_EMAIL, user?.email.orEmpty())
            .putBoolean(KEY_HAS_PASSWORD, user?.hasPassword ?: false)
            .apply()
    }

    private fun clearSession() {
        prefs.edit().clear().apply()
    }

    private fun describe(errMsg: String, detail: String?, fallback: String): String {
        val base = errMsg.ifBlank { fallback }
        return if (!detail.isNullOrBlank() && detail != base) "$base（$detail）" else base
    }

    companion object {
        private const val PREFS = "nicolink_account"
        private const val KEY_TOKEN = "token"
        private const val KEY_TOKEN_EXPIRED = "token_expired"
        private const val KEY_UID = "uid"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_EMAIL = "email"
        private const val KEY_HAS_PASSWORD = "has_password"
    }
}
