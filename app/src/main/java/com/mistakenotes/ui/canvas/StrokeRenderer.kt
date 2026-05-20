package com.mistakenotes.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import kotlin.math.pow

object StrokeRenderer {

    fun renderStroke(canvas: Canvas, stroke: VectorStroke) {
        if (stroke.points.size < 2) return

        val path = Path()
        val smoothedPoints = catmullRomSmooth(stroke.points)

        if (smoothedPoints.size < 2) return

        // Build stroke polygon with pressure-based width
        val leftPoints = mutableListOf<Offset>()
        val rightPoints = mutableListOf<Offset>()

        for (i in smoothedPoints.indices) {
            val point = smoothedPoints[i]
            val pressure = point.pressure.coerceIn(0.1f, 1f)
            val halfWidth = stroke.baseThickness * pressure / 2

            // Calculate tangent direction
            val tangent = if (i == 0) {
                val next = smoothedPoints[i + 1]
                Offset(next.x - point.x, next.y - point.y)
            } else if (i == smoothedPoints.lastIndex) {
                val prev = smoothedPoints[i - 1]
                Offset(point.x - prev.x, point.y - prev.y)
            } else {
                val prev = smoothedPoints[i - 1]
                val next = smoothedPoints[i + 1]
                Offset((next.x - prev.x) / 2, (next.y - prev.y) / 2)
            }

            val normal = Offset(-tangent.y, tangent.x).normalized() * halfWidth

            leftPoints.add(Offset(point.x + normal.x, point.y + normal.y))
            rightPoints.add(Offset(point.x - normal.x, point.y - normal.y))
        }

        // Build path
        path.moveTo(leftPoints[0].x, leftPoints[0].y)
        for (i in 1 until leftPoints.size) {
            path.lineTo(leftPoints[i].x, leftPoints[i].y)
        }
        for (i in rightPoints.indices.reversed()) {
            path.lineTo(rightPoints[i].x, rightPoints[i].y)
        }
        path.close()

        canvas.drawPath(
            path = path,
            color = stroke.color,
            style = Fill
        )

        // Draw round caps at start and end
        val startPoint = smoothedPoints.first()
        val endPoint = smoothedPoints.last()
        val startPressure = startPoint.pressure.coerceIn(0.1f, 1f)
        val endPressure = endPoint.pressure.coerceIn(0.1f, 1f)

        canvas.drawOval(
            oval = androidx.compose.ui.geometry.Rect(
                center = Offset(startPoint.x, startPoint.y),
                halfWidth = stroke.baseThickness * startPressure / 2,
                halfHeight = stroke.baseThickness * startPressure / 2
            ),
            color = stroke.color,
            style = Fill
        )
        canvas.drawOval(
            oval = androidx.compose.ui.geometry.Rect(
                center = Offset(endPoint.x, endPoint.y),
                halfWidth = stroke.baseThickness * endPressure / 2,
                halfHeight = stroke.baseThickness * endPressure / 2
            ),
            color = stroke.color,
            style = Fill
        )
    }

    private fun catmullRomSmooth(points: List<StrokePoint>): List<StrokePoint> {
        if (points.size < 2) return points
        if (points.size == 2) return points

        val result = mutableListOf<StrokePoint>()
        val segments = 4

        for (i in 0 until points.size - 1) {
            val p0 = if (i > 0) points[i - 1] else points[i]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = if (i < points.size - 2) points[i + 2] else points[i + 1]

            for (j in 0 until segments) {
                val t = j.toFloat() / segments
                val x = catmullRomInterpolate(p0.x, p1.x, p2.x, p3.x, t)
                val y = catmullRomInterpolate(p0.y, p1.y, p2.y, p3.y, t)
                val pressure = lerp(p1.pressure, p2.pressure, t).coerceIn(0.1f, 1f)
                result.add(StrokePoint(x, y, pressure))
            }
        }
        result.add(points.last())
        return result
    }

    private fun catmullRomInterpolate(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        return 0.5f * (
            2 * p1 +
            (-p0 + p2) * t +
            (2 * p0 - 5 * p1 + 4 * p2 - p3) * t.pow(2) +
            (-p0 + 3 * p1 - 3 * p2 + p3) * t.pow(3)
        )
    }

    private fun Offset.normalized(): Offset {
        val length = kotlin.math.sqrt(x * x + y * y)
        return if (length > 0) Offset(x / length, y / length) else this
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}