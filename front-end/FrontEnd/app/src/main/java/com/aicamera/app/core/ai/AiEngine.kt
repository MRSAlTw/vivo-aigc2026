package com.aicamera.app.core.ai

import com.aicamera.app.core.camera.CameraFrame
import kotlinx.coroutines.flow.Flow

/**
 * AI 推理引擎接口。
 * 封装 MNN / TFLite 运行时，上层不关心具体引擎。
 */
interface AiEngine {

    // ────────── 模型加载 ──────────

    /** 是否已加载所有模型就绪 */
    val isReady: Boolean

    /** 异步加载所有模型（在后台线程调用） */
    suspend fun loadModels(): Result<Unit>

    // ────────── 场景分析（寻景核心） ──────────

    /**
     * YOLOv8n 场景目标检测（用于寻景 + 主体检测）。
     * 返回流式结果，每帧输出当前检测到的物体列表。
     */
    fun detectObjects(frame: CameraFrame): Flow<DetectionResult>

    /**
     * EfficientNet-Lite0 场景分类。
     * 判断当前画面属于哪类场景（室内/室外/夜景/人像等）。
     */
    suspend fun classifyScene(frame: CameraFrame): SceneType

    /**
     * NIMA-MobileNet 美学评分（0~10）。
     * 对当前构图的美学质量打分。
     */
    suspend fun assessAesthetics(frame: CameraFrame): Float

    /**
     * 综合场景分析：一次调用完成检测 + 分类 + 评分。
     * 是寻景模块的主入口。
     */
    suspend fun analyzeScene(frame: CameraFrame): SceneAnalysis

    // ────────── 姿势指导（供 Pose 模块） ──────────

    /** MoveNet 实时关节点检测 */
    fun detectPose(frame: CameraFrame): PoseResult?
}

// ══════════════════════════════════════════════
//  场景类型枚举
// ══════════════════════════════════════════════

/**
 * 场景类型（EfficientNet-Lite0 分类结果）。
 */
enum class SceneType(val displayName: String, val emoji: String) {
    PORTRAIT("人像", "🧑"),
    GROUP_PHOTO("合照", "👥"),
    LANDSCAPE("风景", "🏞️"),
    CITYSCAPE("城市", "🏙️"),
    FOOD("美食", "🍜"),
    ARCHITECTURE("建筑", "🏛️"),
    NIGHT("夜景", "🌃"),
    INDOOR("室内", "🏠"),
    NATURE("自然", "🌿"),
    STREET("街拍", "🚶"),
    PET("宠物", "🐱"),
    SUNSET("日落", "🌅"),
    BEACH("海滩", "🏖️"),
    SNOW("雪景", "❄️"),
    GENERAL("通用", "📷"),
}

// ══════════════════════════════════════════════
//  数据分析类
// ══════════════════════════════════════════════

/**
 * 综合场景分析结果。
 */
data class SceneAnalysis(
    val sceneType: SceneType = SceneType.GENERAL,
    val aestheticScore: Float = 0f,           // NIMA 0-10
    val objects: List<DetectedObject> = emptyList(),
    val isGoodShot: Boolean = false,           // 是否值得按下快门
    val guidanceText: String? = null,          // 引导文字
    val confidence: Float = 0f,                // 整体置信度
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * YOLOv8n 单帧检测结果。
 */
data class DetectionResult(
    val objects: List<DetectedObject>,
    val timestamp: Long,
)

/**
 * 单目标检测结果。
 */
data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: FloatArray, // [left, top, right, bottom] 归一化 0-1
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DetectedObject) return false
        return label == other.label && confidence == other.confidence &&
            boundingBox.contentEquals(other.boundingBox)
    }
    override fun hashCode(): Int {
        var result = label.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + boundingBox.contentHashCode()
        return result
    }
}

/**
 * MoveNet 关节点检测结果。
 */
data class PoseResult(
    val keypoints: List<KeyPoint>,
    val timestamp: Long,
)

data class KeyPoint(
    val id: Int,
    val x: Float, // 归一化 0-1
    val y: Float,
    val confidence: Float,
)
