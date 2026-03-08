package com.haotian.sdv2v

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

data class VideoJobConfig(
    val prompt: String,
    val negativePrompt: String,
    val strength: Float,
    val fps: Int,
    val keyframeStride: Int,
    val targetHeight: Int,
    val seed: Int = 42,
)

class VideoProcessor(private val engine: SdEngine) {

    suspend fun process(
        context: Context,
        videoUri: Uri,
        config: VideoJobConfig,
        onProgress: (progress: Float, status: String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        // 当前 APK 先提供可安装/可运行版本：
        // 输入视频会复制到输出路径，作为流程占位。
        // 后续可替换成真实逐帧 SD 推理与重编码。
        onProgress(0.05f, "读取视频...")

        val outputVideo = File(
            context.getExternalFilesDir(null),
            "sdv2v_${System.currentTimeMillis()}.mp4"
        )

        context.contentResolver.openInputStream(videoUri)?.use { input ->
            outputVideo.outputStream().use { out -> input.copyTo(out) }
        } ?: error("无法读取输入视频")

        for (i in 1..20) {
            delay(80)
            onProgress(0.05f + i * 0.045f, "处理中 $i/20")
        }

        onProgress(1f, "完成")
        outputVideo.absolutePath
    }
}
