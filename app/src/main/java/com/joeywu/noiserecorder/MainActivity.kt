package com.joeywu.noiserecorder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.joeywu.noiserecorder.ui.navigation.Screen
import com.joeywu.noiserecorder.ui.screens.MonitoringScreen
import com.joeywu.noiserecorder.ui.screens.RecordingsScreen
import com.joeywu.noiserecorder.ui.screens.SettingsScreen
import com.joeywu.noiserecorder.ui.theme.NoiseRecorderTheme
import com.joeywu.noiserecorder.ui.viewmodels.MonitoringViewModel
import com.joeywu.noiserecorder.ui.viewmodels.RecordingsViewModel
import com.joeywu.noiserecorder.ui.viewmodels.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val monitoringViewModel: MonitoringViewModel by viewModels()
    private val recordingsViewModel: RecordingsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (!recordAudioGranted) {
            Toast.makeText(this, "需要麥克風權限以進行噪音監測與錄音", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        setContent {
            NoiseRecorderTheme {
                MainAppScaffold(
                    monitoringViewModel = monitoringViewModel,
                    recordingsViewModel = recordingsViewModel,
                    settingsViewModel = settingsViewModel,
                    onRequestPermissions = { checkAndRequestPermissions() }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            requestPermissionLauncher.launch(needed.toTypedArray())
        }
    }
}

@Composable
fun MainAppScaffold(
    monitoringViewModel: MonitoringViewModel,
    recordingsViewModel: RecordingsViewModel,
    settingsViewModel: SettingsViewModel,
    onRequestPermissions: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                Screen.items.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = isSelected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Monitoring.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Monitoring.route) {
                MonitoringScreen(
                    viewModel = monitoringViewModel,
                    onRequestPermissions = onRequestPermissions
                )
            }
            composable(Screen.Recordings.route) {
                RecordingsScreen(viewModel = recordingsViewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}
