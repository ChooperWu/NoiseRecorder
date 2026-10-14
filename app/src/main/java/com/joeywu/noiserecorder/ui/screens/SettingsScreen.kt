package com.joeywu.noiserecorder.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joeywu.noiserecorder.data.AudioFormatType
import com.joeywu.noiserecorder.ui.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    var showChangelogDialog by remember { mutableStateOf(false) }

    // 匯出設定檔 Launcher (SAF CreateDocument)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                context.contentResolver.openOutputStream(uri)?.let { outputStream ->
                    val result = viewModel.exportSettings(outputStream)
                    if (result.isSuccess) {
                        Toast.makeText(context, "設定檔匯出成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "匯出失敗：${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // 匯入設定檔 Launcher (SAF OpenDocument)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                context.contentResolver.openInputStream(uri)?.let { inputStream ->
                    val result = viewModel.importSettings(inputStream)
                    if (result.isSuccess) {
                        Toast.makeText(context, "設定檔匯入成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "匯入失敗：格式不正確", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "偵測與錄音設定",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 1. 噪音閥值 (dB)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "噪音啟動閥值", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(
                            text = "${settings.thresholdDb.toInt()} dB",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = settings.thresholdDb.toFloat(),
                        onValueChange = { viewModel.updateThreshold(it.toDouble()) },
                        valueRange = 30f..100f,
                        steps = 69,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("30 dB (極安靜)", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        Text("100 dB (吵雜工廠)", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. 靜音持續停止時間（分鐘）
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "低於閥值持續停止時間", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(
                            text = "${settings.silenceTimeoutMinutes} 分鐘",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "低於噪音閥值時，必須持續維持低音量達指定時間才會結束錄音檔。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = settings.silenceTimeoutMinutes.toFloat(),
                        onValueChange = {
                            val rounded = (Math.round(it * 2.0) / 2.0).toDouble()
                            viewModel.updateSilenceTimeout(rounded)
                        },
                        valueRange = 0.5f..30.0f,
                        steps = 58,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0.5 分鐘 (30秒)", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        Text("30 分鐘", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. 聲音格式設定
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "錄音聲音格式", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    AudioFormatType.entries.forEach { format ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateAudioFormat(format) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.audioFormat == format,
                                onClick = { viewModel.updateAudioFormat(format) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = format.displayName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                val desc = if (format == AudioFormatType.M4A) {
                                    "高壓縮率、體積小（約 1 MB / 分）、相容所有播放器，直接播放"
                                } else {
                                    "無壓縮原始音質、零失真 PCM，適合聲學專業分析"
                                }
                                Text(text = desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. 設定檔匯入與匯出
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "設定檔管理", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "可將目前的閥值、停止時間與格式設定匯出為 JSON 檔，或從本機選取檔案匯入。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                exportLauncher.launch("noise_recorder_settings.json")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("匯出設定")
                        }

                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/*"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("匯入設定")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. 關於與版本歷程
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "關於應用程式", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "NoiseRecorder v1.0.0 (Build 1)",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        TextButton(onClick = { showChangelogDialog = true }) {
                            Text("版本歷程")
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "支援系統：Android 13+ (API 33+)\n架構：Kotlin / Jetpack Compose / Material 3",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // 版本歷程對話框
        if (showChangelogDialog) {
            AlertDialog(
                onDismissRequest = { showChangelogDialog = false },
                title = { Text("版本更新歷程") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "v1.0.0 (2026-10-14)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• 起始畫面：實時分貝量條、狀態指示燈、噪音超標自動錄音、靜音緩衝計時機制。\n" +
                                   "• 前台服務：支援螢幕關閉與背景持續監測，通知欄提供即時分貝與停止快捷。\n" +
                                   "• 錄音管理：檔案列表、內建播放器（播放/暫停/Seek）、單檔改名/複製/刪除、多選批次管理。\n" +
                                   "• 設定功能：噪音閥值與停止時間滑桿微調、M4A 與 WAV 格式切換、設定檔 JSON 匯出與匯入（SAF）。",
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showChangelogDialog = false }) {
                        Text("關閉")
                    }
                }
            )
        }
    }
}
