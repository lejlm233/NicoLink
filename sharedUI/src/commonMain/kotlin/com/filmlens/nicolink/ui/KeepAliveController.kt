package com.filmlens.nicolink.ui

/**
 * 保活服务控制抽象：commonMain 只依赖这个函数钩子，
 * 真实实现（androidApp 的前台服务）由 MainActivity 注入，避免 commonMain 反向依赖 androidApp。
 */
object KeepAliveController {
    var start: () -> Unit = {}
    var stop: () -> Unit = {}
}
