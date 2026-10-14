package com.joeywu.noiserecorder.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joeywu.noiserecorder.data.RecordingItem
import com.joeywu.noiserecorder.ui.theme.DangerRed
import com.joeywu.noiserecorder.ui.viewmodels.RecordingsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingsScreen(viewModel: RecordingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val recordings by viewModel.recordings.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isMultiSelect by viewModel.isMultiSelectMode.collectAsState()

    val currentPlayingItem by viewModel.currentPlayingItem.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()

    var showRenameDialogFor by remember { mutableStateOf<RecordingItem?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingSingleDelete by remember { mutableStateOf<RecordingItem?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 多選控制列或頂部工具列
                if (isMultiSelect) {
                    MultiSelectActionBar(
                        selectedCount = selectedIds.size,
                        totalCount = recordings.size,
                        onSelectAll = { viewModel.selectAll() },
                        onClearSelection = { viewModel.clearSelection() },
                        onDeleteClick = { showDeleteConfirmDialog = true }
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "錄音檔案列表 (${recordings.size})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (recordings.isNotEmpty()) {
                            TextButton(onClick = { viewModel.toggleSelectionMode(true) }) {
                                Text("多重選取")
                            }
                        }
                    }
                }

                // 檔案列表
                if (recordings.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "尚無錄音檔案\n當噪音超過設定門檻時將自動錄音",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 15.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        items(recordings, key = { it.id }) { item ->
                            val isSelected = selectedIds.contains(item.id)
                            RecordingItemRow(
                                item = item,
                                isSelected = isSelected,
                                isMultiSelectMode = isMultiSelect,
                                isPlayingThis = currentPlayingItem?.id == item.id,
                                onClick = {
                                    if (isMultiSelect) {
                                        viewModel.toggleItemSelection(item.id)
                                    } else {
                                        viewModel.play(item)
                                    }
                                },
                                onLongClick = {
                                    if (!isMultiSelect) {
                                        viewModel.toggleSelectionMode(true)
                                        viewModel.toggleItemSelection(item.id)
                                    }
                                },
                                onPlayClick = { viewModel.play(item) },
                                onRenameClick = {
                                    showRenameDialogFor = item
                                    renameInput = item.file.nameWithoutExtension
                                },
                                onDuplicateClick = {
                                    scope.launch {
                                        val res = viewModel.duplicate(item)
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "已複製副本", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "複製失敗", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onDeleteClick = {
                                    pendingSingleDelete = item
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // 播放器預留底部高度
                if (currentPlayingItem != null) {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            // 底部迷你播放器面板
            AnimatedVisibility(
                visible = currentPlayingItem != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                currentPlayingItem?.let { item ->
                    PlayerBottomBar(
                        item = item,
                        isPlaying = isPlaying,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        onPlayPauseClick = {
                            if (isPlaying) viewModel.pause() else viewModel.resume()
                        },
                        onSeek = { viewModel.seekTo(it) },
                        onClose = { viewModel.playerManager.stop() }
                    )
                }
            }
        }
    }

    // 改名對話框
    if (showRenameDialogFor != null) {
        val targetItem = showRenameDialogFor!!
        AlertDialog(
            onDismissRequest = { showRenameDialogFor = null },
            title = { Text("變更檔案名稱") },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        label = { Text("檔案名稱") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "副檔名將維持 .${targetItem.file.extension}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val res = viewModel.rename(targetItem, renameInput)
                            if (res.isSuccess) {
                                Toast.makeText(context, "改名成功", Toast.LENGTH_SHORT).show()
                                showRenameDialogFor = null
                            } else {
                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "改名失敗", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("確定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialogFor = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 刪除確認對話框（多選批次或單檔）
    if (showDeleteConfirmDialog || pendingSingleDelete != null) {
        val isBatch = showDeleteConfirmDialog
        val count = if (isBatch) selectedIds.size else 1
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                pendingSingleDelete = null
            },
            title = { Text("刪除確認") },
            text = { Text("確定要刪除這 $count 個錄音檔案嗎？此動作無法復原。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isBatch) {
                            viewModel.deleteSelected { deleted ->
                                Toast.makeText(context, "已刪除 $deleted 個檔案", Toast.LENGTH_SHORT).show()
                                showDeleteConfirmDialog = false
                            }
                        } else {
                            pendingSingleDelete?.let { item ->
                                viewModel.deleteSingle(item) {
                                    Toast.makeText(context, "已刪除檔案", Toast.LENGTH_SHORT).show()
                                    pendingSingleDelete = null
                                }
                            }
                        }
                    }
                ) {
                    Text("刪除", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    pendingSingleDelete = null
                }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun MultiSelectActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "取消選取")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "已選取 $selectedCount 項",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row {
                IconButton(onClick = onSelectAll) {
                    Icon(Icons.Default.SelectAll, contentDescription = "全選")
                }
                IconButton(onClick = onDeleteClick, enabled = selectedCount > 0) {
                    Icon(Icons.Default.Delete, contentDescription = "批次刪除", tint = if (selectedCount > 0) DangerRed else Color.Gray)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingItemRow(
    item: RecordingItem,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    isPlayingThis: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDuplicateClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlayingThis) {
                MaterialTheme.colorScheme.primaryContainer
            } else if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                IconButton(onClick = onPlayClick) {
                    Icon(
                        imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        text = dateFormat.format(Date(item.lastModified)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.formattedSize,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (item.durationMs > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.formattedDuration,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            if (!isMultiSelectMode) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "選單")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("播放") },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onPlayClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("重新命名") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRenameClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("複製副本") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDuplicateClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("刪除", color = DangerRed) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed) },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerBottomBar(
    item: RecordingItem,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatMs(currentPositionMs)} / ${formatMs(durationMs)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(onClick = onPlayPauseClick) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "播放/暫停",
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "關閉播放列")
                }
            }

            Slider(
                value = currentPositionMs.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
