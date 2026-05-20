package com.mistakenotes.ui.components.drawing

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF

/**
 * 笔画渲染器
 * 使用 PerfectStroke 算法将采样点渲染为平滑的笔画
 */
class StrokeRenderer {

    data class RenderOptions(
        val color: Int = 0xFF000000.toInt(),
        val size: Float = 8f,
        val thinning: Float = 0.5f,
        val smoothing: Float = 0.5f,
        val streamline: Float = 0.5f,
        val alpha: Int = 255
    )

    private val paint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    /**
     * 渲染笔画到 Canvas
     */
    fun renderStroke(canvas: Canvas, points: List<StrokePoint>, options: RenderOptions) {
        if (points.size < 2) return

        val outlinePoints = PerfectStroke.getStroke(
            points,
            PerfectStroke.Options(
                size = options.size,
                thinning = options.thinning,
                smoothing = options.smoothing,
                streamline = options.streamline,
                simulatePressure = false
            )
        )

        if (outlinePoints.isEmpty()) return

        val path = PerfectStroke.pointsToPath(outlinePoints)

        paint.color = options.color
        paint.alpha = options.alpha

        canvas.drawPath(path, paint)
    }

    /**
     * 创建用于存储的 PathData
     */
    fun createPathData(points: List<StrokePoint>, options: RenderOptions): PathData? {
        if (points.size < 2) return null

        val outlinePoints = PerfectStroke.getStroke(
            points,
            PerfectStroke.Options(
                size = options.size,
                thinning = options.thinning,
                smoothing = options.smoothing,
                streamline = options.streamline,
                simulatePressure = false
            )
        )

        if (outlinePoints.isEmpty()) return null

        val path = PerfectStroke.pointsToPath(outlinePoints)

        val paint = Paint().apply {
            style = Paint.Style.FILL
            color = options.color
            alpha = options.alpha
            isAntiAlias = true
        }

        return PathData(path, paint, DrawingTool.PEN)
    }

    /**
     * 渲染高亮笔（半透明）
     */
    fun renderHighlighterStroke(canvas: Canvas, points: List<StrokePoint>, color: Int, size: Float) {
        val alpha = 100 // 高亮笔半透明
        renderStroke(
            canvas,
            points,
            RenderOptions(
                color = color,
                size = size * 2, // 高亮笔更粗
                thinning = 0.3f,
                smoothing = 0.8f,
                streamline = 0.7f,
                alpha = alpha
            )
        )
    }
}