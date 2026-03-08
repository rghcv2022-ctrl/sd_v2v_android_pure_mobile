# SD Video2Video (Pure Android, Offline)

这是一个**纯手机端离线** APK 工程骨架：
- 本地选择视频
- 本地抽帧（FFmpegKit）
- 逐帧推理（`SdEngine`）
- 本地编码回 MP4

> 当前默认 `PassThroughSdEngine` 只是打通流程，不做真实 Stable Diffusion 推理。你需要把 `SdEngine` 换成真实模型实现（ONNX / NCNN / MNN / TFLite 任一）。

---

## 1) 环境

- Android Studio Iguana+（建议最新版）
- JDK 17
- Android SDK 34

## 2) 打开工程

打开目录：`outputs/sd_v2v_android_pure_mobile`

## 3) 运行

- 连接手机（iQOO Neo10）
- Run `app`
- 选择视频
- 填 Prompt
- 点击“开始离线生成”

输出路径在 App 私有目录：
`/Android/data/com.haotian.sdv2v/files/sdv2v_xxx.mp4`

---

## 4) 接入真实 Stable Diffusion（关键）

把 `app/src/main/java/com/haotian/sdv2v/SdEngine.kt` 中 `PassThroughSdEngine` 改成真实推理器。

推荐两条路线：

### 路线 A（推荐上手）：ONNX Runtime Mobile + SD-Turbo
- 模型：`sd-turbo` 的 image-to-image 版本（或蒸馏模型）
- 优点：生态成熟、Java/Kotlin 接入简单
- 缺点：模型拆分和预后处理要自己做

### 路线 B（更高性能）：NCNN + JNI
- 用 C++ 在 JNI 里跑 UNet/VAE/TextEncoder
- 优点：移动端性能好
- 缺点：工程复杂度高

---

## 5) 适配你这台设备（iQOO Neo10）建议参数

- 输出：480p
- FPS：6（1 分钟 = 360 帧）
- `keyframeStride=1`（质量优先）
- 如果太慢：改成 `keyframeStride=2`（速度快约 1.6~1.9x）

---

## 6) 下一步（我建议）

1. 先跑通这个工程（确认视频 I/O 全正常）
2. 我再给你补 `OnnxSdTurboEngine.kt` 真推理版本
3. 再加“温控/后台任务/断点续跑”

