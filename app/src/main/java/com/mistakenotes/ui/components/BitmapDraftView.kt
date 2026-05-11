package com.mistakenotes.ui.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.mistakenotes.ui.components.drawing.CoordTransformer
import com.mistakenotes.ui.components.drawing.Stroke
import com.mistakenotes.ui.components.drawing.StrokeRecorder

/**
 * 基于 Bitmap 的手写视图，支持像素级橡皮擦
 * 用于草稿纸功能
 *
 * 重构后使用：
 * - CoordTransformer: 统一坐标变换
 * - StrokeRecorder: 笔画级撤销（内存降低10x）
 */
class BitmapDraftView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 写入模式：true = 笔写模式（触控笔可写，手指不可写），false = 手写模式（手指可写，触控笔不可写）
    var isPenMode: Boolean = true

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
            rebuildBackgroundBitmap()
            invalidate()
        }

    // 橡皮擦模式
    var isEraserMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    // 橡皮擦半径（像素）
    var eraserRadius: Float = 30f

    // 笔迹粗细级别
    enum class StrokeWidthLevel { THIN, MEDIUM, THICK }

    // 笔类型
    enum class PenType { FOUNTAIN_PEN, BALLPOINT_PEN }

    // 笔迹颜色
    var penColor: Int = Color.parseColor("#D4A574")
        set(value) {
            field = value
            stylusPaint.color = value
            fingerPaint.color = adjustColorForFinger(value)
        }

    private fun adjustColorForFinger(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * 0.9f).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }

    var strokeWidthLevel: StrokeWidthLevel = StrokeWidthLevel.MEDIUM
        set(value) {
            field = value
            updateStrokeWidth()
        }

    var penType: PenType = PenType.FOUNTAIN_PEN
        set(value) {
            field = value
            updatePenStyle()
        }

    private fun updateStrokeWidth() {
        stylusPaint.strokeWidth = when (strokeWidthLevel) {
            StrokeWidthLevel.THIN -> 3f
            StrokeWidthLevel.MEDIUM -> 5f
            StrokeWidthLevel.THICK -> 8f
        }
        fingerPaint.strokeWidth = when (strokeWidthLevel) {
            StrokeWidthLevel.THIN -> 2f
            StrokeWidthLevel.MEDIUM -> 4f
            StrokeWidthLevel.THICK -> 6f
        }
    }

    private fun updatePenStyle() {
        when (penType) {
            PenType.FOUNTAIN_PEN -> {
                stylusPaint.strokeCap = Paint.Cap.BUTT
                stylusPaint.strokeJoin = Paint.Join.BEVEL
            }
            PenType.BALLPOINT_PEN -> {
                stylusPaint.strokeCap = Paint.Cap.ROUND
                stylusPaint.strokeJoin = Paint.Join.ROUND
            }
        }
    }

    // 白板模式（无限画布）
    var isWhiteboardMode: Boolean = false

    // 无限画布模式：画布可向任意方向扩展
    var isInfiniteCanvasMode: Boolean = false

    // 画布扩展倍数（相对于视图尺寸）
    private val canvasExpandRatio = 3f

    // 切换写入模式
    fun togglePenMode() {
        isPenMode = !isPenMode
        invalidate()
    }

    // 缩放相关
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

    // 清空确认回调
    var onClearRequested: (() -> Unit)? = null

    // 坐标变换器
    private val transformer = CoordTransformer()

    // 笔画记录器（替代Bitmap拷贝式撤销）
    private val strokeRecorder = StrokeRecorder(maxHistory = 50)

    // 撤销/重做
    fun undo(): Boolean {
        val stroke = strokeRecorder.undo() ?: return false
        redrawAllStrokes()
        invalidate()
        return true
    }

    fun redo(): Boolean {
        val stroke = strokeRecorder.redo() ?: return false
        redrawAllStrokes()
        invalidate()
        return true
    }

    fun canUndo(): Boolean = strokeRecorder.canUndo()
    fun canRedo(): Boolean = strokeRecorder.canRedo()

    // Bitmap 相关
    private var drawingBitmap: Bitmap? = null
    private var drawingCanvas: Canvas? = null
    private var backgroundBitmap: Bitmap? = null
    private var backgroundCanvas: Canvas? = null

    // 画笔
    private val stylusPaint = Paint().apply {
        color = Color.parseColor("#D4A574")
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        isFilterBitmap = true
    }

    private val fingerPaint = Paint().apply {
        color = Color.parseColor("#E8E4DC")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        isFilterBitmap = true
    }

    // 初始化笔迹粗细和笔类型
    init {
        updateStrokeWidth()
        updatePenStyle()
    }

    // 橡皮擦画笔
    private val eraserPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    // 橡皮擦光标
    private var eraserCursorX: Float = 0f
    private var eraserCursorY: Float = 0f
    private var showEraserCursor: Boolean = false

    // 采样距离阈值
    private val sampleDistance = 2f

    // 当前书写点
    private var currentPoints = mutableListOf<PointF>()
    private var currentPaint: Paint? = null

    // 当前笔画信息（用于记录）
    private var currentStrokeColor: Int = Color.parseColor("#D4A574")
    private var currentStrokeWidth: Float = 5f
    private var currentStrokeCap: Paint.Cap = Paint.Cap.ROUND
    private var currentStrokeJoin: Paint.Join = Paint.Join.ROUND

    // 双指缩放检测器
    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val oldScale = transformer.scaleFactor
            transformer.scaleFactor *= detector.scaleFactor
            transformer.scaleFactor = transformer.scaleFactor.coerceIn(minScale, maxScale)

            val focusX = detector.focusX
            val focusY = detector.focusY
            val scaleChange = transformer.scaleFactor / oldScale

            transformer.translateX = focusX - (focusX - transformer.translateX) * scaleChange
            transformer.translateY = focusY - (focusY - transformer.translateY) * scaleChange

            transformer.constrainTranslate(
                if (isInfiniteCanvasMode) {
                    (viewWidth * canvasExpandRatio - viewWidth) / 2 + viewWidth * (transformer.scaleFactor - 1)
                } else {
                    viewWidth * (transformer.scaleFactor - 1) / 2
                },
                if (isInfiniteCanvasMode) {
                    (viewHeight * canvasExpandRatio - viewHeight) / 2 + viewHeight * (transformer.scaleFactor - 1)
                } else {
                    viewHeight * (transformer.scaleFactor - 1) / 2
                }
            )

            onScaleChangeListener?.invoke(transformer.scaleFactor)
            invalidate()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
        }
    })

    // 单指拖动相关
    private var isSingleFingerDragging = false
    private var singleFingerStartX = 0f
    private var singleFingerStartY = 0f
    private var dragStartTranslateX = 0f
    private var dragStartTranslateY = 0f

    init {
        setBackgroundColor(paperColor.colorInt)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()

        transformer.setCanvasSize(w.toFloat(), h.toFloat())

        if (w > 0 && h > 0) {
            rebuildDrawingBitmap()
            rebuildBackgroundBitmap()
            redrawAllStrokes()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)

        if (isWhiteboardMode) {
            setMeasuredDimension(availableWidth, availableHeight)
            viewWidth = availableWidth.toFloat()
            viewHeight = availableHeight.toFloat()
        } else {
            val a4Ratio = A4_HEIGHT_MM / A4_WIDTH_MM
            val a4Width = availableWidth
            val a4Height = (a4Width * a4Ratio).toInt().coerceAtMost(availableHeight)

            setMeasuredDimension(a4Width, a4Height)
            viewWidth = a4Width.toFloat()
            viewHeight = a4Height.toFloat()
        }
    }

    private fun rebuildBackgroundBitmap() {
        if (viewWidth > 0 && viewHeight > 0) {
            backgroundBitmap?.recycle()
            backgroundBitmap = Bitmap.createBitmap(viewWidth.toInt(), viewHeight.toInt(), Bitmap.Config.ARGB_8888)
            backgroundCanvas = Canvas(backgroundBitmap!!)
            drawBackgroundToBitmap(backgroundCanvas!!)
        }
    }

    private fun rebuildDrawingBitmap() {
        if (viewWidth > 0 && viewHeight > 0) {
            drawingBitmap?.recycle()
            val width = if (isInfiniteCanvasMode) {
                (viewWidth * canvasExpandRatio).toInt()
            } else {
                viewWidth.toInt()
            }
            val height = if (isInfiniteCanvasMode) {
                (viewHeight * canvasExpandRatio).toInt()
            } else {
                viewHeight.toInt()
            }
            drawingBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            drawingCanvas = Canvas(drawingBitmap!!)

            if (isInfiniteCanvasMode) {
                transformer.canvasOffsetX = (width - viewWidth) / 2f
                transformer.canvasOffsetY = (height - viewHeight) / 2f
            } else {
                transformer.canvasOffsetX = 0f
                transformer.canvasOffsetY = 0f
            }
        }
    }

    private fun drawBackgroundToBitmap(canvas: Canvas) {
        canvas.drawColor(paperColor.colorInt)

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
                while (x <= canvas.width) {
                    canvas.drawLine(x, 0f, x, canvas.height.toFloat(), linePaint)
                    x += gridSize
                }
                var y = 0f
                while (y <= canvas.height) {
                    canvas.drawLine(0f, y, canvas.width.toFloat(), y, linePaint)
                    y += gridSize
                }
            }
            CanvasBackground.LINES -> {
                val lineSpacing = 60f
                var y = lineSpacing
                while (y <= canvas.height) {
                    canvas.drawLine(0f, y, canvas.width.toFloat(), y, linePaint)
                    y += lineSpacing
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)

        if (isScaling) {
            return true
        }

        if (event.pointerCount == 2) {
            currentPoints.clear()
            return true
        }

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isEraserMode) {
                    eraserCursorX = x
                    eraserCursorY = y
                    showEraserCursor = true
                    eraseAtCanvas(x, y)
                } else {
                    val toolType = event.getToolType(0)
                    val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS

                    if (isStylus && isPenMode) {
                        currentPaint = stylusPaint
                        currentStrokeColor = stylusPaint.color
                        currentStrokeWidth = stylusPaint.strokeWidth
                        currentStrokeCap = stylusPaint.strokeCap
                        currentStrokeJoin = stylusPaint.strokeJoin
                    } else if (!isPenMode) {
                        currentPaint = fingerPaint
                        currentStrokeColor = fingerPaint.color
                        currentStrokeWidth = fingerPaint.strokeWidth
                        currentStrokeCap = fingerPaint.strokeCap
                        currentStrokeJoin = fingerPaint.strokeJoin
                    } else if (isPenMode && !isStylus) {
                        isSingleFingerDragging = true
                        singleFingerStartX = x
                        singleFingerStartY = y
                        dragStartTranslateX = transformer.translateX
                        dragStartTranslateY = transformer.translateY
                        return true
                    }

                    currentPoints.clear()
                    strokeRecorder.beginStroke()

                    val canvasPoint = transformer.screenToInfiniteCanvas(x, y)
                    currentPoints.add(PointF(canvasPoint.x, canvasPoint.y))
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isEraserMode) {
                    eraserCursorX = x
                    eraserCursorY = y
                    eraseAtCanvas(x, y)
                } else if (isSingleFingerDragging && event.pointerCount == 1) {
                    val newTranslateX = dragStartTranslateX + (x - singleFingerStartX)
                    val newTranslateY = dragStartTranslateY + (y - singleFingerStartY)
                    transformer.translateX = newTranslateX
                    transformer.translateY = newTranslateY
                    transformer.constrainTranslate(
                        if (isInfiniteCanvasMode) {
                            (viewWidth * canvasExpandRatio - viewWidth) / 2 + viewWidth * (transformer.scaleFactor - 1)
                        } else {
                            viewWidth * (transformer.scaleFactor - 1) / 2
                        },
                        if (isInfiniteCanvasMode) {
                            (viewHeight * canvasExpandRatio - viewHeight) / 2 + viewHeight * (transformer.scaleFactor - 1)
                        } else {
                            viewHeight * (transformer.scaleFactor - 1) / 2
                        }
                    )
                } else {
                    if (currentPoints.isNotEmpty()) {
                        val canvasPoint = transformer.screenToInfiniteCanvas(x, y)
                        val last = currentPoints.last()
                        val dx = canvasPoint.x - last.x
                        val dy = canvasPoint.y - last.y
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (dist >= sampleDistance) {
                            currentPoints.add(PointF(canvasPoint.x, canvasPoint.y))
                            if (currentPoints.size >= 2 && currentPaint != null) {
                                val canvas = drawingCanvas ?: return true
                                val p1 = currentPoints[currentPoints.size - 2]
                                val p2 = currentPoints[currentPoints.size - 1]
                                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, currentPaint!!)
                            }
                        }
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isEraserMode) {
                    showEraserCursor = false
                }
                isSingleFingerDragging = false

                // 保存笔画到记录器
                if (currentPoints.isNotEmpty() && currentPaint != null) {
                    val stroke = Stroke(
                        points = currentPoints.toList(),
                        color = currentStrokeColor,
                        strokeWidth = currentStrokeWidth,
                        strokeCap = currentStrokeCap,
                        strokeJoin = currentStrokeJoin
                    )
                    strokeRecorder.addStroke(stroke)
                }

                currentPoints.clear()
                currentPaint = null
                invalidate()
            }
        }
        return true
    }

    private fun eraseAtCanvas(x: Float, y: Float) {
        val canvas = drawingCanvas ?: return
        eraserPaint.strokeWidth = eraserRadius * 2
        val canvasPoint = transformer.screenToInfiniteCanvas(x, y)
        canvas.drawCircle(canvasPoint.x, canvasPoint.y, eraserRadius, eraserPaint)
    }

    private fun redrawAllStrokes() {
        drawingCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val paint = Paint()
        for (stroke in strokeRecorder.getAllStrokes()) {
            stroke.draw(drawingCanvas!!, paint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制背景
        backgroundBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        canvas.save()

        // 裁剪
        val screenLeft = maxOf(0f, transformer.translateX)
        val screenTop = maxOf(0f, transformer.translateY)
        val screenRight = minOf(width.toFloat(), viewWidth * transformer.scaleFactor + transformer.translateX)
        val screenBottom = minOf(height.toFloat(), viewHeight * transformer.scaleFactor + transformer.translateY)
        canvas.clipRect(screenLeft, screenTop, screenRight, screenBottom)

        // 应用变换
        canvas.translate(transformer.translateX, transformer.translateY)
        canvas.scale(transformer.scaleFactor, transformer.scaleFactor)

        // 绘制内容 bitmap
        if (isInfiniteCanvasMode) {
            drawingBitmap?.let { bitmap ->
                canvas.save()
                canvas.clipRect(0f, 0f, viewWidth, viewHeight)
                canvas.translate(-transformer.canvasOffsetX, -transformer.canvasOffsetY)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                canvas.restore()
            }
        } else {
            drawingBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
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
            canvas.drawCircle(eraserCursorX, eraserCursorY, eraserRadius, cursorPaint)
        }
    }

    fun requestClear() {
        onClearRequested?.invoke()
    }

    fun confirmClear() {
        strokeRecorder.clear()
        drawingCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        drawingBitmap?.eraseColor(Color.TRANSPARENT)
        if (isInfiniteCanvasMode) {
            transformer.canvasOffsetX = (drawingBitmap?.width?.toFloat() ?: viewWidth) / 2 - viewWidth / 2
            transformer.canvasOffsetY = (drawingBitmap?.height?.toFloat() ?: viewHeight) / 2 - viewHeight / 2
        }
        rebuildBackgroundBitmap()
        invalidate()
    }

    fun clearDirect() {
        strokeRecorder.clear()
        drawingCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        drawingBitmap?.eraseColor(Color.TRANSPARENT)
        if (isInfiniteCanvasMode) {
            transformer.canvasOffsetX = (drawingBitmap?.width?.toFloat() ?: viewWidth) / 2 - viewWidth / 2
            transformer.canvasOffsetY = (drawingBitmap?.height?.toFloat() ?: viewHeight) / 2 - viewHeight / 2
        }
        rebuildBackgroundBitmap()
        invalidate()
    }

    fun resetCanvasOffset() {
        transformer.canvasOffsetX = (drawingBitmap?.width?.toFloat() ?: viewWidth) / 2 - viewWidth / 2
        transformer.canvasOffsetY = (drawingBitmap?.height?.toFloat() ?: viewHeight) / 2 - viewHeight / 2
        transformer.translateX = 0f
        transformer.translateY = 0f
        invalidate()
    }

    fun getScale(): Float = transformer.scaleFactor
}
