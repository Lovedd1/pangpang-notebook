# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

错题笔记应用：支持手写的小米 6 Pro 平板应用，用于学习考试类错题的记录、整理和复习。纯本地使用，备份至百度网盘或夸克网盘。

- 设计规格：`docs/superpowers/specs/2026-05-08-mistake-notes-design.md`
- 前端原型：`docs/prototypes/砚台版-full.html`

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

**AI 接入**

- DeepSeek / Minimax API：图片文字识别、大题答案对比

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
- **Catmull-Rom 样条曲线**：直线笔直，曲线流畅
- **距离采样**：每 2 像素采样一次，避免抖动
- 支持撤销（undo）和清空（clear）

### 错题录入
- `ui/screens/ImportScreen.kt` + `ImportViewModel.kt`
- 支持拍照/相册选择题目图片
- 题目类型选择（选择题/大题）
- 科目选择、知识点标签多选
- 正确答案和解析输入
- 数据保存到 Room 数据库

### 复习流程
- `ui/screens/ReviewScreen.kt` + `ReviewViewModel.kt`
- 复习列表：统计卡片（待复习/逾期/已完成）+ 开始按钮
- 答题界面：题目显示 + 手写答题/选择题 + 提交
- 选择题自动判断对错，大题手动标记
- 进度条显示当前进度
- 复习记录保存到 Room 数据库

### 数据库
- Room + KSP 编译器
- `AppDatabase.kt`、`Dao.kt`、`Converters.kt`
- `MistakeRepository.kt` 数据仓库
- `Mistake`、`Review`、`Subject` 实体

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
    │   └── HandwritingView.kt    # 手写画布（原生 View，双笔模式）
    └── screens/
        ├── MainScreen.kt         # 主页面（首页+导航）
        ├── ImportScreen.kt       # 错题录入页面
        ├── ImportViewModel.kt   # 录入 ViewModel
        ├── ReviewScreen.kt       # 复习流程页面
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