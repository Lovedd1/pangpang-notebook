# 复习草稿纸功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在复习页面实现草稿纸功能：顶部工具栏 + 左侧草稿纸/右侧题目左右分栏布局

**Architecture:** 重新设计 HandwritingView 组件支持笔写模式（笔落下锁定缩放，笔抬起解锁），复习页面采用左右分栏，工具栏通过 Compose 实现

**Tech Stack:** Kotlin + Jetpack Compose + Android View（HandwritingView）

---

## 文件结构

```
app/src/main/java/com/mistakenotes/ui/
├── components/
│   └── HandwritingView.kt          # 重新设计：笔写模式、像素级橡皮擦、撤销/重做
├── screens/
│   ├── ReviewScreen.kt             # 修改：左右分栏布局、工具栏
│   └── ReviewViewModel.kt          # 修改：工具状态管理
└── theme/
    └── InkStoneColors.kt           # 现有颜色
```

---

## 任务分解

### Task 1: 分析现有 HandwritingView 实现

**Files:**
- Read: `app/src/main/java/com/mistakenotes/ui/components/HandwritingView.kt`

- [ ] **Step 1: 阅读现有代码**

理解现有实现：
- Path 矢量绘制方式
- 触控笔/手指分离处理
- 缩放/拖动逻辑
- 橡皮擦实现
- 撤销/重做机制

---

### Task 2: 重新设计 HandwritingView 属性和接口

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/components/HandwritingView.kt`

- [ ] **Step 1: 添加笔颜色属性**

```kotlin
// 默认颜色（蓝、黑、红）
var penColorBlue: Int = Color.parseColor("#1E88E5")
var penColorBlack: Int = Color.parseColor("#000000")
var penColorRed: Int = Color.parseColor("#E53935")

// 当前选中的笔颜色
var currentPenColor: Int = penColorBlack
```

- [ ] **Step 2: 添加笔粗细属性（mm单位）**

```kotlin
// 笔粗细默认值
var penThickness0: Float = 0.1f  // mm
var penThickness1: Float = 0.3f  // mm
var penThickness2: Float = 0.5f  // mm

// 当前选中的粗细
var currentPenThickness: Float = penThickness1
```

- [ ] **Step 3: 添加像素到 mm 的转换**

```kotlin
// 假设屏幕密度，1mm ≈ 3.78px（以 160dpi 为基准）
// 实际应根据 displayMetrics.density 计算
private fun mmToPx(mm: Float): Float {
    return mm * 3.78f * resources.displayMetrics.density / 160f
}
```

- [ ] **Step 4: 添加橡皮擦属性**

```kotlin
// 橡皮擦大小（像素）
var eraserSize: Float = 20f  // 默认 20px
var eraserMinSize: Float = 5f
var eraserMaxSize: Float = 50f

// 橡皮擦光标
var showEraserCursor: Boolean = false
var eraserCursorX: Float = 0f
var eraserCursorY: Float = 0f
```

- [ ] **Step 5: 添加笔写模式锁定状态**

```kotlin
// 笔是否按下（用于控制缩放锁定）
var isStylusPressed: Boolean = false

// 是否锁定缩放/移动（笔按下时为 true）
var isZoomLocked: Boolean = false
```

- [ ] **Step 6: 添加撤销/重做支持**

```kotlin
// 使用 Path 历史记录实现撤销/重做
private val pathHistory = mutableListOf<PathData>()
private val redoHistory = mutableListOf<PathData>()

fun undo(): Boolean
fun redo(): Boolean
fun canUndo(): Boolean
fun canRedo(): Boolean
```

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/mistakenotes/ui/components/HandwritingView.kt
git commit -m "refactor(HandwritingView): add properties for new design"
```

---

