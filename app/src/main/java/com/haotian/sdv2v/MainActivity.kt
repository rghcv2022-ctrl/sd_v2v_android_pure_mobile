package com.haotian.sdv2v

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                AppScreen(
                    onToast = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
                )
            }
        }
    }
}

@Composable
private fun AppScreen(onToast: (String) -> Unit) {
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var prompt by remember { mutableStateOf("cinematic, detailed, realistic lighting") }
    var negativePrompt by remember { mutableStateOf("low quality, blurry, artifacts") }
    var strengthText by remember { mutableStateOf("0.45") }
    var fpsText by remember { mutableStateOf("6") }
    var keyStrideText by remember { mutableStateOf("1") }
    var processing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("等待选择视频") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            videoUri = uri
            status = "已选择视频"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("SD 视频转视频（离线本地）", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("1) 选择视频")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }) {
                        Text("选择视频")
                    }
                    Text(if (videoUri == null) "未选择" else "已选择")
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Prompt") }
                )

                OutlinedTextField(
                    value = negativePrompt,
                    onValueChange = { negativePrompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Negative Prompt") }
                )

                OutlinedTextField(
                    value = strengthText,
                    onValueChange = { strengthText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Strength (0~1)") }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = fpsText,
                        onValueChange = { fpsText = it },
                        label = { Text("FPS") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = keyStrideText,
                        onValueChange = { keyStrideText = it },
                        label = { Text("关键帧步长") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    enabled = !processing && videoUri != null,
                    onClick = {
                        val uri = videoUri ?: return@Button
                        val strength = strengthText.toFloatOrNull() ?: 0.45f
                        val fps = fpsText.toIntOrNull() ?: 6
                        val keyStride = keyStrideText.toIntOrNull() ?: 1
                        val cfg = VideoJobConfig(
                            prompt = prompt,
                            negativePrompt = negativePrompt,
                            strength = strength.coerceIn(0f, 1f),
                            fps = fps.coerceIn(3, 12),
                            keyframeStride = keyStride.coerceAtLeast(1),
                            targetHeight = 480
                        )

                        processing = true
                        progress = 0f
                        status = "开始处理..."

                        scope.launch {
                            val processor = VideoProcessor(PassThroughSdEngine())
                            runCatching {
                                processor.process(
                                    context = context,
                                    videoUri = uri,
                                    config = cfg,
                                    onProgress = { p, s ->
                                        progress = p
                                        status = s
                                    }
                                )
                            }.onSuccess { output ->
                                status = "完成：$output"
                                onToast("处理完成，输出文件：$output")
                            }.onFailure { e ->
                                status = "失败：${e.message}"
                                onToast("处理失败：${e.message}")
                            }
                            processing = false
                        }
                    }
                ) {
                    Text(if (processing) "处理中..." else "开始离线生成")
                }
            }
        }

        Text("状态：$status")
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text("进度 ${(progress * 100).toInt()}%")
    }
}
