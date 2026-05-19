# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 当前优先级最高任务

**草稿纸重构 v2**：基于 infinipaint + DrawBox + WriteBuddy + perfect-freehand 重构手写画布

- 设计文档：`docs/superpowers/specs/2026-05-19-draft-paper-redesign-v2-design.md`
- **实现计划**：`docs/superpowers/plans/2026-05-19-draft-paper-redesign-implementation.md`
- 核心功能：无限画布、压力感应笔画（perfect-freehand）、像素级橡皮擦、撤销/重做

**实现任务清单（按顺序）：**
1. `StrokePoint.kt` — 笔画采样点数据类
2. `PerfectStroke.kt` — perfect-freehand 算法核心
3. `TransformState.kt` + `InfiniteCanvas.kt` — 无限画布引擎
4. `PathLayer.kt` + `BitmapLayer.kt` — 矢量层和像素层
5. `UndoRedoManager.kt` — 撤销/重做管理
6. `DrawingEvent.kt` + `EraserTool.kt` — 事件类型和橡皮擦工具
7. `StrokeRenderer.kt` — 笔画渲染器
8. `HandwritingCanvas.kt` — 主视图（整合所有模块）
9. 删除 `HandwritingView.kt`
10. 更新 `ReviewScreen.kt` 和 `ReviewViewModel.kt`

### 项目结构

```
app/src/main/java/com/mistakenotes/ui/components/
├── HandwritingCanvas.kt          # 新主画布（重构后替换 HandwritingView）
└── drawing/
    ├── PerfectStroke.kt          # 算法核心
    ├── StrokePoint.kt            # 数据类
    ├── StrokeRenderer.kt         # 渲染器
    ├── InfiniteCanvas.kt         # 无限画布引擎
    ├── TransformState.kt         # 变换状态
    ├── BitmapLayer.kt            # 像素层
    ├── PathLayer.kt              # 矢量层
    ├── UndoRedoManager.kt        # 撤销/重做
    ├── EraserTool.kt            # 橡皮擦工具
    └── DrawingEvent.kt          # 事件类型
```

## 项目概述

错题笔记应用：支持手写的小米 6 Pro 平板应用，用于学习考试类错题的记录、整理和复习。纯本地使用，备份至百度网盘或夸克网盘。

- 设计规格：`docs/superpowers/specs/2026-05-08-mistake-notes-design.md`
- 草稿纸设计（v2）：`docs/superpowers/specs/2026-05-19-draft-paper-redesign-v2-design.md`
- 前端原型：`docs/prototypes/砚台版-full.html`

## 回答要求

除开发代码以外，回答用中文回答

## 技术栈

**Android 原生开发（Kotlin + Jetpack Compose）**

- 原因：手写体验最好，低延迟直接调用手写笔 API
- 最低支持：Android 8.0（API 26）
- JDK：11（Android Studio 内置）

**核心依赖**

- Jetpack Compose：UI 框架
- material-icons-extended：图标
- Room：本地数据库（KSP 编译器）
- Hilt：依赖注入
- Coil：图片加载
- Navigation Compose：页面导航
- DataStore：偏好设置

**文字识别**

- ~~DeepSeek API~~（已移除）
- ~~MiniMax API~~（已移除）
- ~~Google ML Kit~~（已移除，中文识别效果差）
- **Tesseract OCR**：本地 OCR，使用 chi_sim 中文语言包
  - 离线可用，无需网络
  - 中文识别效果好于 ML Kit
  - 语言包位置：`app/src/main/assets/chi_sim.traineddata`
  - 自动识别：选择图片后自动触发识别

**备份**

- 手动导出至百度网盘或夸克网盘

## UI 设计风格：砚台

**深色主题**，金色强调色，参考传统文房四宝意象。

| 元素 | 值 |
|------|-----|
| 背景色 | `#1A1A1A` |
| 卡片色 | `#242424` |
| 文字色 | `#E8E4DC` |
| 强调色 | `#D4A574`（金/琥珀色）|
| 成功色 | `#6ABF6A` |
| 错误色 | `#D44040` |

**关键交互特性**

- 侧边栏可折叠/展开（左上角切换按钮）
- 错题库支持自建库（自定义颜色、名称）
- 复习流程：题目显示 → 手写答题 → 提交 → 手动标记对错

## 已实现功能

### 手写画布（HandwritingCanvas 重构中）
- `ui/components/HandwritingView.kt` — 当前实现，原生 Android View
- **重构目标**：基于 perfect-freehand 算法 + 无限画布 + 像素级橡皮擦
- **实现计划**：`docs/superpowers/plans/2026-05-19-draft-paper-redesign-implementation.md`

### 错题录入
- **笔写模式**：只用触控笔书写，笔落下时锁定缩放/移动，笔抬起时解锁
- **笔属性**：颜色（蓝 #1E88E5 / 黑 #000000 / 红 #E53935），粗细（0.1/0.3/0.5mm）
- **撤销/重做**：快照制 undo/redo（50步），每笔独立可撤销
- **缩放/拖动**：双指捏合缩放（100%-500%），笔抬起时可操作
- **纸张底色**：黑色（#242424）/ 白色（#FFFFFF）/ 肉色（#F5E6D3）
- **纸张线型**：空白 / 网格（40dp间隔）/ 横线（60dp间隔）
- **Catmull-Rom 样条曲线**：直线笔直，曲线流畅
- **距离采样**：每 2 像素采样一次，避免抖动
- **内容裁剪**：超出可见区域的内容自动隐藏

