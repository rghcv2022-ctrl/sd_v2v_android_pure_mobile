package com.haotian.sdv2v

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

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
        onProgress(0.05f, "准备输入视频...")

        val workDir = File(context.cacheDir, "sdv2v_work").apply { mkdirs() }
        val inputVideo = File(workDir, "input_${System.currentTimeMillis()}.mp4")
        val outputVideo = File(
            context.getExternalFilesDir(null),
            "sdv2v_${System.currentTimeMillis()}.mp4"
        )

        context.contentResolver.openInputStream(videoUri)?.use { input ->
            inputVideo.outputStream().use { out -> input.copyTo(out) }
        } ?: error("无法读取输入视频")

        onProgress(0.2f, "开始风格化处理...")

        val vf = buildFilter(config)
        val cmdPrimary = "-y -i \"${inputVideo.absolutePath}\" -vf \"$vf\" -c:v libx264 -preset veryfast -crf 23 -c:a copy \"${outputVideo.absolutePath}\""

        val sessionPrimary = FFmpegKit.execute(cmdPrimary)
        if (!ReturnCode.isSuccess(sessionPrimary.returnCode)) {
            // 兜底编码器，避免个别机型缺失 x264 编码
            onProgress(0.7f, "主编码失败，切换兼容模式...")
            val cmdFallback = "-y -i \"${inputVideo.absolutePath}\" -vf \"$vf\" -c:v mpeg4 -q:v 4 -c:a aac -b:a 128k \"${outputVideo.absolutePath}\""
            val sessionFallback = FFmpegKit.execute(cmdFallback)
            if (!ReturnCode.isSuccess(sessionFallback.returnCode)) {
                val failLog = sessionFallback.failStackTrace ?: sessionFallback.logsAsString
                error("视频处理失败: $failLog")
            }
        }

        onProgress(1f, "完成")
        outputVideo.absolutePath
    }

    private fun buildFilter(config: VideoJobConfig): String {
        val saturation = 1.0 + config.strength.coerceIn(0f, 1f) * 0.8
        val contrast = 1.0 + config.strength.coerceIn(0f, 1f) * 0.35
        val brightness = (config.strength.coerceIn(0f, 1f) - 0.5) * 0.06

        val base = String.format(
            Locale.US,
            "fps=%d,scale=-2:%d,eq=saturation=%.3f:contrast=%.3f:brightness=%.3f,unsharp=5:5:0.70:3:3:0.30",
            config.fps,
            config.targetHeight,
            saturation,
            contrast,
            brightness
        )

        val p = config.prompt.lowercase(Locale.getDefault())
        val style = when {
            "anime" in p || "cartoon" in p || "二次元" in p -> ",hue=s=1.25"
            "black and white" in p || "bw" in p || "黑白" in p -> ",hue=s=0"
            "warm" in p || "暖色" in p -> ",colorbalance=rs=.08:gs=.03:bs=-.03"
            "cold" in p || "cool" in p || "冷色" in p -> ",colorbalance=rs=-.03:gs=.00:bs=.08"
            else -> ""
        }

        return base + style
    }
}
