# YOLOv10 IP Stream Vision (Android APK)

This project is now an **Android app (APK)** in Kotlin with a native UI, designed for real-time IP camera stream inference on-device.

## What it does
- Connects to an MJPEG IP stream URL (`http://<ip>:<port>/video`)
- Runs **YOLOv10** detection on each frame using **ONNX Runtime Android**
- Draws live bounding boxes + labels in the app UI
- Shows estimated processing FPS and detection counts

## Snapdragon 7+ Gen 3 optimization notes
The app is structured for mobile performance:
- Uses the lightweight `yolov10n` model format by default (`yolov10n.onnx`)
- ONNX Runtime graph optimization enabled (`ALL_OPT`)
- Threading configured for balanced throughput
- Detection pipeline keeps all processing on device

For best accuracy/speed balance on Snapdragon 7+ Gen 3:
1. Export a mobile-optimized YOLOv10 ONNX model at 640 input.
2. Prefer FP16 or quantized INT8 variant if validated for your data.
3. Benchmark `yolov10s` vs `yolov10n` for your latency target (15–30 FPS stream).

## Required model asset
Place your ONNX model here:

`app/src/main/assets/yolov10n.onnx`

(Already included: `app/src/main/assets/coco.txt` labels.)

## Build APK
From project root:

```bash
./gradlew assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

## Run
1. Install APK on device.
2. Open app.
3. Paste stream URL.
4. Tap **Start**.

## Notes
- Current stream reader targets MJPEG streams.
- If your camera only provides RTSP/HLS, add a decoding layer (e.g. Media3/FFmpeg bridge) before detector input.
