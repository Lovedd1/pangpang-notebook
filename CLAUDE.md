# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

错题笔记应用：支持手写的小米 6 Pro 平板应用，用于学习考试类错题的记录、整理和复习。纯本地使用，备份至百度网盘或夸克网盘。

- 设计规格：`docs/superpowers/specs/2026-05-08-mistake-notes-design.md`
- 草稿纸功能设计：`docs/superpowers/specs/2026-05-10-draft-paper-design.md`
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
- 复习流程：题目显示 → 手写答题 → 提交 → AI 对比分析 → 手动标记对错

## 已实现功能

### 手写画布
- `ui/components/HandwritingView.kt` — 原生 Android View，低延迟手写
- **模式切换**：笔写模式（触控笔金色）/ 手写模式（手指浅色），通过按钮切换
- **缩放/拖动**：双指捏合缩放（100%-500%），单指拖动画布（笔写模式）/ 书写（手写模式）
- **纸张底色**：黑色（#242424）/ 白色（#FFFFFF）/ 肉色（#F5E6D3）
- **纸张线型**：空白 / 网格（40dp间隔）/ 横线（60dp间隔）
- **Catmull-Rom 样条曲线**：直线笔直，曲线流畅
- **距离采样**：每 2 像素采样一次，避免抖动
- 支持撤销（undo）和清空（clear）
- **内容裁剪**：超出可见区域的内容自动隐藏，不显示在背景色外

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
- 答题界面：
  - **题目显示**：显示 OCR 识别的题目文字（`recognizedQuestion`）
  - 选择题（A/B/C/D 选项卡，单选/多选统一 UI）
  - 大题：手写区域（与主页 HandwritingView 相同）
  - **草稿纸**：点击"草稿纸"按钮切换到手写画布，用于计算和思考，笔记保留，提交后自动清除
  - 提交后显示对错标识
- 进度条显示当前进度
- 复习记录保存到 Room 数据库

### 草稿纸功能（复习界面）
- `DraftModeContent` composable（位于 ReviewScreen.kt）
- `ui/components/BitmapDraftView.kt` — 基于 Bitmap 的手写视图，支持像素级橡皮擦
- **核心组件**（`ui/components/drawing/`）：
  - `CoordTransformer.kt` — 统一坐标变换器
  - `Stroke.kt` — 笔画数据模型
  - `StrokeRecorder.kt` — 笔画级撤销记录器（内存降低10x）
- **工具栏**：
  - 倍率显示（实时更新）
  - 底色选择：黑色、白色、肉色
  - 线型选择：空白、网格、横线
  - 笔写/手写模式切换
  - 橡皮擦工具（带大小 S/M/L 选择）
  - 清空按钮
- **A4 纸张**：默认 A4 比例（210mm x 297mm）
- **缩放**：双指缩放（笔写模式用触控笔时禁用）
- **缩放范围**：100% - 500%
- **笔类型**：钢笔（圆头）/ 圆珠笔（尖头）
- **笔迹颜色**：金色（#D4A574）

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
    │   ├── HandwritingView.kt    # 手写画布（原生 View，Path 矢量绘制）
    │   ├── BitmapDraftView.kt    # 草稿纸画布（Bitmap 绘制，像素级橡皮擦）
    │   └── drawing/              # 绘制相关组件
    │       ├── CoordTransformer.kt  # 坐标变换器
    │       ├── Stroke.kt             # 笔画数据模型
    │       └── StrokeRecorder.kt     # 笔画记录器
    └── screens/
        ├── MainScreen.kt         # 主页面（首页+导航）
        ├── ImportScreen.kt       # 错题录入页面
        ├── ImportViewModel.kt   # 录入 ViewModel
        ├── ReviewScreen.kt       # 复习流程页面（含 DraftModeContent 草稿纸）
        └── ReviewViewModel.kt   # 复习 ViewModel
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