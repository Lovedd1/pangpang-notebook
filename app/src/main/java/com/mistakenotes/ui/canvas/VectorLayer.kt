package com.mistakenotes.ui.canvas

import androidx.compose.runtime.mutableStateListOf

class VectorLayer {
    val strokes = mutableStateListOf<VectorStroke>()
    var opacity: Float = 1f
    var isVisible: Boolean = true
    var isLocked: Boolean = false

    fun addStroke(stroke: VectorStroke) {
        strokes.add(stroke)
    }

    fun removeStroke(strokeId: String) {
        strokes.removeIf { it.id == strokeId }
    }

    fun clear() {
        strokes.clear()
    }
}