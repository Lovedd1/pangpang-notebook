# 草稿纸重构设计文档 v2

> 日期：2026-05-19
> 版本：v2（基于用户反馈的修订版）

## 一、需求概述

重构错题笔记应用的草稿纸功能，参考以下开源项目：

| 项目 | 用途 |
|------|------|
| [infinipaint](https://github.com/ErrorAtLine0/infinipaint) | 无限缩放（无限画布）、压力感应、像素级橡皮擦 |
| [DrawBox](https://github.com/akshay2211/DrawBox) | 自定义笔画颜色和宽度、撤销/重做 |
| [WriteBuddy](https://github.com/henni99/WriteBuddy) | 画笔工具架构参考 |
| [perfect-freehand](https://github.com/steveruizok/perfect-freehand) | 压力感应笔画算法 |

### 核心需求

| 需求 | 说明 |
|------|------|
| 手写答题与草稿纸切换 | 右侧按钮切换，面板内容独立保留 |
| 多工具支持 | 画笔、荧光笔、橡皮擦 |
| 压力感应 | 画笔压力越大笔画越粗 |
| 像素级橡皮擦 | 手指/触控笔切换橡皮擦模式，支持按压力度自动触发，Bitmap级擦除 |
| 无限画布 | 画布可无限延伸，缩放无上限 |
| 触控笔锁定 | 笔落下锁定缩放/拖动，笔抬起解锁 |
| 笔迹平滑 | perfect-freehand 算法，直线不歪曲 |
| 撤销/重做 | 50步快照制 |
| 自动保存 | 提交前自动保存，重进不消失，提交后删除 |

---

## 二、技术决策

| 决策点 | 选择 |
|--------|------|
| 画布类型 | 无限画布（无缩放上限，内存限制） |
| 绘制技术 | 原生 Android View |
| 笔画算法 | perfect-freehand Kotlin 移植版 |
| 橡皮擦 | 像素级 Bitmap 擦除 |
| 触控笔 | 落下锁定缩放，抬起解锁 |
| 撤销/重做 | 50 步快照制 |

---

## 三、架构设计

### 3.1 文件结构

```
app/src/main/java/com/mistakenotes/ui/components/
├── HandwritingCanvas.kt          # 主画布 View（替换现有 HandwritingView）
└── drawing/
    ├── PerfectStroke.kt          # perfect-freehand 算法 Kotlin 版
    ├── StrokePoint.kt            # 笔画采样点数据类
    ├── StrokeRenderer.kt         # 笔画渲染器
    ├── InfiniteCanvas.kt         # 无限画布引擎
    ├── TransformState.kt         # 变换状态（缩放/平移）
    ├── BitmapLayer.kt            # 像素层（橡皮擦用）
    ├── PathLayer.kt              # 矢量层（笔画存储）
    ├── UndoRedoManager.kt        # 撤销/重做管理
    ├── EraserTool.kt             # 像素级橡皮擦工具
    └── DrawingEvent.kt           # 绘制事件类型
```

### 3.2 组件职责

| 类 | 职责 |
|----|------|
| `HandwritingCanvas` | 主 View，管理所有子模块，接收触摸事件 |
| `PerfectStroke` | 核心算法：输入点 → 平滑插值 → 输出多边形 |
| `StrokeRenderer` | 调用 PerfectStroke 生成 Path 并绘制 |
| `InfiniteCanvas` | 管理世界坐标系到屏幕坐标系的变换 |
| `TransformState` | 保存 scale/translate 状态，无限范围 |
| `BitmapLayer` | 存储 Bitmap，橡皮擦模式时合并 Path 到这里 |
| `PathLayer` | 存储所有笔画 Path，撤销/重做单位 |
| `UndoRedoManager` | 管理快照，50 步限制 |
| `EraserTool` | 像素级擦除逻辑 |

### 3.3 分层架构

```
HandwritingCanvas（主容器）
├── PathLayer（矢量层）
│   ├── 画笔 Path 列表
│   ├── 荧光笔 Path 列表
│   └── 形状 Path 列表
├── BitmapLayer（像素层）
│   └── 用于橡皮擦时的像素操作
├── UILayer（交互层）
│   ├── 工具栏（顶部）
│   └── 面板切换按钮（右侧）
└── CanvasBackground（背景层）
    └── 空白/网格/横线 + 底色
```

---

## 四、PerfectStroke 算法设计

### 4.1 算法原理

```
输入点 → 插值平滑 → 生成控制点 → 构建多边形边缘 → 输出 Path
```

### 4.2 核心参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `size` | 基础笔画粗细（直径，px） | 8 |
| `thinning` | 压力影响系数（0-1） | 0.5 |
| `smoothing` | 边缘平滑度（0-1） | 0.5 |
| `streamline` | 线条流畅度（0-1） | 0.5 |
| `simulatePressure` | 是否模拟压力 | false（用真实压力） |

### 4.3 StrokePoint 数据结构

```kotlin
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
    val tilt: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)
```

### 4.4 采样策略

- 距离采样：每 2px 采样一次（避免抖动）
- 压力采样：触控笔真实压力（0-1）
- 时间戳：用于计算速度（模拟压力时用）

---

## 五、无限画布引擎设计

### 5.1 TransformState（变换状态）

```kotlin
data class TransformState(
    var scale: Float = 1f,           // 缩放比例，无限范围
    var translateX: Float = 0f,      // X 平移
    var translateY: Float = 0f,      // Y 平移
    var viewportWidth: Float = 0f,   // 视口宽度
    var viewportHeight: Float = 0f   // 视口高度
) {
    // 世界坐标转屏幕坐标
    fun worldToScreen(worldX: Float, worldY: Float): PointF

    // 屏幕坐标转世界坐标（用于触摸事件）
    fun screenToWorld(screenX: Float, screenY: Float): PointF
}
```

### 5.2 缩放/平移约束

| 约束 | 值 |
|------|---|
| 缩放范围 | 0.01x ~ 无限（内存限制） |
| 平移范围 | 无限制 |
| 焦点缩放 | 缩放中心跟随手指中心 |

### 5.3 触控笔锁定逻辑

```kotlin
var isZoomLocked: Boolean = false

override fun onTouchEvent(event: MotionEvent): Boolean {
    val tool = getCurrentTool()

    // 触控笔落下：锁定缩放
    if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> isZoomLocked = true
            MotionEvent.ACTION_UP -> isZoomLocked = false
        }
    }

    // 橡皮擦模式：手指也触发
    if (tool is EraserTool && event.action == MotionEvent.ACTION_DOWN) {
        isZoomLocked = true
    }
}
```

---

## 六、像素级橡皮擦设计

### 6.1 BitmapLayer 工作原理

```
普通模式：Path → PathLayer → Canvas 绘制（矢量，内存小）

橡皮擦模式：
1. PathLayer 合并到 BitmapLayer（矢量转像素）
2. 橡皮擦在 BitmapLayer 上按像素擦除
3. 完成后可选择：
   - 保持 Bitmap（用于后续擦除）
   - 转换回 PathLayer（节省内存）
```

### 6.2 EraserTool 配置

| 参数 | 说明 | 范围 |
|------|------|------|
| `size` | 橡皮擦大小（直径，px） | 5-100 |
| `pressureSensitive` | 是否压力感应 | true/false |
| `shape` | 橡皮擦形状 | circle（圆形） |

### 6.3 擦除算法

```kotlin
fun erase(canvas: Canvas, x: Float, y: Float, pressure: Float) {
    val eraserRadius = if (pressureSensitive) {
        baseSize * pressure  // 压力大 = 擦除范围大
    } else {
        baseSize
    }

    // 使用 PorterDuff.Clear 模式擦除
    canvas.drawCircle(x, y, eraserRadius, erasePaint)
}
```

### 6.4 切换流程

```
用户选择橡皮擦工具
    ↓
BitmapLayer.createFromPathLayer(pathLayer)
    ↓
设置 PathLayer 为不可见
    ↓
橡皮擦模式：操作 BitmapLayer
    ↓
用户切换回画笔工具
    ↓
BitmapLayer 合并回 PathLayer（用 alpha 通道重建 Path，或保持 Bitmap）
```

---

## 七、撤销/重做管理设计

### 7.1 UndoRedoManager 策略

```kotlin
class UndoRedoManager(private val maxSteps: Int = 50) {

    private val undoStack = mutableListOf<CanvasSnapshot>()
    private val redoStack = mutableListOf<CanvasSnapshot>()

    data class CanvasSnapshot(
        val pathLayer: List<PathData>,
        val bitmapLayer: Bitmap?,
        val timestamp: Long
    )
}
```

### 7.2 快照触发时机

| 操作 | 触发快照 |
|------|----------|
| 笔画结束（UP） | 保存当前状态 |
| 橡皮擦擦除（每帧） | 不保存（避免过多快照） |
| 清空画布 | 保存 |
| 撤销/重做 | 保存当前到 redo/undo |

### 7.3 API

```kotlin
fun canUndo(): Boolean
fun canRedo(): Boolean
fun undo(): CanvasSnapshot?
fun redo(): CanvasSnapshot?
fun saveState(pathLayer: List<PathData>, bitmapLayer: Bitmap?)
```

---

## 八、工具栏与面板切换设计

### 8.1 工具类型枚举

```kotlin
enum class DrawingTool {
    PEN,           // 普通画笔
    HIGHLIGHTER,   // 荧光笔（半透明）
    ERASER,        // 橡皮擦（像素级）
}
```

### 8.2 工具属性

| 工具 | 属性 |
|------|------|
| PEN | 颜色、大小、压力感应开关 |
| HIGHLIGHTER | 颜色（半透明）、大小、透明度 |
| ERASER | 大小、压力感应开关 |

### 8.3 双面板设计

```
┌─────────────────────────────────────────────────────┐
│  [撤销][重做] | [笔][荧光笔][橡皮][纸张][清空]      │  ← 工具栏
├─────────────────────────────────┬───────────────────┤
│                                 │                   │
│        题目/内容区域              │  [✏ 草稿纸]      │
│                                 │  [✓ 答题区]       │
│                                 │                   │
│                                 │   右侧面板        │
│                                 │  (草稿纸/答题切换) │
│                                 │                   │
└─────────────────────────────────┴───────────────────┘
```

- **答题区**：手写答题，可选提交
- **草稿纸**：纯草稿，不提交
- 两者独立存储，切换时保持状态

---

## 九、参考来源汇总

| 功能 | 来源 |
|------|------|
| 压力感应笔画 | WriteBuddy（架构）+ perfect-freehand（算法） |
| 像素级橡皮擦 | infinipaint（概念） |
| 无限缩放 | infinipaint |
| 撤销/重做 | DrawBox |
| 颜色/粗细自定义 | DrawBox |

---

## 十、文件清单

### 新增文件

```
app/src/main/java/com/mistakenotes/ui/components/
├── HandwritingCanvas.kt          # 主画布 View
└── drawing/
    ├── PerfectStroke.kt          # 算法核心
    ├── StrokePoint.kt            # 数据类
    ├── StrokeRenderer.kt         # 渲染器
    ├── InfiniteCanvas.kt          # 无限画布引擎
    ├── TransformState.kt          # 变换状态
    ├── BitmapLayer.kt             # 像素层
    ├── PathLayer.kt               # 矢量层
    ├── UndoRedoManager.kt         # 撤销/重做
    ├── EraserTool.kt              # 橡皮擦工具
    └── DrawingEvent.kt            # 事件类型
```

### 修改文件

```
app/src/main/java/com/mistakenotes/ui/screens/
├── ReviewScreen.kt               # 适配新画布
└── ReviewViewModel.kt            # 工具状态管理
```

### 删除文件

```
app/src/main/java/com/mistakenotes/ui/components/
└── HandwritingView.kt            # 被 HandwritingCanvas 替换
```