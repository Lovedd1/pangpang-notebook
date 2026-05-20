# 草稿纸重构设计方案

> 日期：2026-05-19

## 一、需求概述

重构错题笔记应用的草稿纸功能，基于 WriteBuddy + compose-stylus + DrawBox + perfect-freehand 四个开源项目。

### 核心需求

| 需求 | 说明 |
|------|------|
| 手写答题与草稿纸切换 | 右侧按钮切换，面板内容独立保留 |
| 多工具支持 | 画笔、荧光笔、橡皮擦、形状工具（直线/矩形/圆形） |
| 工具可调粗细 | 所有工具支持粗细调节 |
| 压力感应 | 画笔压力越大笔画越粗 |
| 像素级橡皮擦 | 手指切换橡皮擦模式，支持按压力度自动触发，Bitmap级擦除 |
| 触控笔锁定 | 笔落下锁定缩放/拖动，笔抬起解锁 |
| 笔迹平滑 | Catmull-Rom 样条曲线，30fps |
| 形状拖动调整 | 形状工具画完可拖动、调整大小 |
| 半透明叠加 | 荧光笔半透明，显示在文字底部 |
| 自动保存 | 提交前自动保存，重进不消失，提交后删除 |

---

## 二、技术方案

### 分层架构

```
DraftPaperView（主容器）
├── PathLayer（矢量层）
│   ├── 笔迹 Path 列表
│   ├── 荧光笔 Path 列表
│   └── 形状 Path 列表（含变换矩阵）
├── BitmapLayer（像素层）
│   └── 用于橡皮擦时的像素操作
├── UILayer（交互层）
│   ├── 工具栏（顶部）
│   ├── 面板切换按钮（右侧）
│   └── 形状选择手柄
└── CanvasBackground（背景层）
    └── 空白/网格/横线 + 底色
```

### 组件职责

| 组件 | 职责 | 参考来源 |
|------|------|----------|
| `DraftPaperView` | 主容器，管理 PathLayer + BitmapLayer 切换 | 自行设计 |
| `PathLayer` | 矢量 Path 存储、绘制、撤销/重做 | DrawBox |
| `BitmapLayer` | 像素级橡皮擦、导出图片 | 自行设计 |
| `PenStroke` | 单笔笔画数据（含压力曲线） | compose-stylus |
| `ToolState` | 工具状态管理 | ReviewViewModel 现有 |
| `DrawingEngine` | 统一入口，分发绘制/擦除事件 | 自行设计 |

### 工作流程

```
用户触摸事件
    ↓
DrawingEngine 接收
    ↓
根据当前工具分发
    ├── 笔/荧光笔/形状 → PathLayer.addPath()
    ├── 橡皮擦 → BitmapLayer.erase() → 合并回 PathLayer
    └── 缩放/拖动 → TransformState
```

### 双面板设计

- `AnswerCanvas`：手写答题区（后续可OCR识别）
- `DraftCanvas`：草稿纸（纯打草稿）
- 右侧切换按钮：`[✏ 草稿纸] / [✓ 答题区]`
- 两面板独立保留笔迹
- 提交后同时清除

---

## 三、工具栏设计

### 顶部工具栏

```
[撤销 | 重做] | [笔 | 荧光笔 | 橡皮擦 | 形状 | 纸张 | 清空] | [缩放%]
```

### 右侧属性面板

| 选中工具 | 显示内容 |
|----------|----------|
| 笔 | 颜色按钮（蓝/黑/红）+ 粗细按钮（0.1/0.3/0.5mm） |
| 荧光笔 | 颜色 + 粗细 + 透明度滑块 |
| 橡皮擦 | 大小滑块（5-50px）+ 光标预览圆圈 |
| 形状 | 类型（直线/矩形/圆形）+ 颜色 + 粗细 |
| 纸张 | 背景（空白/网格/横线）+ 底色（黑/白/肉）|

---

## 四、交互逻辑

### 触控笔交互

| 状态 | 行为 |
|------|------|
| 笔落下 | 锁定缩放/拖动 |
| 笔抬起 | 解锁缩放/拖动 |
| 橡皮擦模式 | 手指切换，显示光标预览 |

### 形状交互

| 状态 | 行为 |
|------|------|
| 绘制完成 | 显示选中手柄（8个控制点） |
| 拖动手柄 | 调整形状大小/旋转 |
| 拖动形状 | 移动位置 |
| 点击空白 | 取消选中 |

### 橡皮擦模式

1. 用户点击橡皮擦工具 → 切换到 BitmapLayer
2. 绘制内容从 PathLayer 合并到 BitmapLayer
3. 橡皮擦用 PorterDuffXfermode 实现像素级擦除
4. 擦除完成可选择：保持 Bitmap 或转回 Path

---

## 五、GitHub 项目使用计划

### 计划引用的项目

| 项目 | 用途 | 使用方式 |
|------|------|----------|
| WriteBuddy | 架构参考、便签/激光/胶带扩展基础 | 架构设计参考 |
| compose-stylus | 压力感应、PenTool 枚举 | 提取 PenEvent 处理逻辑 |
| DrawBox | PathLayer 撤销/重做实现 | 参考其 undo/redo 机制 |
| perfect-freehand | 压力曲线算法 | 参考其压力-粗细映射 |

### 需要额外实现的部分

| 功能 | 实现方式 |
|------|----------|
| 像素级橡皮擦 | PorterDuffXfermode + Bitmap |
| 形状拖动调整 | 自定义 View + Matrix 变换 |
| 双面板切换 | Compose State |

### 遇到 4 个项目之外技术的处理方式

1. 向用户汇报技术需求
2. 在 GitHub 搜索对应开源项目
3. 获得用户确认后引用

---

## 六、文件清单

### 新增文件

- `app/src/main/java/com/mistakenotes/ui/components/drawing/DraftPaperView.kt`
- `app/src/main/java/com/mistakenotes/ui/components/drawing/PathLayer.kt`
- `app/src/main/java/com/mistakenotes/ui/components/drawing/BitmapLayer.kt`
- `app/src/main/java/com/mistakenotes/ui/components/drawing/PenStroke.kt`
- `app/src/main/java/com/mistakenotes/ui/components/drawing/DrawingEngine.kt`
- `app/src/main/java/com/mistakenotes/ui/components/drawing/ShapeTool.kt`

### 修改文件

- `app/src/main/java/com/mistakenotes/ui/screens/ReviewScreen.kt`
- `app/src/main/java/com/mistakenotes/ui/screens/ReviewViewModel.kt`
- `app/src/main/java/com/mistakenotes/ui/components/HandwritingView.kt`（删除，替换为新架构）

---

## 七、性能目标

| 指标 | 目标 |
|------|------|
| 帧率 | 30fps |
| 绘制延迟 | < 20ms |
| 撤销/重做步数 | 50 步 |
| 内存占用 | < 100MB |