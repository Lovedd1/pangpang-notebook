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

    // 写入模式：true = 笔写模式（触控笔可写，手指不可写），false = 手写模式（手指可写，触控笔不可写）
    var isPenMode: Boolean = true

    // 切换写入模式
    fun togglePenMode() {
        isPenMode = !isPenMode
        invalidate()
    }

    // 触控笔路径（单独管理）
    private val stylusPaths = mutableListOf<PathData>()
    private var currentStylusPoints = mutableListOf<PointF>()

    // 手指路径（单独管理）
    private val fingerPaths = mutableListOf<PathData>()
    private var currentFingerPoints = mutableListOf<PointF>()

    // 触控笔画笔（金色）
    private val stylusPaint = Paint().apply {
        color = Color.parseColor("#D4A574")
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    // 手指画笔（默认色）
    private val fingerPaint = Paint().apply {
        color = Color.parseColor("#E8E4DC")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    // 是否启用双笔模式（默认开启）
    var dualModeEnabled: Boolean = true

    // 采样距离阈值（像素），小于此值不采样，避免抖动
    private val sampleDistance = 2f

    var stylusColor: Int = Color.parseColor("#D4A574")
        set(value) {
            field = value
            stylusPaint.color = value
        }

    var fingerColor: Int = Color.parseColor("#E8E4DC")
        set(value) {
            field = value
            fingerPaint.color = value
        }

    var stylusStrokeWidth: Float = 5f
        set(value) {
            field = value
            stylusPaint.strokeWidth = value
        }

    var fingerStrokeWidth: Float = 4f
        set(value) {
            field = value
            fingerPaint.strokeWidth = value
        }

    // 点类
    private data class PointF(val x: Float, val y: Float)

    init {
        setBackgroundColor(Color.parseColor("#242424"))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (dualModeEnabled) {
            handleDualInput(event)
            return true
        }
        return handleSingleInput(event)
    }

    private fun handleDualInput(event: MotionEvent) {
        val pointerCount = event.pointerCount

        for (i in 0 until pointerCount) {
            val toolType = event.getToolType(i)
            val x = event.getX(i)
            val y = event.getY(i)

            when (toolType) {
                MotionEvent.TOOL_TYPE_STYLUS -> {
                    // 笔写模式时触控笔可写，手写模式时触控笔不可写
                    if (isPenMode) {
                        handleStylusEvent(event.actionMasked, x, y)
                    }
                }
                MotionEvent.TOOL_TYPE_FINGER,
                MotionEvent.TOOL_TYPE_MOUSE -> {
                    // 手写模式时手指可写，笔写模式时手指不可写
                    if (!isPenMode) {
                        handleFingerEvent(event.actionMasked, x, y)
                    }
                }
            }
        }
    }

    private fun handleStylusEvent(action: Int, x: Float, y: Float) {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                currentStylusPoints.clear()
                currentStylusPoints.add(PointF(x, y))
            }
            MotionEvent.ACTION_MOVE -> {
                // 距离采样：避免过于密集的点
                val last = currentStylusPoints.last()
                val dx = x - last.x
                val dy = y - last.y
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist >= sampleDistance) {
                    currentStylusPoints.add(PointF(x, y))
                }
            }
            MotionEvent.ACTION_UP -> {
                if (currentStylusPoints.size >= 2) {
                    val path = buildSmoothPath(currentStylusPoints)
                    stylusPaths.add(PathData(path, Paint(stylusPaint)))
                }
                currentStylusPoints.clear()
            }
        }
        invalidate()
    }

    private fun handleFingerEvent(action: Int, x: Float, y: Float) {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                currentFingerPoints.clear()
                currentFingerPoints.add(PointF(x, y))
            }
            MotionEvent.ACTION_MOVE -> {
                val last = currentFingerPoints.last()
                val dx = x - last.x
                val dy = y - last.y
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist >= sampleDistance) {
                    currentFingerPoints.add(PointF(x, y))
                }
            }
            MotionEvent.ACTION_UP -> {
                if (currentFingerPoints.size >= 2) {
                    val path = buildSmoothPath(currentFingerPoints)
                    fingerPaths.add(PathData(path, Paint(fingerPaint)))
                }
                currentFingerPoints.clear()
            }
        }
        invalidate()
    }

    private fun handleSingleInput(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentFingerPoints.clear()
                currentFingerPoints.add(PointF(x, y))
            }
            MotionEvent.ACTION_MOVE -> {
                val last = currentFingerPoints.last()
                val dx = x - last.x
                val dy = y - last.y
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist >= sampleDistance) {
                    currentFingerPoints.add(PointF(x, y))
                }
            }
            MotionEvent.ACTION_UP -> {
                if (currentFingerPoints.size >= 2) {
                    val path = buildSmoothPath(currentFingerPoints)
                    fingerPaths.add(PathData(path, Paint(fingerPaint)))
                }
                currentFingerPoints.clear()
            }
        }
        invalidate()
        return true
    }

    // Catmull-Rom 样条：确保直线画出来是直的，曲线自然平滑
    private fun buildSmoothPath(points: List<PointF>): Path {
        val path = Path()
        if (points.size < 2) return path

        path.moveTo(points[0].x, points[0].y)

        when {
            points.size == 2 -> {
                // 只有两个点，直接连线
                path.lineTo(points[1].x, points[1].y)
            }
            points.size == 3 -> {
                // 三个点，用二次贝塞尔曲线（更精确控制）
                path.quadTo(
                    points[1].x, points[1].y,
                    points[2].x, points[2].y
                )
            }
            else -> {
                // 四个点以上：用 Catmull-Rom 样条插值
                for (i in 0 until points.size - 1) {
                    val p0 = if (i > 0) points[i - 1] else points[0]
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    val p3 = if (i < points.size - 2) points[i + 2] else points[points.size - 1]

                    // Catmull-Rom 到 二阶贝塞尔 的转换
                    // 控制点 = 当前点 + (下一个点 - 前一个点) / 6
                    val cp1x = p1.x + (p2.x - p0.x) / 6f
                    val cp1y = p1.y + (p2.y - p0.y) / 6f
                    val cp2x = p2.x - (p3.x - p1.x) / 6f
                    val cp2y = p2.y - (p3.y - p1.y) / 6f

                    path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                }
            }
        }

        return path
    }

    // 构建当前正在绘制的路径（实时预览）
    private fun buildCurrentPath(points: List<PointF>): Path {
        return buildSmoothPath(points)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制手指路径
        fingerPaths.forEach { pathData ->
            canvas.drawPath(pathData.path, pathData.paint)
        }

        // 绘制当前手指路径（实时预览）
        if (currentFingerPoints.size >= 2) {
            canvas.drawPath(buildCurrentPath(currentFingerPoints), fingerPaint)
        }

        // 绘制触控笔路径（在上层）
        stylusPaths.forEach { pathData ->
            canvas.drawPath(pathData.path, pathData.paint)
        }

        // 绘制当前触控笔路径（实时预览）
        if (currentStylusPoints.size >= 2) {
            canvas.drawPath(buildCurrentPath(currentStylusPoints), stylusPaint)
        }
    }

    fun clear() {
        stylusPaths.clear()
        fingerPaths.clear()
        currentStylusPoints.clear()
        currentFingerPoints.clear()
        invalidate()
    }

    fun undo() {
        when {
            currentStylusPoints.isNotEmpty() -> currentStylusPoints.removeAt(currentStylusPoints.size - 1)
            stylusPaths.isNotEmpty() -> stylusPaths.removeAt(stylusPaths.size - 1)
            currentFingerPoints.isNotEmpty() -> currentFingerPoints.removeAt(currentFingerPoints.size - 1)
            fingerPaths.isNotEmpty() -> fingerPaths.removeAt(fingerPaths.size - 1)
        }
        invalidate()
    }

    fun undoStylus() {
        when {
            currentStylusPoints.isNotEmpty() -> currentStylusPoints.removeAt(currentStylusPoints.size - 1)
            stylusPaths.isNotEmpty() -> stylusPaths.removeAt(stylusPaths.size - 1)
        }
        invalidate()
    }

    fun undoFinger() {
        when {
            currentFingerPoints.isNotEmpty() -> currentFingerPoints.removeAt(currentFingerPoints.size - 1)
            fingerPaths.isNotEmpty() -> fingerPaths.removeAt(fingerPaths.size - 1)
        }
        invalidate()
    }

    fun getStylusPaths(): List<PathData> = stylusPaths.toList()
    fun getFingerPaths(): List<PathData> = fingerPaths.toList()
}