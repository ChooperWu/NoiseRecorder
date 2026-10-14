package com.joeywu.noiserecorder.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Monitoring : Screen("monitoring", "起始/偵測", Icons.Default.GraphicEq)
    object Recordings : Screen("recordings", "錄音檔", Icons.Default.LibraryMusic)
    object Settings : Screen("settings", "設定", Icons.Default.Settings)

    companion object {
        val items = listOf(Monitoring, Recordings, Settings)
    }
}
