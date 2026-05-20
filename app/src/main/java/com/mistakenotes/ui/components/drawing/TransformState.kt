package com.mistakenotes.ui.components.drawing

import android.graphics.PointF

/**
 * 无限画布变换状态
 * 支持无限制缩放和平移
 */
data class TransformState(
    var scale: Float = 1f,
    var translateX: Float = 0f,
    var translateY: Float = 0f,
    var viewportWidth: Float = 0f,
    var viewportHeight: Float = 0f
) {
    companion object {
        const val MIN_SCALE = 0.01f
        // MAX_SCALE 由内存限制，无硬性上限
    }

    /**
     * 世界坐标转屏幕坐标
     */
    fun worldToScreen(worldX: Float, worldY: Float): PointF {
        return PointF(
            worldX * scale + translateX,
            worldY * scale + translateY
        )
    }

    /**
     * 屏幕坐标转世界坐标（用于触摸事件）
     */
    fun screenToWorld(screenX: Float, screenY: Float): PointF {
        return PointF(
            (screenX - translateX) / scale,
            (screenY - translateY) / scale
        )
    }

    /**
     * 以指定焦点进行缩放
     */
    fun zoomAt(focusX: Float, focusY: Float, scaleFactor: Float) {
        val newScale = (scale * scaleFactor).coerceAtLeast(MIN_SCALE)
        val actualFactor = newScale / scale

        translateX = focusX - (focusX - translateX) * actualFactor
        translateY = focusY - (focusY - translateY) * actualFactor
        scale = newScale
    }

    /**
     * 重置为初始状态
     */
    fun reset() {
        scale = 1f
        translateX = 0f
        translateY = 0f
    }

    /**
     * 获取当前可视区域的边界（世界坐标）
     */
    fun getVisibleBounds(): VisibleBounds {
        val topLeft = screenToWorld(0f, 0f)
        val bottomRight = screenToWorld(viewportWidth, viewportHeight)
        return VisibleBounds(
            left = topLeft.x,
            top = topLeft.y,
            right = bottomRight.x,
            bottom = bottomRight.y
        )
    }

    data class VisibleBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )
}