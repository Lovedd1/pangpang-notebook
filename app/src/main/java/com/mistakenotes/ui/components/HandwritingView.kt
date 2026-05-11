package com.mistakenotes.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
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
    WHITE(Color.parseColor("#FFFFFF")),     // 白色
    SKIN(Color.parseColor("#F5E6D3"))       // 肉色/米黄
}

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

    // 画布背景类型
    var canvasBackground: CanvasBackground = CanvasBackground.BLANK
        set(value) {
            field = value
            invalidate()
        }

    // 纸张底色
    var paperColor: PaperColor = PaperColor.BLACK
        set(value) {
            field = value
            setBackgroundColor(value.colorInt)
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

    // 橡皮擦模式
    var isEraserMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    // 橡皮擦半径（像素）
    var eraserRadius: Float = 30f
        set(value) {
            field = value
            invalidate()
        }

    // 切换橡皮擦模式
    fun toggleEraserMode() {
        isEraserMode = !isEraserMode
    }

    // 橡皮擦光标位置（屏幕坐标）
    private var eraserCursorX: Float = 0f
    private var eraserCursorY: Float = 0f
    private var showEraserCursor: Boolean = false

    // 采样距离阈值（像素），小于此值不采样，避免抖动
    private val sampleDistance = 2f

    // 缩放相关
    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f
    private val minScale = 1f
    private val maxScale = 5f
    private var isScaling = false

    // A4 纸张尺寸（毫米）
    companion object {
        val A4_WIDTH_MM = 210f
        val A4_HEIGHT_MM = 297f
    }

    // View 尺寸
    private var viewWidth = 0f
    private var viewHeight = 0f

    // 缩放比例回调（用于UI显示）
    var onScaleChangeListener: ((Float) -> Unit)? = null

    // 单指拖动相关（笔写模式下用手指拖动画布）
    private var isSingleFingerDragging = false
    private var singleFingerStartX = 0f
    private var singleFingerStartY = 0f
    private var dragStartTranslateX = 0f
    private var dragStartTranslateY = 0f

    // 双指缩放检测器
    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val oldScale = scaleFactor
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(minScale, maxScale)

            // 以缩放中心点为基准调整平移
            val focusX = detector.focusX
            val focusY = detector.focusY
            val scaleChange = scaleFactor / oldScale

            translateX = focusX - (focusX - translateX) * scaleChange
            translateY = focusY - (focusY - translateY) * scaleChange

            // 限制拖动范围，内容不能完全移出可见区域
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

    // 双指拖动相关
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

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

    init {
        setBackgroundColor(paperColor.colorInt)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 获取父视图给的可用空间
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)

        // A4 比例 1:√2 (约 1:1.414)
        val a4Ratio = A4_HEIGHT_MM / A4_WIDTH_MM // 约 1.414

        // 根据可用空间计算 A4 尺寸（使用宽度作为基准）
        val a4Width = availableWidth
        val a4Height = (a4Width * a4Ratio).toInt().coerceAtMost(availableHeight)

        setMeasuredDimension(a4Width, a4Height)
        viewWidth = a4Width.toFloat()
        viewHeight = a4Height.toFloat()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 检查是否为触控笔
        val isStylus = event.pointerCount == 1 && event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS

        // 笔写模式下使用触控笔时，禁用缩放
        if (isPenMode && isStylus) {
            // 处理书写逻辑
            if (isEraserMode) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        eraserCursorX = event.x
                        eraserCursorY = event.y
                        showEraserCursor = true
                        eraseAt(event.x, event.y, eraserRadius)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        eraserCursorX = event.x
                        eraserCursorY = event.y
                        eraseAt(event.x, event.y, eraserRadius)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        showEraserCursor = false
                    }
                }
                invalidate()
                return true
            }
            handleStylusEvent(event.action, event.x, event.y)
            return true
        }

        // 优先处理缩放手势
        scaleGestureDetector.onTouchEvent(event)

        // 如果正在缩放，不处理其他触摸事件
        if (isScaling) {
            return true
        }

        // 双指时清除正在书写的内容
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    currentStylusPoints.clear()
                    currentFingerPoints.clear()
                    isSingleFingerDragging = false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isSingleFingerDragging = false
            }
        }

        // 双指时不允许书写（由 ScaleGestureDetector 处理缩放和平移）
        if (event.pointerCount == 2) {
            return true
        }

        // 单指处理（根据模式决定是书写还是拖动）
        if (event.pointerCount == 1) {
            val x = event.x
            val y = event.y

            val toolType = event.getToolType(0)

            // 橡皮擦模式下显示光标
            if (isEraserMode) {
                eraserCursorX = x
                eraserCursorY = y
                showEraserCursor = true
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> eraseAt(x, y, eraserRadius)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> showEraserCursor = false
                }
                invalidate()
                return true
            }

            when (toolType) {
                MotionEvent.TOOL_TYPE_STYLUS -> {
                    // 触控笔：笔写模式下书写
                    if (isPenMode) {
                        handleStylusTouch(event.action, x, y)
                    }
                }
                MotionEvent.TOOL_TYPE_FINGER,
                MotionEvent.TOOL_TYPE_MOUSE -> {
                    if (isPenMode) {
                        // 笔写模式下，手指单指用于拖动画布
                        handleSingleFingerDrag(event.action, x, y)
                    } else {
                        // 手写模式下，手指用于书写
                        handleFingerEvent(event.action, x, y)
                    }
                }
                else -> {
                    if (!isPenMode) {
                        handleFingerEvent(event.action, x, y)
                    }
                }
            }
            return true
        }

        return true
    }

    private fun handleSingleFingerDrag(action: Int, x: Float, y: Float) {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                isSingleFingerDragging = true
                singleFingerStartX = x
                singleFingerStartY = y
                dragStartTranslateX = translateX
                dragStartTranslateY = translateY
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSingleFingerDragging) {
                    val newTranslateX = dragStartTranslateX + (x - singleFingerStartX)
                    val newTranslateY = dragStartTranslateY + (y - singleFingerStartY)

                    // 限制拖动范围
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

    // 触控笔书写时也要考虑画布变换
    private fun handleStylusTouch(action: Int, screenX: Float, screenY: Float) {
        val canvasX = (screenX - translateX) / scaleFactor
        val canvasY = (screenY - translateY) / scaleFactor
        handleStylusEvent(action, canvasX, canvasY)
    }

    // 手指书写时也要考虑画布变换
    private fun handleFingerTouch(action: Int, screenX: Float, screenY: Float) {
        val canvasX = (screenX - translateX) / scaleFactor
        val canvasY = (screenY - translateY) / scaleFactor
        handleFingerEvent(action, canvasX, canvasY)
    }

    private fun handleStylusEvent(action: Int, x: Float, y: Float) {
        if (isEraserMode) {
            when (action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    eraseAt(x, y, eraserRadius)
                }
            }
            return
        }
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                currentStylusPoints.clear()
                currentStylusPoints.add(PointF(x, y))
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentStylusPoints.isNotEmpty()) {
                    val last = currentStylusPoints.last()
                    val dx = x - last.x
                    val dy = y - last.y
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (dist >= sampleDistance) {
                        currentStylusPoints.add(PointF(x, y))
                    }
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
        if (isEraserMode) {
            when (action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    eraseAt(x, y, eraserRadius)
                }
            }
            return
        }
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                currentFingerPoints.clear()
                currentFingerPoints.add(PointF(x, y))
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentFingerPoints.isEmpty()) {
                    currentFingerPoints.add(PointF(x, y))
                } else {
                    val last = currentFingerPoints.last()
                    val dx = x - last.x
                    val dy = y - last.y
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (dist >= sampleDistance) {
                        currentFingerPoints.add(PointF(x, y))
                    }
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

    private fun handleSingleInput(action: Int, x: Float, y: Float): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                currentFingerPoints.clear()
                currentFingerPoints.add(PointF(x, y))
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentFingerPoints.isEmpty()) {
                    currentFingerPoints.add(PointF(x, y))
                } else {
                    val last = currentFingerPoints.last()
                    val dx = x - last.x
                    val dy = y - last.y
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (dist >= sampleDistance) {
                        currentFingerPoints.add(PointF(x, y))
                    }
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

    private fun buildCurrentPath(points: List<PointF>): Path {
        return buildSmoothPath(points)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 保存canvas状态（包含当前变换）
        canvas.save()

        // 裁剪到内容可见区域（在变换之前应用，所以是屏幕坐标）
        // 内容区域：translateX 到 translateX + viewWidth*scaleFactor
        val screenLeft = maxOf(0f, translateX)
        val screenTop = maxOf(0f, translateY)
        val screenRight = minOf(width.toFloat(), viewWidth * scaleFactor + translateX)
        val screenBottom = minOf(height.toFloat(), viewHeight * scaleFactor + translateY)
        canvas.clipRect(screenLeft, screenTop, screenRight, screenBottom)

        // 应用缩放和平移变换
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)

        // 绘制背景（网格或横线）
        drawCanvasBackground(canvas)

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

        canvas.restore()

        // 绘制橡皮擦光标（在画布变换之外，使用屏幕坐标）
        if (showEraserCursor && isEraserMode) {
            val cursorPaint = Paint().apply {
                color = Color.parseColor("#80D4A574") // 半透明金色
                style = Paint.Style.STROKE
                strokeWidth = 2f
                isAntiAlias = true
            }
            canvas.drawCircle(eraserCursorX, eraserCursorY, eraserRadius, cursorPaint)
        }
    }

    private fun drawCanvasBackground(canvas: Canvas) {
        // 根据纸张底色选择线条颜色
        val lineColor = when (paperColor) {
            PaperColor.BLACK -> Color.parseColor("#3A3A3A")  // 深灰线
            PaperColor.WHITE -> Color.parseColor("#CCCCCC") // 浅灰线
            PaperColor.SKIN -> Color.parseColor("#D4C4B0")  // 暖灰线
        }

        val linePaint = Paint().apply {
            color = lineColor
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        when (canvasBackground) {
            CanvasBackground.BLANK -> {
                // 不绘制背景
            }
            CanvasBackground.GRID -> {
                // 绘制网格（20dp 间隔）
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
                // 绘制横线（40dp 间隔）
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

    // 重置缩放和平移到初始状态
    fun resetTransform() {
        scaleFactor = 1f
        translateX = 0f
        translateY = 0f
        onScaleChangeListener?.invoke(scaleFactor)
        invalidate()
    }

    // 获取当前缩放比例
    fun getScale(): Float = scaleFactor

    // 橡皮擦：检测并删除与指定区域相交的路径段
    fun eraseAt(x: Float, y: Float, radius: Float = 30f) {
        val canvasX = (x - translateX) / scaleFactor
        val canvasY = (y - translateY) / scaleFactor

        // 移除触控笔路径中与橡皮擦区域相交的路径
        stylusPaths.removeAll { pathData ->
            pathIntersectsWithCircle(pathData.path, canvasX, canvasY, radius)
        }

        // 移除手指路径中与橡皮擦区域相交的路径
        fingerPaths.removeAll { pathData ->
            pathIntersectsWithCircle(pathData.path, canvasX, canvasY, radius)
        }

        invalidate()
    }

    // 检测路径是否与圆形区域相交
    private fun pathIntersectsWithCircle(path: Path, cx: Float, cy: Float, radius: Float): Boolean {
        val bounds = android.graphics.RectF()
        path.computeBounds(bounds, true)
        // 简单检测：圆心到路径边界框的距离
        val closestX = cx.coerceIn(bounds.left, bounds.right)
        val closestY = cy.coerceIn(bounds.top, bounds.bottom)
        val dx = cx - closestX
        val dy = cy - closestY
        return (dx * dx + dy * dy) <= (radius * radius)
    }

    fun getStylusPaths(): List<PathData> = stylusPaths.toList()
    fun getFingerPaths(): List<PathData> = fingerPaths.toList()
}