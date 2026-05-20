package com.mistakenotes.ui.canvas

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter

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
    onStrokeCompleted: (VectorStroke) -> Unit = {}
) {
    var canvasState by remember { mutableStateOf(CanvasState()) }
    val strokes = remember { mutableStateListOf<VectorStroke>() }
    val layers = remember { listOf(VectorLayer(), VectorLayer()) }
    val undoStack = remember { mutableStateListOf<List<VectorStroke>>() }
    val redoStack = remember { mutableStateListOf<List<VectorStroke>>() }

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
                            undoStack.add(layers[0].strokes.toList())
                            redoStack.clear()
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