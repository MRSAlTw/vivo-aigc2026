#!/usr/bin/env python3
"""
convert_yolov8n_tflite.py — YOLOv8n (.pt) → TFLite (.tflite) 转换脚本

用法:
    pip install ultralytics
    python convert_yolov8n_tflite.py

输出: model/yolov8n.tflite
"""

import os
import shutil
import sys
from pathlib import Path

# 模型输出目录（脚本所在目录的 model/ 文件夹）
MODEL_DIR = Path(__file__).parent / "model"
MODEL_DIR.mkdir(parents=True, exist_ok=True)

def main():
    print("=" * 50)
    print("YOLOv8n → TFLite 转换")
    print("=" * 50)

    # ── Step 1: 检查依赖 ──
    try:
        import ultralytics
        print(f"[OK] ultralytics {ultralytics.__version__}")
    except ImportError:
        print("[FAIL] 未安装 ultralytics，请运行: pip install ultralytics")
        sys.exit(1)

    # ── Step 2: 加载 YOLOv8n（首次自动下载 .pt ~6.3 MB）──
    print("\n[1/3] 加载 YOLOv8n 权重（首次运行会自动下载 ~6.3 MB）...")
    model = ultralytics.YOLO("yolov8n.pt")
    print("      模型加载完成")

    # ── Step 3: 导出为 TFLite ──
    print("\n[2/3] 导出为 TFLite 格式...")
    print("      格式: TFLite | 输入尺寸: 640x640 | FP16 量化")

    # export 参数说明:
    #   format="tflite" → TFLite 格式（Android 原生支持）
    #   imgsz=640       → YOLOv8n 标准输入尺寸
    #   half=True       → FP16 量化，体积减半
    model.export(
        format="tflite",
        imgsz=640,
        half=True,         # FP16 量化
    )

    # ── Step 4: 移动到 model/ 目录 ──
    print("\n[3/3] 移动到 model/ 目录...")

    # Ultralytics 导出 tflite 时输出文件名可能是:
    #   yolov8n_saved_model/yolov8n_float16.tflite
    #   或 yolov8n_float16.tflite
    candidates = list(Path(".").rglob("*.tflite"))
    if not candidates:
        # 也可能在 runs/ 下
        candidates = list(Path(".").rglob("runs/**/*.tflite"))

    if not candidates:
        print("[FAIL] 找不到 .tflite 文件，请检查导出是否成功")
        sys.exit(1)

    src = candidates[0]
    print(f"      找到: {src}")

    # 统一命名为 yolov8n.tflite
    dst = MODEL_DIR / "yolov8n.tflite"
    # 如果目标已存在，先删
    if dst.exists():
        dst.unlink()
    shutil.move(str(src), str(dst))
    file_size = dst.stat().st_size / (1024 * 1024)
    print(f"      已移动到: {dst}")
    print(f"      文件大小: {file_size:.2f} MB")

    # ── 清理临时文件 ──
    for d in ["yolov8n_saved_model", "runs"]:
        dpath = Path(d)
        if dpath.exists() and dpath.is_dir():
            shutil.rmtree(dpath)
            print(f"      已清理临时目录 ({d}/)")

    print("\n" + "=" * 50)
    print("✅ 转换完成!")
    print(f"   模型文件: {dst}")
    print("=" * 50)


if __name__ == "__main__":
    main()
