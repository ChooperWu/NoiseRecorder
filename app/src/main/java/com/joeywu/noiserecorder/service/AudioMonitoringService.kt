package com.joeywu.noiserecorder.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.joeywu.noiserecorder.MainActivity
import com.joeywu.noiserecorder.NoiseRecorderApp
import com.joeywu.noiserecorder.R
import com.joeywu.noiserecorder.audio.IAudioWriter
import com.joeywu.noiserecorder.data.AppPreferences
import com.joeywu.noiserecorder.data.AudioFormatType
import com.joeywu.noiserecorder.data.FileManager
import com.joeywu.noiserecorder.data.SettingsConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.sqrt

enum class MonitoringStatus(val displayText: String) {
    IDLE("未啟動"),
    MONITORING("環境監聽中"),
    RECORDING("噪音錄音中"),
    WAITING_TO_STOP("低於閾值倒數停止中")
}

class AudioMonitoringService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): AudioMonitoringService = this@AudioMonitoringService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private var audioRecord: AudioRecord? = null
    private var isRecordingLoopActive = false
    private var recordThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var currentWriter: IAudioWriter? = null
    private var currentRecordingFile: File? = null
    private var silenceDeadlineMs: Long = 0L

    private lateinit var appPreferences: AppPreferences
    private lateinit var fileManager: FileManager
    private var currentSettings = SettingsConfig()

    private var notificationJob: Job? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_MONITORING = "com.joeywu.noiserecorder.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.joeywu.noiserecorder.STOP_MONITORING"

        private val _status = MutableStateFlow(MonitoringStatus.IDLE)
        val status: StateFlow<MonitoringStatus> = _status.asStateFlow()

        private val _decibel = MutableStateFlow(0.0)
        val decibel: StateFlow<Double> = _decibel.asStateFlow()

        private val _remainingSilenceSeconds = MutableStateFlow(0)
        val remainingSilenceSeconds: StateFlow<Int> = _remainingSilenceSeconds.asStateFlow()

        private val _fileSavedEvent = MutableStateFlow<String?>(null)
        val fileSavedEvent: StateFlow<String?> = _fileSavedEvent.asStateFlow()

        fun isRunning(): Boolean = _status.value != MonitoringStatus.IDLE
    }

    override fun onCreate() {
        super.onCreate()
        appPreferences = AppPreferences(applicationContext)
        fileManager = FileManager(applicationContext)

        serviceScope.launch {
            appPreferences.settingsFlow.collect { settings ->
                currentSettings = settings
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    fun startMonitoring() {
        if (isRecordingLoopActive) return

        startForegroundServiceNotification()

        // 獲取 WakeLock 避免螢幕關閉時 CPU 進入休眠
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "NoiseRecorder:MonitoringWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {}

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                stopMonitoring()
                return
            }

            audioRecord?.startRecording()
            isRecordingLoopActive = true
            _status.value = MonitoringStatus.MONITORING

            startAudioProcessingLoop(bufferSize, sampleRate)
            startNotificationUpdater()
        } catch (_: Exception) {
            stopMonitoring()
        }
    }

    private fun startAudioProcessingLoop(bufferSize: Int, sampleRate: Int) {
        recordThread = Thread {
            val audioBuffer = ShortArray(bufferSize / 2)
            var smoothedDb = 40.0

            while (isRecordingLoopActive) {
                val record = audioRecord ?: break
                val readShorts = record.read(audioBuffer, 0, audioBuffer.size)

                if (readShorts > 0) {
                    // 計算 RMS 與分貝
                    var sum = 0.0
                    for (i in 0 until readShorts) {
                        val sample = audioBuffer[i].toDouble()
                        sum += sample * sample
                    }
                    val rms = sqrt(sum / readShorts)
                    val rawDb = if (rms > 1.0) {
                        20.0 * log10(rms / 32767.0) + 90.0
                    } else {
                        0.0
                    }.coerceIn(0.0, 120.0)

                    // 平滑化濾波
                    smoothedDb = 0.3 * rawDb + 0.7 * smoothedDb
                    _decibel.value = (smoothedDb * 10).toInt() / 10.0

                    handleAudioLogic(audioBuffer, readShorts, smoothedDb, sampleRate)
                }
            }

            // 確保退出時關閉正在寫入的錄音
            stopCurrentWriter()
        }.apply {
            name = "NoiseRecordThread"
            start()
        }
    }

    private fun handleAudioLogic(
        buffer: ShortArray,
        readShorts: Int,
        currentDb: Double,
        sampleRate: Int
    ) {
        val threshold = currentSettings.thresholdDb
        val silenceMinutes = currentSettings.silenceTimeoutMinutes

        when (_status.value) {
            MonitoringStatus.MONITORING -> {
                if (currentDb >= threshold) {
                    // 超過閾值，開始錄音
                    startNewRecording(sampleRate)
                    currentWriter?.write(buffer, readShorts)
                    _status.value = MonitoringStatus.RECORDING
                    _remainingSilenceSeconds.value = 0
                }
            }

            MonitoringStatus.RECORDING -> {
                currentWriter?.write(buffer, readShorts)
                if (currentDb < threshold) {
                    // 低於閾值，進入倒數狀態
                    silenceDeadlineMs = System.currentTimeMillis() + (silenceMinutes * 60 * 1000).toLong()
                    _status.value = MonitoringStatus.WAITING_TO_STOP
                }
            }

            MonitoringStatus.WAITING_TO_STOP -> {
                currentWriter?.write(buffer, readShorts)
                if (currentDb >= threshold) {
                    // 倒數期間噪音再次超過閾值，取消倒數，恢復錄音
                    _status.value = MonitoringStatus.RECORDING
                    _remainingSilenceSeconds.value = 0
                } else {
                    val remainingMs = silenceDeadlineMs - System.currentTimeMillis()
                    if (remainingMs <= 0) {
                        // 倒數結束，停止錄音並儲存
                        stopCurrentWriter()
                        _status.value = MonitoringStatus.MONITORING
                        _remainingSilenceSeconds.value = 0
                    } else {
                        _remainingSilenceSeconds.value = ((remainingMs + 999) / 1000).toInt()
                    }
                }
            }

            MonitoringStatus.IDLE -> {}
        }
    }

    private fun startNewRecording(sampleRate: Int) {
        try {
            val dir = fileManager.getRecordingsDir()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val format = currentSettings.audioFormat
            val file = File(dir, "REC_${timeStamp}.${format.extension}")
            currentRecordingFile = file

            val writer = IAudioWriter.create(file, format, sampleRate)
            writer.start()
            currentWriter = writer
        } catch (_: Exception) {
            currentWriter = null
            currentRecordingFile = null
        }
    }

    private fun stopCurrentWriter() {
        val writer = currentWriter ?: return
        val file = currentRecordingFile
        try {
            writer.stop()
            if (file != null && file.exists() && file.length() > 0) {
                _fileSavedEvent.value = file.name
            }
        } catch (_: Exception) {}
        currentWriter = null
        currentRecordingFile = null
    }

    fun stopMonitoring() {
        isRecordingLoopActive = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        stopCurrentWriter()

        // 釋放 WakeLock
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null

        _status.value = MonitoringStatus.IDLE
        _decibel.value = 0.0
        _remainingSilenceSeconds.value = 0

        notificationJob?.cancel()
        notificationJob = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startForegroundServiceNotification() {
        val notification = buildNotification("服務啟動中...", 0.0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(statusText: String, db: Double): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AudioMonitoringService::class.java).apply {
                action = ACTION_STOP_MONITORING
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "噪音偵測：${"%.1f".format(db)} dB"
        return NotificationCompat.Builder(this, NoiseRecorderApp.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.action_stop), stopIntent)
            .build()
    }

    private fun startNotificationUpdater() {
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            while (isActive && isRecordingLoopActive) {
                val currentStatus = _status.value
                val db = _decibel.value
                val statusText = when (currentStatus) {
                    MonitoringStatus.IDLE -> "已停止"
                    MonitoringStatus.MONITORING -> "監聽中（低於閾值 ${currentSettings.thresholdDb.toInt()} dB）"
                    MonitoringStatus.RECORDING -> "錄音中（超過閾值 ${currentSettings.thresholdDb.toInt()} dB）"
                    MonitoringStatus.WAITING_TO_STOP -> "倒數停止中（剩餘 ${_remainingSilenceSeconds.value} 秒）"
                }
                notificationManager.notify(NOTIFICATION_ID, buildNotification(statusText, db))
                delay(1500)
            }
        }
    }

    override fun onDestroy() {
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }
}
