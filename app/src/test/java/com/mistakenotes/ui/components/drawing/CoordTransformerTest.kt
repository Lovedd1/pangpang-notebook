package com.mistakenotes.ui.components.drawing

import org.junit.Assert.*
import org.junit.Test

class CoordTransformerTest {

    @Test
    fun `初始状态屏幕坐标等于画布坐标`() {
        val transformer = CoordTransformer()
        transformer.setCanvasSize(1000f, 1400f)

        val canvasPoint = transformer.screenToCanvas(100f, 200f)

        assertEquals(100f, canvasPoint.x, 0.01f)
        assertEquals(200f, canvasPoint.y, 0.01f)
    }

    @Test
    fun `平移变换`() {
        val transformer = CoordTransformer()
        transformer.setCanvasSize(1000f, 1400f)
        transformer.translateX = 50f
        transformer.translateY = -30f

        val canvasPoint = transformer.screenToCanvas(100f, 200f)

        assertEquals(50f, canvasPoint.x, 0.01f)  // (100 - 50) / 1
        assertEquals(230f, canvasPoint.y, 0.01f) // (200 - (-30)) / 1 = 230
    }

    @Test
    fun `缩放变换`() {
        val transformer = CoordTransformer()
        transformer.setCanvasSize(1000f, 1400f)
        transformer.scaleFactor = 2f

        val canvasPoint = transformer.screenToCanvas(200f, 400f)

        assertEquals(100f, canvasPoint.x, 0.01f)  // (200 - 0) / 2 = 100
        assertEquals(200f, canvasPoint.y, 0.01f) // (400 - 0) / 2 = 200
    }

    @Test
    fun `缩放加平移`() {
        val transformer = CoordTransformer()
        transformer.setCanvasSize(1000f, 1400f)
        transformer.translateX = 100f
        transformer.translateY = 50f
        transformer.scaleFactor = 2f

        val canvasPoint = transformer.screenToCanvas(300f, 250f)

        // (300 - 100) / 2 = 100
        assertEquals(100f, canvasPoint.x, 0.01f)
        // (250 - 50) / 2 = 100
        assertEquals(100f, canvasPoint.y, 0.01f)
    }

    @Test
    fun `无限画布坐标转换`() {
        val transformer = CoordTransformer()
        transformer.setCanvasSize(3000f, 4200f)
        transformer.canvasOffsetX = 1000f
        transformer.canvasOffsetY = 1400f

        val infinitePoint = transformer.screenToInfiniteCanvas(100f, 200f)

        assertEquals(1100f, infinitePoint.x, 0.01f) // 100 + 1000
        assertEquals(1600f, infinitePoint.y, 0.01f) // 200 + 1400
    }

    @Test
    fun `画布坐标转屏幕坐标`() {
        val transformer = CoordTransformer()
        transformer.setCanvasSize(1000f, 1400f)
        transformer.translateX = 50f
        transformer.translateY = 30f
        transformer.scaleFactor = 2f

        val screenPoint = transformer.canvasToScreen(100f, 200f)

        // 100 * 2 + 50 = 250
        assertEquals(250f, screenPoint.x, 0.01f)
        // 200 * 2 + 30 = 430
        assertEquals(430f, screenPoint.y, 0.01f)
    }

    @Test
    fun `无限画布坐标转屏幕坐标`() {
        val transformer = CoordTransformer()
        transformer.setCanvasSize(3000f, 4200f)
        transformer.canvasOffsetX = 1000f
        transformer.canvasOffsetY = 1400f
        transformer.translateX = 50f
        transformer.translateY = 30f
        transformer.scaleFactor = 2f

        val screenPoint = transformer.infiniteCanvasToScreen(1100f, 1600f)

        // (1100 - 1000) * 2 + 50 = 250
        assertEquals(250f, screenPoint.x, 0.01f)
        // (1600 - 1400) * 2 + 30 = 430
        assertEquals(430f, screenPoint.y, 0.01f)
    }

    @Test
    fun `限制平移范围`() {
        val transformer = CoordTransformer()
        transformer.setCanvasSize(1000f, 1400f)

        // 尝试设置超出范围的值
        transformer.translateX = 500f
        transformer.translateY = 800f

        transformer.constrainTranslate(100f, 200f)

        assertEquals(100f, transformer.translateX, 0.01f)  // 被限制到 100
        assertEquals(200f, transformer.translateY, 0.01f)  // 被限制到 200
    }

    @Test
    fun `负数平移限制`() {
        val transformer = CoordTransformer()
        transformer.setCanvasSize(1000f, 1400f)

        transformer.translateX = -500f
        transformer.translateY = -800f

        transformer.constrainTranslate(100f, 200f)

        assertEquals(-100f, transformer.translateX, 0.01f) // 被限制到 -100
        assertEquals(-200f, transformer.translateY, 0.01f) // 被限制到 -200
    }

    @Test
    fun `重置变换`() {
        val transformer = CoordTransformer()
        transformer.setCanvasSize(1000f, 1400f)
        transformer.translateX = 100f
        transformer.translateY = 50f
        transformer.scaleFactor = 3f

        transformer.reset()

        assertEquals(0f, transformer.translateX, 0.01f)
        assertEquals(0f, transformer.translateY, 0.01f)
        assertEquals(1f, transformer.scaleFactor, 0.01f)
    }

    @Test
    fun `获取当前缩放`() {
        val transformer = CoordTransformer()
        transformer.setCanvasSize(1000f, 1400f)
        transformer.scaleFactor = 2.5f

        assertEquals(2.5f, transformer.getScale(), 0.01f)
    }
}