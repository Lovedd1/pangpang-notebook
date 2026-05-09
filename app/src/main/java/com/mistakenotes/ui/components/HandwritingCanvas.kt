package com.mistakenotes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

data class DrawingPath(
    val path: Path,
    val color: Color,
    val strokeWidth: Float
)

data class DrawingState(
    val paths: List<DrawingPath> = emptyList(),
    val currentPath: Path? = null
)

@Composable
fun HandwritingCanvas(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF1A1A1A),
    strokeColor: Color = Color(0xFFE8E4DC),
    strokeWidth: Float = 4f,
    onDrawingChanged: ((List<DrawingPath>) -> Unit)? = null
) {
    var drawingState by remember { mutableStateOf(DrawingState()) }
    var currentPosition by remember { mutableStateOf<Offset?>(null) }

    val activeColor = strokeColor
    val activeStrokeWidth = strokeWidth

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .border(2.dp, Color(0xFF3A3A3A), RoundedCornerShape(12.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPosition = offset
                            drawingState = drawingState.copy(
                                currentPath = Path().apply { moveTo(offset.x, offset.y) }
                            )
                        },
                        onDrag = { change, _ ->
                            val newPosition = change.position
                            currentPosition = newPosition

                            drawingState.currentPath?.let { path ->
                                path.lineTo(newPosition.x, newPosition.y)
                                drawingState = drawingState.copy(
                                    currentPath = Path().apply { addPath(path) }
                                )
                            }
                        },
                        onDragEnd = {
                            drawingState.currentPath?.let { path ->
                                val newPath = DrawingPath(
                                    path = path,
                                    color = activeColor,
                                    strokeWidth = activeStrokeWidth
                                )
                                drawingState = drawingState.copy(
                                    paths = drawingState.paths + newPath,
                                    currentPath = null
                                )
                                onDrawingChanged?.invoke(drawingState.paths)
                            }
                            currentPosition = null
                        }
                    )
                }
        ) {
            // 绘制已完成的路径
            drawingState.paths.forEach { drawingPath ->
                drawPath(
                    path = drawingPath.path,
                    color = drawingPath.color,
                    style = Stroke(
                        width = drawingPath.strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 绘制当前正在绘制的路径
            drawingState.currentPath?.let { path ->
                drawPath(
                    path = path,
                    color = activeColor,
                    style = Stroke(
                        width = activeStrokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

@Composable
fun HandwritingCanvasWithToolbar(
    modifier: Modifier = Modifier,
    onClear: () -> Unit,
    onUndo: () -> Unit
) {
    // TODO: 实现工具栏版本（颜色选择、画笔粗细、撤销、清空）
    HandwritingCanvas(modifier = modifier)
}