package com.mistakenotes.ui.canvas

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
    val timestamp: Long = System.currentTimeMillis()
)