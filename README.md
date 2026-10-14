# NoiseRecorder (Android 13+)

專為 Android 13（API 33）及以上版本設計的智慧噪音偵測與自動錄音應用程式。

當前版本：`v1.0.1`（詳細歷程請參閱 [CHANGELOG.md](CHANGELOG.md)）

## 功能特點

1. **起始 / 偵測畫面**
   - 即時計算環境分貝（dB）並以動態量條呈現（0 dB ~ 100 dB）。
   - 四段狀態指示：未啟動、環境監聽中、噪音錄音中、低於閾值倒數停止中。
   - 閾值觸發自動錄音：環境噪音 $\ge$ 設定門檻時自動開啟錄音。
   - 靜音延遲緩衝：噪音低於門檻時啟動倒數，持續達指定分鐘數才關閉錄音並存檔；倒數期間若噪音再次超標則重設倒數。
   - 前台常駐服務（Foreground Service with microphone type）：支援螢幕關閉與背景持續監測。

2. **錄音檔瀏覽畫面**
   - 錄音清單依時間倒序排列，顯示檔名、時間、檔案大小與錄音長度。
   - 內建播放控制器：支援播放、暫停、進度軸（Slider）即時跳轉。
   - 檔案管理操作：支援單檔播放、重新命名、複製副本與刪除。
   - 多重選取模式：支援全選、反選與批次刪除。

3. **設定畫面**
   - 噪音閥值調整（30 dB ~ 100 dB，預設 65 dB）。
   - 低於閥值持續停止時間微調（0.5 分鐘 ~ 30 分鐘，預設 2 分鐘）。
   - 聲音格式切換：
     - **M4A**：AAC 編碼，標準 MP4 容器封裝，體積小、通用性高、直接播放。
     - **WAV**：PCM 編碼，無壓縮、無失真原始音質。
   - 設定檔匯入 / 匯出：透過 Storage Access Framework（SAF）將設定輸出為 JSON 檔或從 JSON 匯入復原。

## 系統需求與技術架構

- **最低系統需求**：Android 13 (API 33)
- **目標系統版本**：Android 15 (API 35)
- **語言與架構**：Kotlin 2.0.21、Jetpack Compose、Material 3、Coroutines & StateFlow、DataStore Preferences
- **音訊架構**：AudioRecord（RMS 實時計算）+ MediaCodec / MediaMuxer（M4A）+ RIFF WAV Writer

## 建置方式

```bash
# 編譯與執行單元測試
./gradlew testDebugUnitTest

# 打包 Debug APK
./gradlew assembleDebug
```
產出的 APK 位於 `app/build/outputs/apk/debug/app-debug.apk`。
