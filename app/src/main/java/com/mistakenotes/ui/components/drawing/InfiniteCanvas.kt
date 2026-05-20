package com.mistakenotes.ui.components.drawing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * 无限画布引擎
 * 支持无限制缩放和平移，触控笔锁定
 */
class InfiniteCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val transformState = TransformState()

    var isZoomLocked: Boolean = false
        private set

    var onTransformChangeListener: ((TransformState) -> Unit)? = null

    private var isScaling = false

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (isZoomLocked) return false
            isScaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (isZoomLocked) return false

            transformState.zoomAt(
                detector.focusX,
                detector.focusY,
                detector.scaleFactor
            )

            onTransformChangeListener?.invoke(transformState)
            invalidate()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
        }
    })

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        transformState.viewportWidth = w.toFloat()
        transformState.viewportHeight = h.toFloat()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 检测触控笔
        if (event.pointerCount == 1 && event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> isZoomLocked = true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isZoomLocked = false
            }
        }

        // 如果缩放被锁定，只处理触控笔事件
        if (isZoomLocked && event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            // 手指在触控笔模式下不处理缩放，但可以处理平移
            return false
        }

        scaleGestureDetector.onTouchEvent(event)
        return true
    }

    fun screenToWorld(screenX: Float, screenY: Float) = transformState.screenToWorld(screenX, screenY)
    fun worldToScreen(worldX: Float, worldY: Float) = transformState.worldToScreen(worldX, worldY)

    fun reset() {
        transformState.reset()
        onTransformChangeListener?.invoke(transformState)
        invalidate()
    }
}