### Task 3: 实现笔写模式逻辑（笔落下锁定，笔抬起解锁）

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/components/HandwritingView.kt`

- [ ] **Step 1: 修改 onTouchEvent 处理笔按下/抬起**

```kotlin
override fun onTouchEvent(event: MotionEvent): Boolean {
    val isStylus = event.pointerCount == 1 &&
        event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS

    when (event.action) {
        MotionEvent.ACTION_DOWN -> {
            if (isStylus) {
                isStylusPressed = true
                isZoomLocked = true  // 笔按下，锁定缩放
                // 开始书写...
            }
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            if (isStylus) {
                isStylusPressed = false
                isZoomLocked = false  // 笔抬起，解锁缩放
                // 结束书写...
            }
        }
    }
    // ...
}
```

- [ ] **Step 2: 修改缩放检测逻辑，在 isZoomLocked 时跳过**

```kotlin
private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        if (isZoomLocked) return false  // 笔写模式下禁用缩放
        isScaling = true
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        if (isZoomLocked) return false
        // ... 原有缩放逻辑
    }
})
```

- [ ] **Step 3: 修改单指拖动逻辑，在 isZoomLocked 时跳过**

```kotlin
private fun handleSingleFingerDrag(action: Int, x: Float, y: Float) {
    if (isZoomLocked) return  // 笔写模式下禁用拖动

    when (action) {
        // ... 原有拖动逻辑
    }
}
```

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/mistakenotes/ui/components/HandwritingView.kt
git commit -m "feat(HandwritingView): add stylus write mode with zoom lock"
```

---

### Task 4: 实现像素级橡皮擦

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/components/HandwritingView.kt`

- [ ] **Step 1: 添加 eraserMode 属性**

```kotlin
var isEraserMode: Boolean = false
    set(value) {
        field = value
        showEraserCursor = value
        invalidate()
    }
```

- [ ] **Step 2: 实现像素级橡皮擦方法**

```kotlin
/**
 * 像素级橡皮擦：检测并删除与指定区域相交的路径段
 * 使用 Path.computeBounds() 和圆的边界检测
 */
fun eraseAt(x: Float, y: Float, radius: Float) {
    val canvasX = (x - translateX) / scaleFactor
    val canvasY = (y - translateY) / scaleFactor

    pathHistory.removeAll { pathData ->
        pathIntersectsWithCircle(pathData.path, canvasX, canvasY, radius)
    }

    invalidate()
}

/**
 * 检测路径是否与圆形区域相交
 */
private fun pathIntersectsWithCircle(path: Path, cx: Float, cy: Float, radius: Float): Boolean {
    val bounds = android.graphics.RectF()
    path.computeBounds(bounds, true)

    // 检测圆心到路径边界框的距离
    val closestX = cx.coerceIn(bounds.left, bounds.right)
    val closestY = cy.coerceIn(bounds.top, bounds.bottom)
    val dx = cx - closestX
    val dy = cy - closestY
    return (dx * dx + dy * dy) <= (radius * radius)
}
```

- [ ] **Step 3: 在 onDraw 中绘制橡皮擦光标**

```kotlin
override fun onDraw(canvas: Canvas) {
    // ... 原有绘制逻辑 ...

    // 绘制橡皮擦光标
    if (showEraserCursor && isEraserMode) {
        val cursorPaint = Paint().apply {
            color = Color.parseColor("#80D4A574")  // 半透明金色
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawCircle(eraserCursorX, eraserCursorY, eraserSize, cursorPaint)
    }
}
```

- [ ] **Step 4: 在 touch 事件中更新光标位置**

```kotlin
if (isEraserMode) {
    eraserCursorX = x
    eraserCursorY = y
    when (event.action) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> eraseAt(x, y, eraserSize)
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> showEraserCursor = false
    }
    invalidate()
    return true
}
```

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/mistakenotes/ui/components/HandwritingView.kt
git commit -m "feat(HandwritingView): add pixel-level eraser with cursor"
```

---

### Task 5: 实现撤销/重做功能

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/components/HandwritingView.kt`

- [ ] **Step 1: 将 stylusPaths 和 fingerPaths 改为 pathHistory**

```kotlin
// 替换原有路径列表
private val pathHistory = mutableListOf<PathData>()
private val redoHistory = mutableListOf<PathData>()
```

- [ ] **Step 2: 实现 undo/redo 方法**

```kotlin
fun undo(): Boolean {
    if (pathHistory.isEmpty()) return false
    val path = pathHistory.removeAt(pathHistory.size - 1)
    redoHistory.add(path)
    invalidate()
    return true
}

