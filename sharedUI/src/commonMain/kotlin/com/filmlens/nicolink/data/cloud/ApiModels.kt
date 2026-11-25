package com.filmlens.nicolink.data.cloud

import kotlinx.serialization.Serializable

// ============================================================
// NicoLink 云端 DTO —— 与 nicolink-auth 云对象统一响应信封 { errCode, errMsg, ... }
// errCode=0 成功。只保留账号 + 热力同步相关；无 VIP / 订单 / 邀请 / 支付宝。
// ============================================================

/** 用户信息（云端下发，不含敏感字段；vip 字段被 ignoreUnknownKeys 忽略） */
@Serializable
data class CloudUserInfo(
    val _id: String = "",
    val nickname: String = "",
    val avatar: String = "",
    /** 已绑定的登录邮箱（未绑定为空串） */
    val email: String = "",
    /** 是否已设置密码 */
    val hasPassword: Boolean = false,
)

// ── auth/accountLogin（邮箱或用户名 + 密码） ──

@Serializable
data class AccountLoginRequest(
    val username: String,
    val password: String,
    val deviceName: String,
)

// ── auth/emailRegister（邮箱验证码注册，注册即登录） ──

@Serializable
data class EmailRegisterRequest(
    val email: String,
    val code: String,
    val password: String,
    val deviceName: String,
)

/** 注册 / 登录共用的成功响应 */
@Serializable
data class LoginResponse(
    val errCode: Int = -1,
    val errMsg: String = "",
    val token: String? = null,
    val tokenExpired: Long? = null,
    val userInfo: CloudUserInfo? = null,
    val detail: String? = null,
)

// ── auth/checkToken ──

@Serializable
data class CheckTokenResponse(
    val errCode: Int = -1,
    val errMsg: String = "",
    val uid: String? = null,
    val isValid: Boolean = false,
    val newToken: String? = null,
    val tokenExpired: Long? = null,
)

// ── auth/sendEmailCode ──

@Serializable
data class SendEmailCodeRequest(
    val email: String,
    /** register=注册 bindEmail=绑定邮箱 resetPassword=重设密码 */
    val scene: String,
)

@Serializable
data class SendEmailCodeResponse(
    val errCode: Int = -1,
    val errMsg: String = "",
    /** 验证码有效期（秒） */
    val expiresIn: Int = 0,
)

// ── auth/bindEmail ──

@Serializable
data class BindEmailRequest(
    val email: String,
    val code: String,
)

@Serializable
data class BindEmailResponse(
    val errCode: Int = -1,
    val errMsg: String = "",
    val email: String = "",
)

// ── auth/setPassword · auth/resetPassword ──

@Serializable
data class SetPasswordRequest(
    val password: String,
    val oldPassword: String? = null,
)

@Serializable
data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val password: String,
)

// ── 通用空响应（logout / setPassword / resetPassword / deleteAccount） ──

@Serializable
data class SimpleResponse(
    val errCode: Int = -1,
    val errMsg: String = "",
    val detail: String? = null,
)

// ── auth/syncHeatmap（NicoLink 专属集合 nicolink_heatmap） ──

@Serializable
data class HeatmapSyncRequest(
    /** { "yyyy-MM-dd" -> 当日增量 } */
    val days: Map<String, Int>,
)

@Serializable
data class HeatmapSyncResponse(
    val errCode: Int = -1,
    val errMsg: String = "",
    /** 近 400 天全量（含其他设备） */
    val days: Map<String, Int> = emptyMap(),
)

// ── auth/syncCameras · auth/removeCamera（相机收藏，记录用户用过的设备） ──

@Serializable
data class SavedCameraDto(
    val id: String = "",
    val name: String = "",
    val host: String = "",
    val serial: String = "",
    val last_connected_at: Long = 0L,
    /** 以下为陈列架展示字段，旧版本服务端缺省时保持默认值 */
    val brand: String = "",
    val model: String = "",
    /** 累计连接时长（毫秒），断开时累加并上报 */
    val total_connected_ms: Long = 0L,
    /** 快门次数，相机返回时记录（暂无实机时为 0） */
    val shutter_count: Long = 0L,
)

@Serializable
data class CameraSyncRequest(
    val cameras: List<SavedCameraDto>,
)

@Serializable
data class CameraSyncResponse(
    val errCode: Int = -1,
    val errMsg: String = "",
    /** 全量收藏（按最近使用倒序） */
    val cameras: List<SavedCameraDto> = emptyList(),
)

@Serializable
data class CameraRemoveRequest(
    val camera_id: String,
    /** 序列号优先作身份键（为空时用 camera_id） */
    val serial: String = "",
)

// ── biz/checkUpdate（免登录；走 lazyfilm-biz 云对象，按 X-App-Id: nicolink 隔离） ──

@Serializable
data class CheckUpdateResponse(
    val errCode: Int = -1,
    val errMsg: String = "",
    val latest: AppLatestVersion? = null,
)

@Serializable
data class AppLatestVersion(
    /** 云端最新 versionCode；App 按它比对（versionName 只用于展示） */
    val versionCode: Int = 0,
    val versionName: String = "",
    /** 下载地址；留空时弹窗退化为「知道了」，填上后按钮变「去更新」 */
    val downloadUrl: String = "",
    /** 备用下载地址；留空时弹窗不显示备用入口 */
    val downloadUrlBackup: String = "",
    /** 更新说明（可空，多行原样展示） */
    val notes: String = "",
    /** 强制更新：true 时弹窗不可关闭、只留「立即更新」 */
    val force: Boolean = false,
)
