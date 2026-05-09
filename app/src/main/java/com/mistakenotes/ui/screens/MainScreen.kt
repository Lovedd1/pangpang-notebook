package com.mistakenotes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakenotes.ui.theme.*

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