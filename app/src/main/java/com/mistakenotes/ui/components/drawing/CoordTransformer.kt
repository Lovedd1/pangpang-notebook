package com.mistakenotes.ui.components.drawing

import android.graphics.PointF

/**
 * 统一坐标变换器
 * 负责屏幕坐标与画布坐标之间的转换
 */
class CoordTransformer {
    var translateX = 0f
    var translateY = 0f
    var scaleFactor = 1f
    var canvasOffsetX = 0f
    var canvasOffsetY = 0f

    private var canvasWidth = 0f
    private var canvasHeight = 0f

    fun setCanvasSize(width: Float, height: Float) {
        canvasWidth = width
        canvasHeight = height
    }

    /**
     * 将屏幕坐标转换为画布坐标
     */
    fun screenToCanvas(screenX: Float, screenY: Float): PointF {
        val canvasX = (screenX - translateX) / scaleFactor
        val canvasY = (screenY - translateY) / scaleFactor
        return PointF(canvasX, canvasY)
    }

    /**
     * 将屏幕坐标转换为画布坐标（考虑无限画布偏移）
     */
    fun screenToInfiniteCanvas(screenX: Float, screenY: Float): PointF {
        val canvasPoint = screenToCanvas(screenX, screenY)
        canvasPoint.x += canvasOffsetX
        canvasPoint.y += canvasOffsetY
        return canvasPoint
    }

    /**
     * 将画布坐标转换为屏幕坐标
     */
    fun canvasToScreen(canvasX: Float, canvasY: Float): PointF {
        val screenX = canvasX * scaleFactor + translateX
        val screenY = canvasY * scaleFactor + translateY
        return PointF(screenX, screenY)
    }

    /**
     * 将无限画布坐标转换为屏幕坐标
     */
    fun infiniteCanvasToScreen(canvasX: Float, canvasY: Float): PointF {
        val screenPoint = canvasToScreen(canvasX - canvasOffsetX, canvasY - canvasOffsetY)
        return screenPoint
    }

    /**
     * 限制拖动范围
     */
    fun constrainTranslate(maxTranslateX: Float, maxTranslateY: Float) {
        translateX = translateX.coerceIn(-maxTranslateX, maxTranslateX)
        translateY = translateY.coerceIn(-maxTranslateY, maxTranslateY)
    }

    /**
     * 重置为初始状态
     */
    fun reset() {
        translateX = 0f
        translateY = 0f
        scaleFactor = 1f
    }

    /**
     * 获取当前缩放
     */
    fun getScale(): Float = scaleFactor
}
