# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目状态

CPA 错题笔记应用已完成主体功能开发。包含首页统计、错题录入、复习面板（带手写画布）、数据分析四大核心功能。

## 技术栈

- **Android 原生开发**：Kotlin 2.0.0 + Jetpack Compose
- **最低支持**：Android 8.0（API 26）
- **JDK**：17
- **依赖注入**：Hilt 2.52
- **本地数据库**：Room 2.7.0 + KSP
- **图片加载**：Coil 2.6.0
- **导航**：Navigation Compose 2.8.0
- **Compose BOM**：2025.02.00

## 构建与运行

- **真机调试**：Android Studio 连接设备，运行 `:app` 模块
- **Sync**：File → Sync Project with Gradle Files（或 Ctrl+Shift+O）
- **Gradle**：8.9（位于 gradle/wrapper/gradle-wrapper.properties）

## 目录结构

```
app/src/main/java/com/mistakenotes/
├── MainActivity.kt                    # 应用入口
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt            # Room 数据库
│   │   ├── Dao.kt                    # 数据访问对象
│   │   └── Entities.kt              # 数据库实体
│   └── repository/
│       └── MistakeRepository.kt      # 错题数据仓库
├── domain/model/
│   ├── Subject.kt, Chapter.kt, KnowledgePoint.kt  # 领域模型
│   ├── Mistake.kt                    # 错题实体
│   └── ReviewRecord.kt               # 复习记录
└── ui/
    ├── canvas/
    │   ├── HandwritingCanvas.kt      # 手写画布组件
    │   ├── StrokeRenderer.kt        # 笔触渲染（Catmull-Rom 平滑）
    │   ├── VectorStroke.kt, VectorLayer.kt  # 矢量数据模型
    │   └── UndoRedoManager.kt        # 撤销/重做管理
    ├── navigation/
    │   └── NavGraph.kt               # 导航图
    ├── screens/
    │   ├── HomeScreen.kt, HomeViewModel.kt     # 首页
    │   ├── ImportScreen.kt, ImportViewModel.kt # 录入
    │   ├── ReviewScreen.kt, ReviewViewModel.kt  # 复习
    │   └── AnalysisScreen.kt, AnalysisViewModel.kt  # 分析
    └── theme/
        ├── Color.kt                 # 砚台风格配色（InkStoneBlack, AmberGold 等）
        └── Theme.kt                 # 主题配置
```

## 核心功能

- **首页**：科目筛选、统计数据卡片（待复习/逾期/已掌握/总错题）、快捷入口
- **录入**：图片选择、题型/科目/章节/知识点三级分类、选项编辑
- **复习**：35%/65% 左右分栏、选择题/主观题支持、手写草稿
- **分析**：科目掌握度、章节错题分布、薄弱知识点
- **算法**：基于 Ebbinghaus 的间隔重复复习算法

## 开发说明

- 所有 UI 使用 Compose Material3，主题色为 AmberGold（琥珀金）+ InkStoneBlack（砚石黑）
- 手写画布使用 DrawScope 进行绘制，支持压力感应笔触
- 数据库采用 Room，KSP 编译时生成代码
- ViewModel 通过 Hilt 注入，配合 StateFlow 管理 UI 状态