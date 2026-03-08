package com.haotian.sdv2v

import android.graphics.Bitmap

/**
 * 你可以把这里替换成真实 SD Turbo / SD1.5 img2img 推理实现。
 * 当前默认实现是透传（便于先打通视频流程）。
 */
interface SdEngine {
    suspend fun stylize(
        input: Bitmap,
        prompt: String,
        negativePrompt: String,
        strength: Float,
        seed: Int,
    ): Bitmap
}

class PassThroughSdEngine : SdEngine {
    override suspend fun stylize(
        input: Bitmap,
        prompt: String,
        negativePrompt: String,
        strength: Float,
        seed: Int,
    ): Bitmap {
        return input.copy(input.config ?: Bitmap.Config.ARGB_8888, false)
    }
}
