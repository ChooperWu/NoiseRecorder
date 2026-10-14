package com.joeywu.noiserecorder.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joeywu.noiserecorder.service.MonitoringStatus
import com.joeywu.noiserecorder.ui.theme.DangerRed
import com.joeywu.noiserecorder.ui.theme.SafeGreen
import com.joeywu.noiserecorder.ui.theme.WarningOrange
import com.joeywu.noiserecorder.ui.viewmodels.MonitoringViewModel

@Composable
fun MonitoringScreen(
    viewModel: MonitoringViewModel,
    onRequestPermissions: (() -> Unit)? = null
) {
    val status by viewModel.status.collectAsState()
    val decibel by viewModel.decibel.collectAsState()
    val remainingSeconds by viewModel.remainingSilenceSeconds.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val isRunning = status != MonitoringStatus.IDLE

    val progress by animateFloatAsState(
        targetValue = (decibel.toFloat() / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 200),
        label = "dbProgress"
    )

    val dbColor by animateColorAsState(
        targetValue = when {
            decibel >= settings.thresholdDb -> DangerRed
            decibel >= settings.thresholdDb - 10 -> WarningOrange
            else -> SafeGreen
        },
        label = "dbColor"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 頂部狀態列
            StatusCard(status = status, remainingSeconds = remainingSeconds)

            // 中央分貝儀表
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(260.dp)
                    .padding(16.dp)
            ) {
                // 背景軌道
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 14.dp,
                    trackColor = Color.Transparent,
                )
                // 動態分貝進度
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = dbColor,
                    strokeWidth = 14.dp,
                    trackColor = Color.Transparent,
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isRunning) "${decibel}" else "--",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) dbColor else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "dB (分貝)",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "門檻：${settings.thresholdDb.toInt()} dB",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // 參數資訊摘要
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    InfoColumn(label = "噪音閥值", value = "${settings.thresholdDb.toInt()} dB")
                    InfoColumn(label = "持續停止時間", value = "${settings.silenceTimeoutMinutes} 分")
                    InfoColumn(label = "聲音格式", value = settings.audioFormat.name)
                }
            }

            // 啟動 / 停止按鈕
            Button(
                onClick = {
                    if (!isRunning && onRequestPermissions != null) {
                        onRequestPermissions()
                    }
                    viewModel.toggleMonitoring()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) DangerRed else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "停止偵測" else "啟動偵測",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun StatusCard(status: MonitoringStatus, remainingSeconds: Int) {
    val (bgColor, textColor, text) = when (status) {
        MonitoringStatus.IDLE -> Triple(Color(0xFFE0E0E0), Color(0xFF424242), "狀態：未啟動")
        MonitoringStatus.MONITORING -> Triple(SafeGreen.copy(alpha = 0.15f), SafeGreen, "狀態：環境監聽中")
        MonitoringStatus.RECORDING -> Triple(DangerRed.copy(alpha = 0.15f), DangerRed, "狀態：超過門檻 錄音中")
        MonitoringStatus.WAITING_TO_STOP -> Triple(
            WarningOrange.copy(alpha = 0.15f),
            WarningOrange,
            "狀態：低於門檻 倒數停止中 (${formatSeconds(remainingSeconds)})"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun InfoColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatSeconds(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}
