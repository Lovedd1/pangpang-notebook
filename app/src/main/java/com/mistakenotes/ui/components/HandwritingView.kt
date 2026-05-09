package com.mistakenotes.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

data class PathData(
    val path: Path,
    val paint: Paint
)

class HandwritingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paths = mutableListOf<PathData>()
    private var currentPath: Path? = null
    private val currentPaint = Paint().apply {
        color = Color.parseColor("#E8E4DC")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    var strokeColor: Int = Color.parseColor("#E8E4DC")
        set(value) {
            field = value
            currentPaint.color = value
        }

    var strokeWidth: Float = 4f
        set(value) {
            field = value
            currentPaint.strokeWidth = value
        }

    private var lastX = 0f
    private var lastY = 0f

    init {
        setBackgroundColor(Color.parseColor("#242424"))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = Path().apply {
                    moveTo(x, y)
                }
                lastX = x
                lastY = y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath?.let { path ->
                    // 使用 quadraticBezierTo 实现更平滑的曲线
                    path.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
                    lastX = x
                    lastY = y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                currentPath?.let { path ->
                    path.lineTo(x, y)
                    paths.add(PathData(Path(path), Paint(currentPaint)))
                    currentPath = null
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制已完成的路径
        paths.forEach { pathData ->
            canvas.drawPath(pathData.path, pathData.paint)
        }

        // 绘制当前正在绘制的路径
        currentPath?.let { path ->
            canvas.drawPath(path, currentPaint)
        }
    }

    fun clear() {
        paths.clear()
        currentPath = null
        invalidate()
    }

    fun undo() {
        if (paths.isNotEmpty()) {
            paths.removeAt(paths.size - 1)
            invalidate()
        }
    }

    fun getPaths(): List<PathData> = paths.toList()
}