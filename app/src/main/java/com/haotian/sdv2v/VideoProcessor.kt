package com.haotian.sdv2v

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jcodec.api.android.AndroidFrameGrab
import org.jcodec.api.android.AndroidSequenceEncoder
import org.jcodec.common.io.NIOUtils
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

        val durationSec = readDurationSec(context, videoUri)
        onProgress(0.15f, "开始逐帧处理...")

        val channel = NIOUtils.readableChannel(inputVideo)
        var encoder: AndroidSequenceEncoder? = null
        var frameCount = 0

        try {
            val grab = AndroidFrameGrab.createAndroidFrameGrab(channel)
            encoder = AndroidSequenceEncoder.createSequenceEncoder(outputVideo, config.fps)

            while (true) {
                val frameMeta = grab.getFrameWithMetadata() ?: break
                val inBmp = frameMeta.bitmap ?: break
                val outBmp = stylizeBitmap(inBmp, config)
                encoder.encodeImage(outBmp)
                if (outBmp !== inBmp && !outBmp.isRecycled) outBmp.recycle()

                frameCount++
                val ts = frameMeta.timestamp
                val inner = when {
                    durationSec > 0.0 -> (ts / durationSec).toFloat().coerceIn(0f, 1f)
                    else -> (frameCount / 240f).coerceAtMost(1f)
                }
                onProgress(0.15f + inner * 0.8f, "处理中：$frameCount 帧")
            }
        } finally {
            runCatching { encoder?.finish() }
            NIOUtils.closeQuietly(channel)
        }

        if (frameCount == 0) error("未解码到有效视频帧")

        onProgress(1f, "完成")
        outputVideo.absolutePath
    }

    private fun readDurationSec(context: Context, uri: Uri): Double {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toDoubleOrNull() ?: 0.0
            ms / 1000.0
        } catch (_: Exception) {
            0.0
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun stylizeBitmap(input: Bitmap, config: VideoJobConfig): Bitmap {
        val p = config.prompt.lowercase(Locale.getDefault())
        val strength = config.strength.coerceIn(0f, 1f)

        val saturation = when {
            "black and white" in p || "bw" in p || "黑白" in p -> 0f
            else -> 1f + 0.8f * strength
        }
        val contrast = 1f + 0.35f * strength
        val brightness = (strength - 0.5f) * 0.08f

        val color = ColorMatrix().apply { setSaturation(saturation) }

        val t = ((-0.5f * contrast + 0.5f) + brightness) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, t,
                0f, contrast, 0f, 0f, t,
                0f, 0f, contrast, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
        color.postConcat(contrastMatrix)

        if ("warm" in p || "暖色" in p) {
            color.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.07f, 0f, 0f, 0f, 8f,
                        0f, 1.02f, 0f, 0f, 2f,
                        0f, 0f, 0.94f, 0f, -6f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        } else if ("cool" in p || "cold" in p || "冷色" in p) {
            color.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        0.95f, 0f, 0f, 0f, -6f,
                        0f, 1.00f, 0f, 0f, 0f,
                        0f, 0f, 1.08f, 0f, 9f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

        val out = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(color)
        }
        canvas.drawBitmap(input, 0f, 0f, paint)
        return out
    }
}
