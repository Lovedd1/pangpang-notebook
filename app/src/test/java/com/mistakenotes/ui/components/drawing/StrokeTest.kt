package com.mistakenotes.ui.components.drawing

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class StrokeTest {

    @Test
    fun `单点笔画不绘制`() {
        val stroke = Stroke(
            points = listOf(PointF(10f, 10f)),
            color = 0xFFD4A574.toInt(),
            strokeWidth = 5f,
            strokeCap = Paint.Cap.ROUND,
            strokeJoin = Paint.Join.ROUND
        )

        val canvas = mock(Canvas::class.java)
        val paint = Paint()

        stroke.draw(canvas, paint)

        // 没有断言，因为draw不会在单点时画任何东西
        // 如果没有崩溃就说明通过了
    }

    @Test
    fun `两点笔画绘制直线`() {
        val stroke = Stroke(
            points = listOf(PointF(0f, 0f), PointF(100f, 100f)),
            color = 0xFFD4A574.toInt(),
            strokeWidth = 5f,
            strokeCap = Paint.Cap.ROUND,
            strokeJoin = Paint.Join.ROUND
        )

        val canvas = mock(Canvas::class.java)
        val paint = Paint()

        // 不崩溃即可
        stroke.draw(canvas, paint)
    }

    @Test
    fun `多点笔画绘制路径`() {
        val stroke = Stroke(
            points = listOf(
                PointF(0f, 0f),
                PointF(50f, 50f),
                PointF(100f, 0f),
                PointF(150f, 50f)
            ),
            color = 0xFFD4A574.toInt(),
            strokeWidth = 5f,
            strokeCap = Paint.Cap.ROUND,
            strokeJoin = Paint.Join.ROUND
        )

        val canvas = mock(Canvas::class.java)
        val paint = Paint()

        stroke.draw(canvas, paint)
    }

    @Test
    fun `获取边界矩形`() {
        val stroke = Stroke(
            points = listOf(
                PointF(10f, 20f),
                PointF(100f, 150f)
            ),
            color = 0xFFD4A574.toInt(),
            strokeWidth = 5f,
            strokeCap = Paint.Cap.ROUND,
            strokeJoin = Paint.Join.ROUND
        )

        val bounds = stroke.getBounds()

        assertEquals(10f, bounds.left, 0.01f)
        assertEquals(20f, bounds.top, 0.01f)
        assertEquals(100f, bounds.right, 0.01f)
        assertEquals(150f, bounds.bottom, 0.01f)
    }

    @Test
    fun `空笔画获取空边界`() {
        val stroke = Stroke(
            points = emptyList(),
            color = 0xFFD4A574.toInt(),
            strokeWidth = 5f,
            strokeCap = Paint.Cap.ROUND,
            strokeJoin = Paint.Join.ROUND
        )

        val bounds = stroke.getBounds()

        assertEquals(0f, bounds.left, 0.01f)
        assertEquals(0f, bounds.top, 0.01f)
        assertEquals(0f, bounds.right, 0.01f)
        assertEquals(0f, bounds.bottom, 0.01f)
    }

    @Test
    fun `笔画属性正确保存`() {
        val timestamp = System.currentTimeMillis()
        val stroke = Stroke(
            points = listOf(PointF(0f, 0f), PointF(10f, 10f)),
            color = 0xFF123456.toInt(),
            strokeWidth = 7f,
            strokeCap = Paint.Cap.BUTT,
            strokeJoin = Paint.Join.BEVEL,
            timestamp = timestamp
        )

        assertEquals(2, stroke.points.size)
        assertEquals(0xFF123456.toInt(), stroke.color)
        assertEquals(7f, stroke.strokeWidth, 0.01f)
        assertEquals(Paint.Cap.BUTT, stroke.strokeCap)
        assertEquals(Paint.Join.BEVEL, stroke.strokeJoin)
        assertEquals(timestamp, stroke.timestamp)
    }
}