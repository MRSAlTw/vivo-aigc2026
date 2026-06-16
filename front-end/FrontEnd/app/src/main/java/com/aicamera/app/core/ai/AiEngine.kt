package com.aicamera.app.core.ai

import com.aicamera.app.core.camera.CameraFrame
import kotlinx.coroutines.flow.Flow

/**
 * AI 推理引擎接口。
 * 封装 MNN / TFLite 运行时，上层不关心具体引擎。
 */
interface AiEngine {

    /** YOLOv8n 场景目标检测（用于寻景 + 主体检测） */
    fun detectObjects(frame: CameraFrame): Flow<DetectionResult>

    /** MoveNet 实时关节点检测（用于姿势指导） */
    fun detectPose(frame: CameraFrame): PoseResult?

    /** NIMA 美学评分（预留自学习） */
    suspend fun assessAesthetics(frame: CameraFrame): Float
}

data class DetectionResult(
    val objects: List<DetectedObject>,
    val timestamp: Long,
)

data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: FloatArray, // [left, top, right, bottom] 归一化 0-1
)

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
