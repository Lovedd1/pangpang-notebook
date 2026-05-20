package com.mistakenotes.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.mistakenotes.ui.components.drawing.*

class HandwritingCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 子模块
    private val pathLayer = PathLayer()
    private val bitmapLayer = BitmapLayer(1, 1)
    private val undoRedoManager = UndoRedoManager(50)
    private val strokeRenderer = StrokeRenderer()
    private val transformState = TransformState()

    // 当前工具状态
    private var currentTool: DrawingTool = DrawingTool.PEN
    private var eraserTool = EraserTool()

    // 笔属性
    var penColor: Int = Color.BLACK
        set(value) {
            field = value
            currentPaint.color = value
        }

    var penSize: Float = 8f
        set(value) {
            field = value
            currentPaint.strokeWidth = value
        }

    // penThickness 兼容属性（供 ReviewScreen 使用）
    var penThickness: Float
        get() = penSize
        set(value) { penSize = value }

    // 高亮笔属性
    var highlighterColor: Int = Color.YELLOW
    var highlighterSize: Float = 16f

    // 纸张属性
    var canvasBackground: CanvasBackground = CanvasBackground.BLANK
    var paperColor: PaperColor = PaperColor.BLACK

    // 当前绘制中的点
    private var currentPoints = mutableListOf<StrokePoint>()

    // 触控笔状态
    private var isZoomLocked = false

    // 背景画笔
    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    // 当前绘制画笔
    private val currentPaint = Paint().apply {
        color = penColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // 缩放回调
    var onScaleChangeListener: ((Float) -> Unit)? = null

    init {
        setBackgroundColor(paperColor.colorInt)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        transformState.viewportWidth = w.toFloat()
        transformState.viewportHeight = h.toFloat()
        bitmapLayer.setSize(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制背景
        canvas.drawColor(paperColor.colorInt)

        // 应用变换
        canvas.save()
        canvas.translate(transformState.translateX, transformState.translateY)
        canvas.scale(transformState.scale, transformState.scale)

        // 绘制网格/横线
        drawBackground(canvas)

        // 绘制路径层
        pathLayer.draw(canvas)

        // 绘制当前正在绘制的笔画
        if (currentPoints.size >= 2) {
            when (currentTool) {
                DrawingTool.PEN -> {
                    strokeRenderer.renderStroke(
                        canvas,
                        currentPoints,
                        StrokeRenderer.RenderOptions(
                            color = penColor,
                            size = penSize,
                            thinning = 0.5f,
                            smoothing = 0.5f,
                            streamline = 0.5f
                        )
                    )
                }
                DrawingTool.HIGHLIGHTER -> {
                    strokeRenderer.renderHighlighterStroke(canvas, currentPoints, highlighterColor, highlighterSize)
                }
                DrawingTool.ERASER -> {
                    // 橡皮擦模式不在这里绘制
                }
            }
        }

        canvas.restore()
    }

    private fun drawBackground(canvas: Canvas) {
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

        val viewWidth = transformState.viewportWidth / transformState.scale
        val viewHeight = transformState.viewportHeight / transformState.scale

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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 检测触控笔
        val isStylus = event.pointerCount == 1 && event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isStylus) {
                    isZoomLocked = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isStylus) {
                    isZoomLocked = false
                }
            }
        }

        // 如果缩放被锁定，处理绘制
        if (isZoomLocked && currentTool != DrawingTool.ERASER) {
            handleDrawEvent(event)
            return true
        }

        // 橡皮擦模式下也锁定
        if (currentTool == DrawingTool.ERASER && event.action == MotionEvent.ACTION_DOWN) {
            // 切换到 Bitmap 模式
            bitmapLayer.renderFromPathLayer(pathLayer, paperColor.colorInt)
            handleEraseEvent(event)
            return true
        }

        if (currentTool == DrawingTool.ERASER) {
            handleEraseEvent(event)
            return true
        }

        // 处理缩放/平移
        return handlePanZoomEvent(event)
    }

    private fun handleDrawEvent(event: MotionEvent) {
        val screenX = event.x
        val screenY = event.y
        val worldPoint = transformState.screenToWorld(screenX, screenY)
        val pressure = event.pressure.coerceIn(0f, 1f)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPoints.clear()
                currentPoints.add(StrokePoint(worldPoint.x, worldPoint.y, pressure))
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentPoints.isNotEmpty()) {
                    val last = currentPoints.last()
                    val dx = worldPoint.x - last.x
                    val dy = worldPoint.y - last.y
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (dist >= 2f) {
                        currentPoints.add(StrokePoint(worldPoint.x, worldPoint.y, pressure))
                    }
                } else {
                    currentPoints.add(StrokePoint(worldPoint.x, worldPoint.y, pressure))
                }
            }
            MotionEvent.ACTION_UP -> {
                if (currentPoints.size >= 2) {
                    // 保存撤销状态
                    undoRedoManager.saveState(pathLayer, bitmapLayer)

                    // 创建并添加路径
                    val pathData = strokeRenderer.createPathData(
                        currentPoints,
                        StrokeRenderer.RenderOptions(
                            color = penColor,
                            size = penSize,
                            thinning = 0.5f,
                            smoothing = 0.5f,
                            streamline = 0.5f
                        )
                    )
                    pathData?.let { pathLayer.addPath(it.path, it.paint, currentTool) }
                }
                currentPoints.clear()
            }
        }

        invalidate()
    }

    private fun handleEraseEvent(event: MotionEvent) {
        val screenX = event.x
        val screenY = event.y
        val pressure = event.pressure.coerceIn(0f, 1f)
        val radius = eraserTool.getRadius(pressure)

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // 在 Bitmap 上擦除
                // 注意：这里需要将屏幕坐标转换为 Bitmap 坐标
                val worldPoint = transformState.screenToWorld(screenX, screenY)
                // 实际擦除逻辑需要考虑缩放
                // 简化版本：直接用屏幕坐标
                bitmapLayer.erase(screenX, screenY, radius * transformState.scale)
            }
            MotionEvent.ACTION_UP -> {
                // 擦除完成，可以选择将结果合并回 PathLayer
            }
        }

        invalidate()
    }

    private fun handlePanZoomEvent(event: MotionEvent): Boolean {
        // 简单的双指缩放和平移实现
        // 这里需要更复杂的实现，简化版本暂不支持
        return true
    }

    // ==================== 公共 API ====================

    fun undo(): Boolean {
        val result = undoRedoManager.undo(pathLayer, bitmapLayer)
        if (result) invalidate()
        return result
    }

    fun redo(): Boolean {
        val result = undoRedoManager.redo(pathLayer, bitmapLayer)
        if (result) invalidate()
        return result
    }

    fun canUndo(): Boolean = undoRedoManager.canUndo()
    fun canRedo(): Boolean = undoRedoManager.canRedo()

    fun clear() {
        undoRedoManager.saveState(pathLayer, bitmapLayer)
        pathLayer.clear()
        bitmapLayer.clear()
        invalidate()
    }

    fun setTool(tool: DrawingTool) {
        currentTool = tool
    }

    fun getTool(): DrawingTool = currentTool

    fun setEraserSize(size: Float) {
        eraserTool = eraserTool.copy(baseSize = size)
    }

    fun resetTransform() {
        transformState.reset()
        onScaleChangeListener?.invoke(transformState.scale)
        invalidate()
    }

    fun getScale(): Float = transformState.scale

    fun getPaths(): List<PathData> = pathLayer.getPaths()
}

// 纸张背景类型
enum class CanvasBackground {
    BLANK,
    GRID,
    LINES
}

// 纸张颜色
enum class PaperColor(val colorInt: Int) {
    BLACK(0xFF242424.toInt()),
    WHITE(Color.WHITE),
    SKIN(0xFFF5E6D3.toInt())
}
