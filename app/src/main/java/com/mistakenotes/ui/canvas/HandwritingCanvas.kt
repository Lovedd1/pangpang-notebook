package com.mistakenotes.ui.canvas

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.default.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class DrawingTool { PEN, HIGHLIGHTER, ERASER }

data class CanvasState(
    val currentTool: DrawingTool = DrawingTool.PEN,
    val penColor: Color = Color.Blue,
    val penThickness: Float = 3f,
    val isPenDown: Boolean = false,
    val currentStroke: VectorStroke? = null
)

@Composable
fun HandwritingCanvas(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    onStrokeCompleted: (VectorStroke) -> Unit = {},
    onUndoStateChange: (canUndo: Boolean, canRedo: Boolean) -> Unit = { _, _ -> }
) {
    var canvasState by remember { mutableStateOf(CanvasState()) }
    val strokes = remember { mutableStateListOf<VectorStroke>() }
    val layers = remember { listOf(VectorLayer(), VectorLayer()) }
    val undoManager = remember { UndoRedoManager<VectorStroke>(50) }

    fun updateUndoState() {
        onUndoStateChange(undoManager.canUndo(), undoManager.canRedo())
    }

    Canvas(
        modifier = modifier
            .background(backgroundColor)
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        canvasState = canvasState.copy(isPenDown = true)
                        val stroke = VectorStroke(
                            points = listOf(
                                StrokePoint(
                                    event.x,
                                    event.y,
                                    event.pressure.coerceIn(0.1f, 1f)
                                )
                            ),
                            color = canvasState.currentTool.let {
                                if (it == DrawingTool.HIGHLIGHTER) canvasState.penColor.copy(alpha = 0.3f)
                                else canvasState.penColor
                            },
                            baseThickness = canvasState.penThickness
                        )
                        canvasState = canvasState.copy(currentStroke = stroke)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val currentStroke = canvasState.currentStroke ?: return@pointerInteropFilter true
                        val newPoints = currentStroke.points + StrokePoint(
                            event.x,
                            event.y,
                            event.pressure.coerceIn(0.1f, 1f)
                        )
                        canvasState = canvasState.copy(
                            currentStroke = currentStroke.copy(points = newPoints)
                        )
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val stroke = canvasState.currentStroke
                        if (stroke != null && stroke.points.size >= 2) {
                            layers[0].addStroke(stroke)
                            undoManager.saveState(layers[0].strokes.toList())
                            updateUndoState()
                            onStrokeCompleted(stroke)
                        }
                        canvasState = canvasState.copy(isPenDown = false, currentStroke = null)
                        true
                    }
                    else -> false
                }
            }
    ) {
        // Draw all completed strokes
        layers.forEach { layer ->
            if (layer.isVisible) {
                layer.strokes.forEach { stroke ->
                    StrokeRenderer.renderStroke(this, stroke)
                }
            }
        }
        // Draw current stroke being drawn
        canvasState.currentStroke?.let { stroke ->
            StrokeRenderer.renderStroke(this, stroke)
        }
    }
}

@Composable
fun HandwritingToolbar(
    currentTool: DrawingTool,
    penColor: Color,
    penThickness: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    onToolChange: (DrawingTool) -> Unit,
    onColorChange: (Color) -> Unit,
    onThicknessChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color(0xFF2A2A2A))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Undo button
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(
                Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                tint = if (canUndo) Color.White else Color.Gray
            )
        }

        // Redo button
        IconButton(onClick = onRedo, enabled = canRedo) {
            Icon(
                Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                tint = if (canRedo) Color.White else Color.Gray
            )
        }

        VerticalDivider(
            modifier = Modifier
                .height(24.dp)
                .padding(horizontal = 4.dp),
            color = Color.Gray
        )

        // Tool selection
        ToolButton(
            emoji = "✏️",
            isSelected = currentTool == DrawingTool.PEN,
            onClick = { onToolChange(DrawingTool.PEN) }
        )
        ToolButton(
            emoji = "🖍️",
            isSelected = currentTool == DrawingTool.HIGHLIGHTER,
            onClick = { onToolChange(DrawingTool.HIGHLIGHTER) }
        )
        ToolButton(
            emoji = "🧽",
            isSelected = currentTool == DrawingTool.ERASER,
            onClick = { onToolChange(DrawingTool.ERASER) }
        )

        VerticalDivider(
            modifier = Modifier
                .height(24.dp)
                .padding(horizontal = 4.dp),
            color = Color.Gray
        )

        // Color selection (only for PEN and HIGHLIGHTER)
        if (currentTool != DrawingTool.ERASER) {
            ColorButton(color = Color.Blue, isSelected = penColor == Color.Blue, onClick = { onColorChange(Color.Blue) })
            ColorButton(color = Color.Black, isSelected = penColor == Color.Black, onClick = { onColorChange(Color.Black) })
            ColorButton(color = Color.Red, isSelected = penColor == Color.Red, onClick = { onColorChange(Color.Red) })

            VerticalDivider(
                modifier = Modifier
                    .height(24.dp)
                    .padding(horizontal = 4.dp),
                color = Color.Gray
            )

            // Thickness selection
            ThicknessButton(text = "0.1mm", isSelected = penThickness == 0.1f, onClick = { onThicknessChange(0.1f) })
            ThicknessButton(text = "0.3mm", isSelected = penThickness == 0.3f, onClick = { onThicknessChange(0.3f) })
            ThicknessButton(text = "0.5mm", isSelected = penThickness == 0.5f, onClick = { onThicknessChange(0.5f) })
        }

        Spacer(modifier = Modifier.weight(1f))

        // Clear button
        IconButton(onClick = onClear) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Clear",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ToolButton(
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (isSelected) Color.DarkGray else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 18.sp)
    }
}

@Composable
private fun ColorButton(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Color.White else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun ThicknessButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(28.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = if (isSelected) Color.White else Color.Gray
        )
    }
}