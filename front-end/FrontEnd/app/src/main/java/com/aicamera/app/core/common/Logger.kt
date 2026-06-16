package com.aicamera.app.core.common

/**
 * 统一日志接口�? * 底层封装 Timber（或 android.util.Log），各模块通过此接口打日志�? * 禁止直接调用 Log.d / println�? */
interface Logger {
    fun d(tag: String, msg: String)
    fun e(tag: String, msg: String, throwable: Throwable? = null)
    fun w(tag: String, msg: String)
}

