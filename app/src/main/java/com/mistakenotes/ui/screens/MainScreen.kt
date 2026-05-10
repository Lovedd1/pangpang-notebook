package com.mistakenotes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakenotes.ui.theme.*
import com.mistakenotes.ui.components.CanvasBackground
import com.mistakenotes.ui.components.PaperColor

@Composable
fun MainScreen() {
    var currentScreen by remember { mutableStateOf("home") }

    when (currentScreen) {
        "import" -> ImportScreen(
            onNavigateBack = { currentScreen = "home" },
            onSaveSuccess = { currentScreen = "home" }
        )
        "review" -> ReviewScreen(
            onNavigateBack = { currentScreen = "home" }
        )
        else -> HomeScreen(
            onNavigateToImport = { currentScreen = "import" },
            onNavigateToReview = { currentScreen = "review" }
        )
    }
}

@Composable
fun HomeScreen(
    onNavigateToImport: () -> Unit,
    onNavigateToReview: () -> Unit
) {
    var viewRef by remember { mutableStateOf<com.mistakenotes.ui.components.HandwritingView?>(null) }
    var isPenMode by remember { mutableStateOf(true) }
    var scaleText by remember { mutableStateOf("100%") }
    var canvasBg by remember { mutableStateOf(CanvasBackground.BLANK) }
    var paperColor by remember { mutableStateOf(PaperColor.BLACK) }

    // 当 viewRef 准备好时，同步初始状态
    LaunchedEffect(viewRef) {
        viewRef?.let {
            isPenMode = it.isPenMode
            scaleText = "${(it.getScale() * 100).toInt()}%"
            it.onScaleChangeListener = { scale ->
                scaleText = "${(scale * 100).toInt()}%"
            }
            it.canvasBackground = canvasBg
            it.paperColor = paperColor
        }
    }

    // 监听状态变化并同步到 view
    LaunchedEffect(canvasBg) {
        viewRef?.canvasBackground = canvasBg
    }
    LaunchedEffect(paperColor) {
        viewRef?.paperColor = paperColor
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkStoneBg)
            .padding(24.dp)
    ) {
        Text(
            text = "砚台 · 错题笔记",
            color = InkStoneAccent,
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "请在下方的画布上手写",
            color = InkStoneTextDim,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    color = InkStoneSurface,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
        ) {
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    com.mistakenotes.ui.components.HandwritingView(context).apply {
                        setBackgroundColor(android.graphics.Color.parseColor("#242424"))
                        fingerColor = android.graphics.Color.parseColor("#E8E4DC")
                        fingerStrokeWidth = 4f
                    }.also { view ->
                        viewRef = view
                    }
                },
                update = { view -> }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 模式切换按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    viewRef?.togglePenMode()
                    isPenMode = !isPenMode
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPenMode) InkStoneAccent else InkStoneSurface
                )
            ) {
                Text(
                    text = if (isPenMode) "笔写模式" else "手写模式",
                    color = if (isPenMode) InkStoneBg else InkStoneText
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            Text(
                text = scaleText,
                color = InkStoneTextDim,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "手写画布已就绪",
                color = InkStoneTextDim,
                fontSize = 12.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { viewRef?.undo() }) {
                    Text("撤销", color = InkStoneAccent)
                }
                TextButton(onClick = { viewRef?.clear() }) {
                    Text("清空", color = InkStoneError)
                }
                TextButton(onClick = {
                    viewRef?.resetTransform()
                    scaleText = "100%"
                }) {
                    Text("重置", color = InkStoneTextDim)
                }
                // 背景切换按钮
                Box {
                    var expanded by remember { mutableStateOf(false) }
                    TextButton(onClick = { expanded = true }) {
                        Text(
                            text = when (canvasBg) {
                                CanvasBackground.BLANK -> "空白"
                                CanvasBackground.GRID -> "网格"
                                CanvasBackground.LINES -> "横线"
                            },
                            color = InkStoneTextDim
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("空白") },
                            onClick = {
                                canvasBg = CanvasBackground.BLANK
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("网格") },
                            onClick = {
                                canvasBg = CanvasBackground.GRID
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("横线") },
                            onClick = {
                                canvasBg = CanvasBackground.LINES
                                expanded = false
                            }
                        )
                    }
                }
                // 纸张底色切换按钮
                Box {
                    var expandedColor by remember { mutableStateOf(false) }
                    TextButton(onClick = { expandedColor = true }) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    color = when (paperColor) {
                                        PaperColor.BLACK -> androidx.compose.ui.graphics.Color(0xFF242424)
                                        PaperColor.WHITE -> androidx.compose.ui.graphics.Color.White
                                        PaperColor.SKIN -> androidx.compose.ui.graphics.Color(0xFFF5E6D3)
                                    },
                                    shape = CircleShape
                                )
                        )
                    }
                    DropdownMenu(
                        expanded = expandedColor,
                        onDismissRequest = { expandedColor = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("黑色") },
                            onClick = {
                                paperColor = PaperColor.BLACK
                                expandedColor = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("白色") },
                            onClick = {
                                paperColor = PaperColor.WHITE
                                expandedColor = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("肉色") },
                            onClick = {
                                paperColor = PaperColor.SKIN
                                expandedColor = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 导航按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onNavigateToImport,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = InkStoneAccent
                )
            ) {
                Text("录入新题", color = InkStoneBg)
            }
            Button(
                onClick = onNavigateToReview,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = InkStoneSurface
                )
            ) {
                Text("进入复习", color = InkStoneText)
            }
        }
    }
}