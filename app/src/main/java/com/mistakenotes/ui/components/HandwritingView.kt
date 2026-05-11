package com.mistakenotes.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

data class PathData(
    val path: Path,
    val paint: Paint
)

enum class CanvasBackground {
    BLANK,   // 空白
    GRID,    // 网格
    LINES    // 横线
}

enum class PaperColor(val colorInt: Int) {
    BLACK(Color.parseColor("#242424")),      // 黑色
    WHITE(Color.parseColor("#FFFFFF")),       // 白色
    SKIN(Color.parseColor("#F5E6D3"))         // 肉色/米黄
}

class HandwritingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ========== 笔刷属性 ==========
    // 默认笔颜色
    companion object {
        val PEN_BLUE = Color.parseColor("#1E88E5")
        val PEN_BLACK = Color.parseColor("#000000")
        val PEN_RED = Color.parseColor("#E53935")

        // 笔厚度（毫米）
        val PEN_THIN = 0.1f
        val PEN_MEDIUM = 0.3f
        val PEN_THICK = 0.5f

        // A4 纸张尺寸（毫米）
        val A4_WIDTH_MM = 210f
        val A4_HEIGHT_MM = 297f

        // 橡皮擦尺寸
        const val ERASER_SIZE_DEFAULT = 20f
        const val ERASER_SIZE_MIN = 5f
        const val ERASER_SIZE_MAX = 50f
    }

    // 当前笔颜色（默认金色，砚台风格）
    var penColor: Int = Color.parseColor("#D4A574")
        set(value) {
            field = value
            currentPaint.color = value
        }

    // 当前笔厚度（毫米）
    var penThickness: Float = PEN_MEDIUM
        set(value) {
            field = value
            currentPaint.strokeWidth = mmToPx(value)
        }

    // 笔刷 Paint（统一使用）
    private val currentPaint = Paint().apply {
        color = penColor
        strokeWidth = mmToPx(penThickness)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    // mm 转像素
    fun mmToPx(mm: Float): Float {
        return mm * resources.displayMetrics.density
    }

    // ========== 橡皮擦属性 ==========
    var isEraserMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var eraserSize: Float = ERASER_SIZE_DEFAULT
        set(value) {
            field = value.coerceIn(ERASER_SIZE_MIN, ERASER_SIZE_MAX)
            invalidate()
        }

    private var eraserCursorX: Float = 0f
    private var eraserCursorY: Float = 0f
    private var showEraserCursor: Boolean = false

    fun toggleEraserMode() {
        isEraserMode = !isEraserMode
    }

    // ========== 触控笔锁定状态 ==========
    private var isStylusPressed: Boolean = false
    private var isZoomLocked: Boolean = false

    // ========== 写入模式 ==========
    // 写入模式：true = 笔写模式（触控笔可写，手指不可写），false = 手写模式（手指可写，触控笔不可写）
    var isPenMode: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    fun togglePenMode() {
        isPenMode = !isPenMode
        invalidate()
    }

    // ========== 画布背景类型 ==========
    var canvasBackground: CanvasBackground = CanvasBackground.BLANK
        set(value) {
            field = value
            invalidate()
        }

    // ========== 纸张底色 ==========
    var paperColor: PaperColor = PaperColor.BLACK
        set(value) {
            field = value
            setBackgroundColor(value.colorInt)
            invalidate()
        }

    // ========== 统一路径历史（支持撤销/重做） ==========
    private val pathHistory = mutableListOf<PathData>()
    private val redoHistory = mutableListOf<PathData>()
    private var currentPoints = mutableListOf<PointF>()

    fun canUndo(): Boolean = pathHistory.isNotEmpty()
    fun canRedo(): Boolean = redoHistory.isNotEmpty()

    fun undo() {
        if (pathHistory.isNotEmpty()) {
            val removed = pathHistory.removeAt(pathHistory.size - 1)
            redoHistory.add(removed)
            invalidate()
        }
    }

    fun redo() {
        if (redoHistory.isNotEmpty()) {
            val restored = redoHistory.removeAt(redoHistory.size - 1)
            pathHistory.add(restored)
            invalidate()
        }
    }

    // ========== 缩放相关 ==========
    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f
    private val minScale = 1f
    private val maxScale = 5f
    private var isScaling = false

    // View 尺寸
    private var viewWidth = 0f
    private var viewHeight = 0f

    // 缩放比例回调
    var onScaleChangeListener: ((Float) -> Unit)? = null

    // 单指拖动相关
    private var isSingleFingerDragging = false
    private var singleFingerStartX = 0f
    private var singleFingerStartY = 0f
    private var dragStartTranslateX = 0f
    private var dragStartTranslateY = 0f

    // ========== 双指缩放检测器 ==========
    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            // 触控笔按下时禁用缩放
            if (isZoomLocked) return false
            isScaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // 触控笔按下时禁用缩放
            if (isZoomLocked) return false

            val oldScale = scaleFactor
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(minScale, maxScale)

            val focusX = detector.focusX
            val focusY = detector.focusY
            val scaleChange = scaleFactor / oldScale

            translateX = focusX - (focusX - translateX) * scaleChange
            translateY = focusY - (focusY - translateY) * scaleChange

            val maxTranslate = viewWidth * (scaleFactor - 1) / 2
            translateX = translateX.coerceIn(-maxTranslate, maxTranslate)
            translateY = translateY.coerceIn(-maxTranslate, maxTranslate)

            onScaleChangeListener?.invoke(scaleFactor)
            invalidate()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
        }
    })

    // 采样距离阈值
    private val sampleDistance = 2f

    init {
        setBackgroundColor(paperColor.colorInt)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)

        val a4Ratio = A4_HEIGHT_MM / A4_WIDTH_MM

        val a4Width = availableWidth
        val a4Height = (a4Width * a4Ratio).toInt().coerceAtMost(availableHeight)

        setMeasuredDimension(a4Width, a4Height)
        viewWidth = a4Width.toFloat()
        viewHeight = a4Height.toFloat()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val isStylus = event.pointerCount == 1 && event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS

        // 触控笔按下/抬起时锁定/解锁缩放
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isStylus) {
                    isStylusPressed = true
                    isZoomLocked = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isStylus) {
                    isStylusPressed = false
                    isZoomLocked = false
                }
            }
        }

        // 触控笔按下时不允许缩放
        if (isZoomLocked) {
            if (isEraserMode) {
                handleEraserEvent(event)
            } else {
                handleStylusEvent(event)
            }
            return true
        }

        // 优先处理缩放手势
        scaleGestureDetector.onTouchEvent(event)

        if (isScaling) {
            return true
        }

        // 多指处理
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    currentPoints.clear()
                    isSingleFingerDragging = false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isSingleFingerDragging = false
            }
        }

        if (event.pointerCount == 2) {
            return true
        }

        // 单指处理
        if (event.pointerCount == 1) {
            if (isEraserMode) {
                handleEraserEvent(event)
                return true
            }

            val toolType = event.getToolType(0)

            when (toolType) {
                MotionEvent.TOOL_TYPE_STYLUS -> {
                    if (isPenMode) {
                        handleStylusEvent(event)
                    }
                }
                MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_MOUSE -> {
                    if (isPenMode) {
                        handleSingleFingerDrag(event.action, event.x, event.y)
                    } else {
                        handleFingerEvent(event.action, event.x, event.y)
                    }
                }
                else -> {
                    if (!isPenMode) {
                        handleFingerEvent(event.action, event.x, event.y)
                    }
                }
            }
            return true
        }

        return true
    }

    private fun handleEraserEvent(event: MotionEvent) {
        val screenX = event.x
        val screenY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                eraserCursorX = screenX
                eraserCursorY = screenY
                showEraserCursor = true
                eraseAt(screenX, screenY, eraserSize)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                showEraserCursor = false
            }
        }
        invalidate()
    }

    private fun handleStylusEvent(event: MotionEvent) {
        // 转换到画布坐标
        val canvasX = (event.x - translateX) / scaleFactor
        val canvasY = (event.y - translateY) / scaleFactor

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPoints.clear()
                currentPoints.add(PointF(canvasX, canvasY))
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentPoints.isNotEmpty()) {
                    val last = currentPoints.last()
                    val dx = canvasX - last.x
                    val dy = canvasY - last.y
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (dist >= sampleDistance) {
                        currentPoints.add(PointF(canvasX, canvasY))
                    }
                } else {
                    currentPoints.add(PointF(canvasX, canvasY))
                }
            }
            MotionEvent.ACTION_UP -> {
                if (currentPoints.size >= 2) {
                    val path = buildSmoothPath(currentPoints)
                    pathHistory.add(PathData(path, Paint(currentPaint)))
                    redoHistory.clear()
                }
                currentPoints.clear()
            }
        }
        invalidate()
    }

    private fun handleFingerEvent(action: Int, screenX: Float, screenY: Float) {
        val canvasX = (screenX - translateX) / scaleFactor
        val canvasY = (screenY - translateY) / scaleFactor

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                currentPoints.clear()
                currentPoints.add(PointF(canvasX, canvasY))
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentPoints.isEmpty()) {
                    currentPoints.add(PointF(canvasX, canvasY))
                } else {
                    val last = currentPoints.last()
                    val dx = canvasX - last.x
                    val dy = canvasY - last.y
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (dist >= sampleDistance) {
                        currentPoints.add(PointF(canvasX, canvasY))
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (currentPoints.size >= 2) {
                    val path = buildSmoothPath(currentPoints)
                    pathHistory.add(PathData(path, Paint(currentPaint)))
                    redoHistory.clear()
                }
                currentPoints.clear()
            }
        }
        invalidate()
    }

    private fun handleSingleFingerDrag(action: Int, screenX: Float, screenY: Float) {
        // 缩放锁定时不允许拖动
        if (isZoomLocked) return

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                isSingleFingerDragging = true
                singleFingerStartX = screenX
                singleFingerStartY = screenY
                dragStartTranslateX = translateX
                dragStartTranslateY = translateY
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSingleFingerDragging) {
                    val newTranslateX = dragStartTranslateX + (screenX - singleFingerStartX)
                    val newTranslateY = dragStartTranslateY + (screenY - singleFingerStartY)

                    val maxTranslate = viewWidth * (scaleFactor - 1) / 2
                    translateX = newTranslateX.coerceIn(-maxTranslate, maxTranslate)
                    translateY = newTranslateY.coerceIn(-maxTranslate, maxTranslate)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isSingleFingerDragging = false
            }
        }
    }

    // Catmull-Rom 样条：确保直线画出来是直的，曲线自然平滑
    private fun buildSmoothPath(points: List<PointF>): Path {
        val path = Path()
        if (points.size < 2) return path

        path.moveTo(points[0].x, points[0].y)

        when {
            points.size == 2 -> {
                path.lineTo(points[1].x, points[1].y)
            }
            points.size == 3 -> {
                path.quadTo(
                    points[1].x, points[1].y,
                    points[2].x, points[2].y
                )
            }
            else -> {
                for (i in 0 until points.size - 1) {
                    val p0 = if (i > 0) points[i - 1] else points[0]
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    val p3 = if (i < points.size - 2) points[i + 2] else points[points.size - 1]

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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()

        // 裁剪到内容可见区域
        val screenLeft = maxOf(0f, translateX)
        val screenTop = maxOf(0f, translateY)
        val screenRight = minOf(width.toFloat(), viewWidth * scaleFactor + translateX)
        val screenBottom = minOf(height.toFloat(), viewHeight * scaleFactor + translateY)
        canvas.clipRect(screenLeft, screenTop, screenRight, screenBottom)

        // 应用缩放和平移变换
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)

        // 绘制背景
        drawCanvasBackground(canvas)

        // 绘制所有已完成的路径
        pathHistory.forEach { pathData ->
            canvas.drawPath(pathData.path, pathData.paint)
        }

        // 绘制当前正在书写的路径
        if (currentPoints.size >= 2) {
            canvas.drawPath(buildSmoothPath(currentPoints), currentPaint)
        }

        canvas.restore()

        // 绘制橡皮擦光标
        if (showEraserCursor && isEraserMode) {
            val cursorPaint = Paint().apply {
                color = Color.parseColor("#80D4A574")
                style = Paint.Style.STROKE
                strokeWidth = 2f
                isAntiAlias = true
            }
            canvas.drawCircle(eraserCursorX, eraserCursorY, eraserSize, cursorPaint)
        }
    }

    private fun drawCanvasBackground(canvas: Canvas) {
        val lineColor = when (paperColor) {
            PaperColor.BLACK -> Color.parseColor("#3A3A3A")
            PaperColor.WHITE -> Color.parseColor("#CCCCCC")
            PaperColor.SKIN -> Color.parseColor("#D4C4B0")
        }

        val linePaint = Paint().apply {
            color = lineColor
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        when (canvasBackground) {
            CanvasBackground.BLANK -> { }
            CanvasBackground.GRID -> {
                val gridSize = 40f
                var x = 0f
                while (x <= viewWidth) {
                    canvas.drawLine(x, 0f, x, viewHeight, linePaint)
                    x += gridSize
                }
                var y = 0f
                while (y <= viewHeight) {
                    canvas.drawLine(0f, y, viewWidth, y, linePaint)
                    y += gridSize
                }
            }
            CanvasBackground.LINES -> {
                val lineSpacing = 60f
                var y = lineSpacing
                while (y <= viewHeight) {
                    canvas.drawLine(0f, y, viewWidth, y, linePaint)
                    y += lineSpacing
                }
            }
        }
    }

    fun clear() {
        pathHistory.clear()
        currentPoints.clear()
        redoHistory.clear()
        invalidate()
    }

    // 重置缩放和平移
    fun resetTransform() {
        scaleFactor = 1f
        translateX = 0f
        translateY = 0f
        onScaleChangeListener?.invoke(scaleFactor)
        invalidate()
    }

    fun getScale(): Float = scaleFactor

    // 橡皮擦：删除与圆形区域相交的路径
    fun eraseAt(screenX: Float, screenY: Float, radius: Float = eraserSize) {
        val canvasX = (screenX - translateX) / scaleFactor
        val canvasY = (screenY - translateY) / scaleFactor

        pathHistory.removeAll { pathData ->
            pathIntersectsWithCircle(pathData.path, canvasX, canvasY, radius / scaleFactor)
        }

        invalidate()
    }

    // 检测路径是否与圆形区域相交
    private fun pathIntersectsWithCircle(path: Path, cx: Float, cy: Float, radius: Float): Boolean {
        val bounds = RectF()
        path.computeBounds(bounds, true)
        val closestX = cx.coerceIn(bounds.left, bounds.right)
        val closestY = cy.coerceIn(bounds.top, bounds.bottom)
        val dx = cx - closestX
        val dy = cy - closestY
        return (dx * dx + dy * dy) <= (radius * radius)
    }

    fun getPaths(): List<PathData> = pathHistory.toList()
}