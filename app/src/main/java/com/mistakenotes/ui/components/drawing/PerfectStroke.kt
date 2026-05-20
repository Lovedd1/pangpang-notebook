package com.mistakenotes.ui.components.drawing

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * perfect-freehand 算法 Kotlin 移植版
 * 将输入点转换为平滑的压力感应笔画多边形
 */
object PerfectStroke {

    data class Options(
        val size: Float = 8f,
        val thinning: Float = 0.5f,
        val smoothing: Float = 0.5f,
        val streamline: Float = 0.5f,
        val simulatePressure: Boolean = false,
        val cap: Boolean = true,
        val start: StartEnd = StartEnd(),
        val end: StartEnd = StartEnd()
    )

    data class StartEnd(
        val cap: Boolean = true,
        val taper: Float = 0f
    )

    /**
     * 主入口：将输入点转换为笔画多边形点
     */
    fun getStroke(points: List<StrokePoint>, options: Options = Options()): List<PointF> {
        if (points.size < 2) return emptyList()

        // 1. 获取插值后的点
        val strokePoints = getStrokePoints(points, options)

        // 2. 生成多边形边缘
        return getStrokeOutlinePoints(strokePoints, options)
    }

    /**
     * 获取插值平滑后的点
     */
    internal fun getStrokePoints(points: List<StrokePoint>, options: Options): List<StrokeStrokePoint> {
        if (points.isEmpty()) return emptyList()

        val result = mutableListOf<StrokeStrokePoint>()
        var runningLength = 0f
        var prevPoint: StrokeStrokePoint? = null

        for (i in points.indices) {
            val point = points[i]
            val pressure = if (options.simulatePressure) {
                // 根据速度模拟压力
                val prev = prevPoint
                if (prev != null) {
                    val dx = point.x - prev.point.x
                    val dy = point.y - prev.point.y
                    val dist = sqrt(dx * dx + dy * dy)
                    val time = maxOf(1L, point.timestamp - prev.timestamp)
                    val speed = dist / time
                    (1f - minOf(speed / 0.5f, 1f) * (1f - options.thinning)).coerceIn(0.1f, 1f)
                } else 0.5f
            } else {
                point.pressure
            }

            // 计算方向向量
            val vector = if (prevPoint != null) {
                val dx = point.x - prevPoint.point.x
                val dy = point.y - prevPoint.point.y
                val len = sqrt(dx * dx + dy * dy)
                if (len > 0) PointF(dx / len, dy / len) else PointF(1f, 0f)
            } else {
                PointF(1f, 0f)
            }

            if (prevPoint != null) {
                val dx = point.x - prevPoint.point.x
                val dy = point.y - prevPoint.point.y
                runningLength += sqrt(dx * dx + dy * dy)
            }

            val strokePoint = StrokeStrokePoint(
                point = PointF(point.x, point.y),
                pressure = pressure,
                vector = vector,
                distance = runningLength,
                runningLength = runningLength,
                timestamp = point.timestamp
            )

            result.add(strokePoint)
            prevPoint = strokePoint
        }

        // 应用 streamline（流畅度）
        return applyStreamline(result, options.streamline)
    }

    private data class StrokeStrokePoint(
        val point: PointF,
        val pressure: Float,
        val vector: PointF,
        val distance: Float,
        val runningLength: Float,
        val timestamp: Long = 0L
    )

    /**
     * 生成笔画多边形边缘点
     */
    internal fun getStrokeOutlinePoints(strokePoints: List<StrokeStrokePoint>, options: Options): List<PointF> {
        if (strokePoints.size < 2) return emptyList()

        val outlinePoints = mutableListOf<PointF>()
        val size = options.size
        val thinning = options.thinning

        // 遍历每个点，生成左右边缘
        for (i in strokePoints.indices) {
            val sp = strokePoints[i]

            // 计算当前点的笔画宽度
            val pressureSize = size * (0.5f + sp.pressure * thinning * 0.5f)
            val halfWidth = pressureSize / 2

            // 垂直于方向向量的边缘
            val perpX = -sp.vector.y
            val perpY = sp.vector.x

            // 左侧点
            outlinePoints.add(PointF(
                sp.point.x + perpX * halfWidth,
                sp.point.y + perpY * halfWidth
            ))

            // 右侧点（从后向前添加，形成多边形）
            outlinePoints.add(0, PointF(
                sp.point.x - perpX * halfWidth,
                sp.point.y - perpY * halfWidth
            ))
        }

        // 简化和平滑边缘
        return simplifyOutline(outlinePoints, options.smoothing)
    }

    private fun applyStreamline(points: List<StrokeStrokePoint>, streamline: Float): List<StrokeStrokePoint> {
        if (points.size < 3 || streamline <= 0) return points

        val result = mutableListOf<StrokeStrokePoint>()
        result.add(points.first())

        for (i in 1 until points.size - 1) {
            val prev = points[i - 1]
            val curr = points[i]
            val next = points[i + 1]

            val factor = 1f - streamline

            val smoothedX = curr.point.x * factor + (prev.point.x + next.point.x) / 2 * streamline
            val smoothedY = curr.point.y * factor + (prev.point.y + next.point.y) / 2 * streamline

            result.add(StrokeStrokePoint(
                point = PointF(smoothedX, smoothedY),
                pressure = curr.pressure,
                vector = curr.vector,
                distance = curr.distance,
                runningLength = curr.runningLength,
                timestamp = curr.timestamp
            ))
        }

        result.add(points.last())
        return result
    }

    private fun simplifyOutline(points: List<PointF>, smoothing: Float): List<PointF> {
        if (points.size < 3 || smoothing <= 0) return points

        // 简化算法：距离小于阈值的点合并
        val threshold = smoothing * 2f
        val result = mutableListOf<PointF>()

        for (point in points) {
            if (result.isEmpty() || result.size < 2) {
                result.add(point)
            } else {
                val last = result.last()
                val dist = sqrt(
                    (point.x - last.x) * (point.x - last.x) +
                    (point.y - last.y) * (point.y - last.y)
                )
                if (dist >= threshold) {
                    result.add(point)
                }
            }
        }

        return result
    }

    /**
     * 将 PointF 列表转换为 Android Path
     */
    fun pointsToPath(points: List<PointF>): android.graphics.Path {
        val path = android.graphics.Path()
        if (points.isEmpty()) return path

        // 找到最左边的点作为起点
        val startIndex = points.indices.minByOrNull { points[it].x } ?: 0
        val orderedPoints = if (startIndex == 0) points else {
            points.slice(startIndex until points.size) + points.slice(0 until startIndex)
        }

        path.moveTo(orderedPoints[0].x, orderedPoints[0].y)

        if (orderedPoints.size == 1) {
            return path
        }

        if (orderedPoints.size == 2) {
            path.lineTo(orderedPoints[1].x, orderedPoints[1].y)
            return path
        }

        // 使用二次贝塞尔曲线连接点，形成平滑多边形
        for (i in 0 until orderedPoints.size - 1) {
            val curr = orderedPoints[i]
            val next = orderedPoints[i + 1]
            val midX = (curr.x + next.x) / 2
            val midY = (curr.y + next.y) / 2
            path.quadTo(curr.x, curr.y, midX, midY)
        }

        // 闭合路径
        path.close()
        return path
    }
}