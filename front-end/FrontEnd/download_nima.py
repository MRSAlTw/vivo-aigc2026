#!/usr/bin/env python3
"""
download_nima.py — 下载 NIMA-MobileNet 美学评分模型并转为 TFLite

模型来源: GitHub — titu1994/neural-image-assessment
架构: MobileNetV1 + Dropout(0.75) + Dense(10, softmax)
训练集: AVA (Aesthetic Visual Analysis)
输入: (1, 224, 224, 3) float32 [-1, 1]
输出: (1, 10) 美学评分分布（1-10 分各自的概率）

用法:
    pip install tensorflow
    python download_nima.py

输出: model/nima_mobilenet.tflite
"""

import shutil
import sys
import urllib.request
from pathlib import Path

import numpy as np
import tensorflow as tf

# 模型输出目录
MODEL_DIR = Path(__file__).parent / "model"
MODEL_DIR.mkdir(parents=True, exist_ok=True)

# GitHub Release 上的预训练权重
WEIGHTS_URL = (
    "https://github.com/titu1994/neural-image-assessment/releases/"
    "download/v0.3/mobilenet_weights.h5"
)
WEIGHTS_FILE = Path(__file__).parent / "nima_mobilenet_weights.h5"


def download_weights():
    """从 GitHub 下载预训练权重"""
    print("\n[1/4] 下载 NIMA MobileNet 预训练权重...")
    print(f"      来源: {WEIGHTS_URL}")

    if WEIGHTS_FILE.exists():
        size_mb = WEIGHTS_FILE.stat().st_size / (1024 * 1024)
        print(f"      已存在，跳过下载 ({size_mb:.1f} MB)")
        return

    print("      下载中 (约 12.6 MB)...")
    try:
        req = urllib.request.Request(WEIGHTS_URL, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=60) as resp:
            total = int(resp.headers.get("Content-Length", 0))
            data = resp.read()

        WEIGHTS_FILE.write_bytes(data)
        print(f"      下载完成: {len(data) / (1024 * 1024):.1f} MB")
    except Exception as e:
        print(f"[FAIL] 下载失败: {e}")
        sys.exit(1)


def build_nima_model():
    """
    构建 NIMA-MobileNet 模型（与 titu1994 原始结构完全一致，确保权重匹配）。

    架构: MobileNetV1 → GlobalAvgPool → Dropout(0.75) → Dense(10, softmax)
    输入值域: [-1, 1]（预处理在 Android 端做: x * 2.0 - 1.0）
    """
    print("\n[2/4] 构建 NIMA 模型架构...")

    # 与 titu1994/neural-image-assessment 完全一致的构建方式
    base_model = tf.keras.applications.MobileNet(
        input_shape=(224, 224, 3),
        alpha=1.0,
        include_top=False,
        pooling="avg",
        weights=None,
    )
    base_model.trainable = False

    x = base_model.output
    x = tf.keras.layers.Dropout(0.75, name="dropout")(x)
    outputs = tf.keras.layers.Dense(10, activation="softmax", name="scores")(x)

    model = tf.keras.Model(inputs=base_model.input, outputs=outputs, name="nima_mobilenet")
    print(f"      模型构建完成 (总层数: {len(model.layers)})")
    return model


def load_weights_and_convert(model):
    """加载预训练权重，转为 TFLite FP16"""
    print("\n[3/4] 加载权重 + 转换 TFLite FP16...")

    # 加载 .h5 权重
    model.load_weights(str(WEIGHTS_FILE))
    print("      权重加载完成")

    # 验证输出（输入值域 [-1, 1]）
    test_input = (np.random.rand(1, 224, 224, 3).astype(np.float32) * 2.0) - 1.0
    test_output = model(test_input, training=False)
    print(f"      Keras 输出形状: {test_output.shape}  (应为 (1, 10))")
    mean_score = np.sum(np.arange(1, 11) * test_output.numpy()[0])
    print(f"      Keras 示例评分: {mean_score:.2f} / 10")

    # 转换 TFLite FP16
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]

    tflite_model = converter.convert()
    size_mb = len(tflite_model) / (1024 * 1024)
    print(f"      TFLite 转换完成，大小: {size_mb:.2f} MB")

    # 验证 TFLite 输出与 Keras 一致
    interpreter = tf.lite.Interpreter(model_content=tflite_model)
    interpreter.allocate_tensors()
    input_idx = interpreter.get_input_details()[0]["index"]
    output_idx = interpreter.get_output_details()[0]["index"]

    interpreter.set_tensor(input_idx, test_input)
    interpreter.invoke()
    tflite_output = interpreter.get_tensor(output_idx)
    tflite_mean = np.sum(np.arange(1, 11) * tflite_output[0])
    print(f"      TFLite 示例评分: {tflite_mean:.2f} / 10  (应与上面一致)")

    return tflite_model


def main():
    print("=" * 50)
    print("NIMA-MobileNet → TFLite")
    print("=" * 50)

    # Step 1: 下载权重
    download_weights()

    # Step 2: 构建模型
    model = build_nima_model()

    # Step 3: 加载权重 + 转 TFLite
    tflite_model = load_weights_and_convert(model)

    # Step 4: 保存
    print("\n[4/4] 保存模型...")
    dst = MODEL_DIR / "nima_mobilenet.tflite"
    if dst.exists():
        backup = MODEL_DIR / "nima_mobilenet.tflite.bak"
        shutil.move(str(dst), str(backup))
        print(f"      已备份旧模型: {backup.name}")

    dst.write_bytes(tflite_model)
    print(f"      已保存: {dst}")
    print(f"      文件大小: {dst.stat().st_size / (1024 * 1024):.2f} MB")

    # 清理权重文件
    if WEIGHTS_FILE.exists():
        WEIGHTS_FILE.unlink()
        print(f"      已清理临时文件: {WEIGHTS_FILE.name}")

    print("\n" + "=" * 50)
    print("✅ NIMA 模型下载转换完成!")
    print(f"   模型文件: {dst}")
    print(f"   推理输入: (1, 224, 224, 3) float32 [-1, 1]")
    print(f"   推理输出: (1, 10) → 分数 = Σ(i × prob_i)")
    print("=" * 50)


if __name__ == "__main__":
    main()