### 错题录入
- `ui/screens/ImportScreen.kt` + `ui/screens/ImportViewModel.kt`
- `data/remote/TesseractOcrService.kt` — Tesseract OCR 服务（中文语言包）
- 支持拍照/相册选择题目图片
- **拍照后裁剪确认**：拍照后弹出对话框，可选择"裁剪"或"直接使用"
- **系统图库编辑**：裁剪使用系统图库（ACTION_EDIT），不同设备支持情况不同
- **题目标题**：用户可输入标题，用于区分列表中的题目
- 题目类型：**单选题**（A/B/C/D 选项按钮）/ **多选题**（2-4个正确答案）/ **大题**
- 科目选择、知识点标签多选
- **识别题目可编辑**：识别后显示在卡片中，可手动修改
- **本地文字识别**：选择图片后自动触发 Tesseract OCR 识别（离线）
- 数据保存到 Room 数据库

### 复习流程
- `ui/screens/ReviewScreen.kt` + `ReviewViewModel.kt`
- 复习列表：统计卡片（待复习/逾期/已完成）+ 点击列表项直接进入答题
- **跳过今日**：在复习列表中点击跳过图标，不影响后续轮次

#### 答题界面（左右分栏）
- **左侧**：题目显示区（OCR 识别的题目文字）
- **右侧**：可切换面板，通过 `[✏ 草稿纸]` / `[✓ 答题区]` 按钮切换
  - **答题区模式**：选择题显示 ABCD 选项 + 提交按钮；大题显示手写作答区 + 提交/跳过
  - **草稿纸模式**：HandwritingView 草稿纸（大题场景下与答题区各自独立保留笔迹）

#### 顶部工具栏
- **左侧 80%**：[撤销 | 重做] | [笔 | 纸张 | 清空] | [缩放比例]
- **右侧 20%**：根据选中工具显示选项面板
  - 笔工具：颜色按钮（蓝/黑/红）+ 粗细按钮（0.1/0.3/0.5mm）
  - 纸张工具：背景选择（空白/网格/横线）+ 底色选择（黑/白/肉）
- 撤销/重做/清空作用于当前可见面板的 HandwritingView

#### 提交后
- 答题区和草稿纸同时自动清除
- 显示对错标识
- 进入下一题

### 数据库
- Room + KSP 编译器
- `AppDatabase.kt`、`Dao.kt`、`Converters.kt`
- `MistakeRepository.kt` 数据仓库
- `Mistake`、`Review`、`Subject` 实体

**Mistake 实体字段**：
- `id`、`title`（题目标题）、`subject`、`tags`、`questionImagePath`
- `recognizedQuestion`：识别出的题目文本（显示在复习界面）
- `correctAnswer`：正确答案（单选题为选项字母，多选题为逗号分隔的字母）
- `explanation`、`questionType`（SINGLE_CHOICE / MULTI_CHOICE / ESSAY）
- `createdAt`、`wrongCount`、`skipToday`（跳过今日复习标记）

## 项目结构

```
app/src/main/java/com/mistakenotes/
├── MainActivity.kt              # 入口
├── MistakeNotesApp.kt           # Application 类（Hilt）
├── di/
│   └── AppModule.kt             # Hilt 依赖注入模块
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt       # Room 数据库
│   │   ├── Converters.kt        # 类型转换器
│   │   └── Dao.kt              # Dao 接口
│   └── repository/
│       └── MistakeRepository.kt # 数据仓库
├── domain/model/
│   ├── Mistake.kt               # 错题实体
│   ├── Review.kt                # 复习记录实体
│   └── Subject.kt              # 科目实体
└── ui/
    ├── theme/                   # Compose 主题（砚台风格）
    ├── components/
    │   └── HandwritingView.kt    # 手写画布（原生 View，Path 矢量绘制）
    └── screens/
        ├── MainScreen.kt         # 主页面（首页+导航）
        ├── ImportScreen.kt       # 错题录入页面
        ├── ImportViewModel.kt   # 录入 ViewModel
        ├── ReviewScreen.kt       # 复习流程页面（左右分栏 + 工具栏）
        └── ReviewViewModel.kt   # 复习 ViewModel（包含 ToolState 管理）
```

## 构建与运行

- **真机调试**：Android Studio 连接小米 6 Pro，运行 `:app` 模块
- **构建命令**：Android Studio 内置，无需手动 gradle 命令
- **Sync**：File → Sync Project with Gradle Files（或 Ctrl+Shift+O）

## 开发注意事项

- 无商业用途
- 本地优先，数据不上云
- 手写功能需要低延迟，**必须使用原生 View**（不要用 Compose Canvas）
- UI 风格必须遵循砚台设计（深色主题 + 金色强调）
- 错题库支持用户自建库，UI 需要适配此功能
- 数据库使用 Room + KSP，需配置 KSP 编译器
- 草稿纸工具栏：触控笔落下时锁定缩放/移动，抬起时解锁