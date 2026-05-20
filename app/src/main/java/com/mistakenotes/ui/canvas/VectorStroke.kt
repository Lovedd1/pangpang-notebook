package com.mistakenotes.ui.canvas

import androidx.compose.ui.graphics.Color
import java.util.UUID

data class VectorStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<StrokePoint> = emptyList(),
    val color: Color = Color.Black,
    val baseThickness: Float = 3f
)