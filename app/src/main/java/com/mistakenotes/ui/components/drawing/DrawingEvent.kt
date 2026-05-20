package com.mistakenotes.ui.components.drawing

/**
 * 绘制工具类型
 */
enum class DrawingTool {
    PEN,
    HIGHLIGHTER,
    ERASER
}

/**
 * 绘制事件类型
 */
sealed class DrawingEvent {
    data class StrokeStarted(val point: StrokePoint) : DrawingEvent()
    data class StrokeMoved(val point: StrokePoint) : DrawingEvent()
    data class StrokeEnded(val points: List<StrokePoint>) : DrawingEvent()
    data class EraseMoved(val x: Float, val y: Float, val pressure: Float) : DrawingEvent()
    object EraserModeEntered : DrawingEvent()
    object EraserModeExited : DrawingEvent()
}