fun redo(): Boolean {
    if (redoHistory.isEmpty()) return false
    val path = redoHistory.removeAt(redoHistory.size - 1)
    pathHistory.add(path)
    invalidate()
    return true
}

fun canUndo(): Boolean = pathHistory.isNotEmpty()
fun canRedo(): Boolean = redoHistory.isNotEmpty()
```

- [ ] **Step 3: 修改书写逻辑，书写完成时加入 pathHistory**

```kotlin
// 在 ACTION_UP 时
if (currentPoints.size >= 2) {
    val path = buildSmoothPath(currentPoints)
    val paint = Paint().apply {
        color = currentPenColor
        strokeWidth = mmToPx(currentPenThickness)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    pathHistory.add(PathData(path, paint))
    redoHistory.clear()  // 新笔画清空重做历史
}
```

- [ ] **Step 4: 修改 clear 方法**

```kotlin
fun clear() {
    pathHistory.clear()
    redoHistory.clear()
    invalidate()
}
```

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/mistakenotes/ui/components/HandwritingView.kt
git commit -m "feat(HandwritingView): add undo/redo support"
```

---

### Task 6: 修改 ReviewViewModel 管理工具状态

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ReviewViewModel.kt`

- [ ] **Step 1: 添加工具状态数据类**

```kotlin
data class ToolState(
    val selectedTool: String = "pen",  // pen, eraser, paper
    val penColor: Int = android.graphics.Color.parseColor("#000000"),
    val penThickness: Float = 0.3f,
    val eraserSize: Float = 20f,
    val canvasBackground: CanvasBackground = CanvasBackground.BLANK,
    val paperColor: PaperColor = PaperColor.BLACK,
    val scale: Float = 1f
)
```

- [ ] **Step 2: 添加状态和切换方法**

```kotlin
private val _toolState = MutableStateFlow(ToolState())
val toolState: StateFlow<ToolState> = _toolState

fun selectTool(tool: String) {
    _toolState.value = _toolState.value.copy(selectedTool = tool)
}

fun setPenColor(color: Int) {
    _toolState.value = _toolState.value.copy(penColor = color)
}

fun setPenThickness(thickness: Float) {
    _toolState.value = _toolState.value.copy(penThickness = thickness)
}

fun setEraserSize(size: Float) {
    _toolState.value = _toolState.value.copy(eraserSize = size)
}

fun setCanvasBackground(bg: CanvasBackground) {
    _toolState.value = _toolState.value.copy(canvasBackground = bg)
}

fun setPaperColor(color: PaperColor) {
    _toolState.value = _toolState.value.copy(paperColor = color)
}

fun updateScale(scale: Float) {
    _toolState.value = _toolState.value.copy(scale = scale)
}
```

- [ ] **Step 3: 移除旧的草稿纸状态（isDraftMode）相关代码**

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ReviewViewModel.kt
git commit -m "refactor(ReviewViewModel): add tool state management"
```

---

### Task 7: 修改 ReviewScreen 实现工具栏和左右分栏布局

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ReviewScreen.kt`

- [ ] **Step 1: 添加顶部工具栏布局**

```kotlin
@Composable
fun DraftToolbar(
    modifier: Modifier = Modifier,
    toolState: ToolState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onSelectTool: (String) -> Unit,
    onPenColorChange: (Int) -> Unit,
    onPenThicknessChange: (Float) -> Unit,
    onEraserSizeChange: (Float) -> Unit,
    onCanvasBgChange: (CanvasBackground) -> Unit,
    onPaperColorChange: (PaperColor) -> Unit
) {
    Row(
        modifier = modifier.height(48.dp).fillMaxWidth().background(InkStoneBg),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧 80%：主工具栏
        Row(
            modifier = Modifier.weight(0.8f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 撤销
            IconButton(onClick = onUndo) {
                Icon(Icons.Default.Undo, "撤销", tint = InkStoneTextDim)
            }
            // 重做
            IconButton(onClick = onRedo) {
                Icon(Icons.Default.Redo, "重做", tint = InkStoneTextDim)
            }
            Divider(modifier = Modifier.width(1.dp).height(20.dp), color = InkStoneBorder)
            // 笔
            ToolButton(tool = "pen", isSelected = toolState.selectedTool == "pen", onClick = { onSelectTool("pen") })
            // 橡皮擦
            ToolButton(tool = "eraser", isSelected = toolState.selectedTool == "eraser", onClick = { onSelectTool("eraser") })
            // 纸张
            ToolButton(tool = "paper", isSelected = toolState.selectedTool == "paper", onClick = { onSelectTool("paper") })
            // 清空
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Delete, "清空", tint = InkStoneError)
            }
        }

        // 右侧 20%：选项面板
        Surface(
            modifier = Modifier.weight(0.2f).fillMaxHeight(),
            color = InkStoneSurface,
            shape = RoundedCornerShape(6.dp)
        ) {
            // 根据选中工具显示不同选项
            when (toolState.selectedTool) {
                "pen" -> PenOptionsPanel(toolState, onPenColorChange, onPenThicknessChange)
                "eraser" -> EraserOptionsPanel(toolState.eraserSize, onEraserSizeChange)
                "paper" -> PaperOptionsPanel(toolState, onCanvasBgChange, onPaperColorChange)
            }
        }
    }
}
```

- [ ] **Step 2: 实现 PenOptionsPanel**

```kotlin
@Composable
fun PenOptionsPanel(
    toolState: ToolState,
    onPenColorChange: (Int) -> Unit,
    onPenThicknessChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 颜色按钮：蓝、黑、红
        ColorButton(color = penColorBlue, onClick = { onPenColorChange(penColorBlue) })
        ColorButton(color = penColorBlack, onClick = { onPenColorChange(penColorBlack) })
        ColorButton(color = penColorRed, onClick = { onPenColorChange(penColorRed) })

        Spacer(modifier = Modifier.width(8.dp))

        // 粗细按钮：0.1mm, 0.3mm, 0.5mm
        ThicknessButton(thickness = 0.1f, current = toolState.penThickness, onClick = { onPenThicknessChange(0.1f) })
        ThicknessButton(thickness = 0.3f, current = toolState.penThickness, onClick = { onPenThicknessChange(0.3f) })
        ThicknessButton(thickness = 0.5f, current = toolState.penThickness, onClick = { onPenThicknessChange(0.5f) })
    }
}
```

- [ ] **Step 3: 实现 EraserOptionsPanel（滑块调节大小）**

```kotlin
@Composable
fun EraserOptionsPanel(
    eraserSize: Float,
    onEraserSizeChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("S", fontSize = 10.sp, color = InkStoneTextDim)
        Slider(
            value = eraserSize,
            onValueChange = onEraserSizeChange,
            valueRange = 5f..50f,
            modifier = Modifier.weight(1f)
        )
        Text("L", fontSize = 10.sp, color = InkStoneTextDim)
    }
}
```

- [ ] **Step 4: 实现 PaperOptionsPanel**

```kotlin
@Composable
fun PaperOptionsPanel(
    toolState: ToolState,
    onCanvasBgChange: (CanvasBackground) -> Unit,
    onPaperColorChange: (PaperColor) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 背景：空白、网格、横线
        CanvasBgButton(bg = CanvasBackground.BLANK, isSelected = toolState.canvasBackground == CanvasBackground.BLANK, onClick = { onCanvasBgChange(CanvasBackground.BLANK) })
        CanvasBgButton(bg = CanvasBackground.GRID, isSelected = toolState.canvasBackground == CanvasBackground.GRID, onClick = { onCanvasBgChange(CanvasBackground.GRID) })
        CanvasBgButton(bg = CanvasBackground.LINES, isSelected = toolState.canvasBackground == CanvasBackground.LINES, onClick = { onCanvasBgChange(CanvasBackground.LINES) })

        Spacer(modifier = Modifier.width(4.dp))

        // 底色：黑、白、肉
        PaperColorButton(color = PaperColor.BLACK, isSelected = toolState.paperColor == PaperColor.BLACK, onClick = { onPaperColorChange(PaperColor.BLACK) })
        PaperColorButton(color = PaperColor.WHITE, isSelected = toolState.paperColor == PaperColor.WHITE, onClick = { onPaperColorChange(PaperColor.WHITE) })
        PaperColorButton(color = PaperColor.SKIN, isSelected = toolState.paperColor == PaperColor.SKIN, onClick = { onPaperColorChange(PaperColor.SKIN) })
    }
}
```

- [ ] **Step 5: 修改 QuestionContent 实现左右分栏**

```kotlin
Row(
    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
) {
    // 左侧：草稿纸（HandwritingView）
    Surface(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        color = InkStoneSurface,
        shape = RoundedCornerShape(12.dp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                HandwritingView(context).apply {
                    setBackgroundColor(toolState.paperColor.colorInt)
                }.also { onHandwritingViewReady(it) }
            },
            update = { view ->
                view.penColor = toolState.penColor
                view.currentPenThickness = toolState.penThickness
                view.isEraserMode = toolState.selectedTool == "eraser"
                view.eraserSize = toolState.eraserSize
                view.canvasBackground = toolState.canvasBackground
            }
        )
    }

    // 右侧：题目
    Surface(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        color = InkStoneSurface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "题目", color = InkStoneTextDim, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = question.recognizedQuestion, color = InkStoneText, fontSize = 16.sp)
        }
    }
}
```

- [ ] **Step 6: 添加提交后清空草稿纸逻辑**

在 `onMarkCorrect` 和 `onMarkWrong` 回调中：
```kotlin
onMarkCorrect = {
    handwritingView?.clear()
    viewModel.markAnswer(true)
},
```

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ReviewScreen.kt
git commit -m "feat(ReviewScreen): add draft paper toolbar and left/right layout"
```

