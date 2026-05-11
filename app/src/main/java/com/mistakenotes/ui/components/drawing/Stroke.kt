package com.mistakenotes.ui.components.drawing

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF

/**
 * 笔画数据模型
 * 用于笔画级撤销/重做
 */
data class Stroke(
    val points: List<PointF>,
    val color: Int,
    val strokeWidth: Float,
    val strokeCap: Paint.Cap,
    val strokeJoin: Paint.Join,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 将笔画绘制到画布
     */
    fun draw(canvas: android.graphics.Canvas, paint: Paint) {
        if (points.size < 2) return

        paint.color = color
        paint.strokeWidth = strokeWidth
        paint.strokeCap = strokeCap
        paint.strokeJoin = strokeJoin
        paint.style = Paint.Style.STROKE
        paint.isAntiAlias = true
        paint.isFilterBitmap = true

        val path = Path()
        path.moveTo(points[0].x, points[0].y)

        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }

        canvas.drawPath(path, paint)
    }

    /**
     * 获取边界矩形
     */
    fun getBounds(): android.graphics.RectF {
        if (points.isEmpty()) return android.graphics.RectF()

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (point in points) {
            minX = minOf(minX, point.x)
            minY = minOf(minY, point.y)
            maxX = maxOf(maxX, point.x)
            maxY = maxOf(maxY, point.y)
        }

        return android.graphics.RectF(minX, minY, maxX, maxY)
    }
}
