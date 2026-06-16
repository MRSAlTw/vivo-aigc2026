package com.aicamera.app.core.common

/**
 * 统一业务结果类型�? * �?Repository / UseCase 方法返回�?sealed class，替�?Kotlin stdlib Result<T>�? * 以便携带业务错误信息�? */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val code: ErrorCode, val message: String, val cause: Throwable? = null) : AppResult<Nothing>()
}

enum class ErrorCode {
    CAMERA_UNAVAILABLE,
    MODEL_LOAD_FAILED,
    PERMISSION_DENIED,
    TEMPLATE_NOT_FOUND,
    NETWORK_ERROR,
    VOICE_RECOGNITION_FAILED,
    UNKNOWN,
}