---

### Task 8: 集成测试

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/components/HandwritingView.kt`

- [ ] **Step 1: 测试笔写模式锁定**

手动测试：
1. 用触控笔在草稿纸上书写
2. 笔按下时尝试双指缩放 → 应该被禁用
3. 笔抬起后尝试双指缩放 → 应该正常工作

- [ ] **Step 2: 测试橡皮擦**

手动测试：
1. 选择橡皮擦工具
2. 调节橡皮擦大小
3. 在画布上滑动，笔画应被像素级擦除
4. 圆圈光标应跟随手指移动

- [ ] **Step 3: 测试撤销/重做**

手动测试：
1. 书写几笔
2. 点击撤销 → 应回退一步
3. 点击重做 → 应恢复一步

- [ ] **Step 4: 测试提交后清空**

手动测试：
1. 在草稿纸上书写
2. 选择答案（选择题）或提交（大题）
3. 点击"做对了"或"做错了"
4. 草稿纸应自动清除

- [ ] **Step 5: 测试缩放**

手动测试：
1. 笔抬起时双指缩放 → 应正常工作
2. 缩放范围 100% - 500%
3. 缩放比例应显示在工具栏

---

## 验证清单

- [ ] 工具栏顶部横跨，左 80% 主工具，右 20% 选项面板
- [ ] 笔选项显示颜色（蓝/黑/红）和粗细（0.1/0.3/0.5mm）
- [ ] 橡皮擦选项显示大小滑块（5px - 50px）
- [ ] 纸张选项显示背景（空白/网格/横线）和底色（黑/白/肉）
- [ ] 撤销/重做功能正常
- [ ] 笔落下时锁定缩放/移动，笔抬起时解锁
- [ ] 橡皮擦是像素级，画布上显示圆圈光标
- [ ] 提交后草稿纸自动清除
- [ ] 缩放范围 100% - 500%，工具栏显示当前比例