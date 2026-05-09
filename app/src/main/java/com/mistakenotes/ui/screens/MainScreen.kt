package com.mistakenotes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mistakenotes.ui.components.HandwritingView
import com.mistakenotes.ui.theme.*

@Composable
fun MainScreen() {
    var viewRef by remember { mutableStateOf<HandwritingView?>(null) }
    var pathCount by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkStoneBg)
            .padding(24.dp)
    ) {
        Text(
            text = "砚台 · 手写测试",
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
                .background(InkStoneSurface, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    HandwritingView(context).apply {
                        setBackgroundColor(android.graphics.Color.parseColor("#242424"))
                        strokeColor = android.graphics.Color.parseColor("#E8E4DC")
                        strokeWidth = 4f
                    }.also { view ->
                        viewRef = view
                    }
                },
                update = { view ->
                    // 更新逻辑
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已绘制 $pathCount 条路径",
                color = InkStoneTextDim,
                fontSize = 12.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.TextButton(
                    onClick = { viewRef?.undo() }
                ) {
                    Text("撤销", color = InkStoneAccent)
                }
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewRef?.clear()
                        pathCount = 0
                    }
                ) {
                    Text("清空", color = InkStoneError)
                }
            }
        }
    }
}