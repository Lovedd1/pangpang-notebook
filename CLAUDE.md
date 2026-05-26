# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目状态

CPA 错题笔记应用 — 核心复习流程（录入→复习→分析）已完成。手写画布已移除，复习改为纯文本交互，主页改为今日待复习+逾期列表。功能稳定，可日常使用。

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
- **Gradle**：8.9

## 目录结构

```
app/src/main/java/com/mistakenotes/
├── MainActivity.kt
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt            # Room 数据库 + 预置CPA六科112章数据
│   │   ├── Dao.kt                    # 数据访问对象
│   │   └── Entities.kt              # 数据库实体
│   └── repository/
│       └── MistakeRepository.kt      # 数据仓库（Entity↔Domain映射）
├── domain/model/
│   ├── Subject.kt, Chapter.kt, KnowledgePoint.kt
│   ├── Mistake.kt                    # 错题（含questionImagePath本地路径）
│   └── ReviewRecord.kt               # 复习记录（含Ebbinghaus间隔）
├── di/
│   └── DatabaseModule.kt             # Hilt DI（Room + DAO）
└── ui/
    ├── navigation/
    │   └── NavGraph.kt               # 4屏导航（Home/Import/Review/Analysis）
    ├── screens/
    │   ├── HomeScreen.kt             # 主页：科目筛选 + 今日待复习/逾期可展开列表
    │   ├── HomeViewModel.kt          # 今日/逾期分离逻辑 + 复习状态追踪
    │   ├── ImportScreen.kt           # 录入：图片+按钮式选项+三级分类
    │   ├── ImportViewModel.kt        # 选项管理 + 图片本地存储 + 答案字母化
    │   ├── ReviewScreen.kt           # 复习：题目图片+单列选项+结果展示
    │   ├── ReviewViewModel.kt        # 复习逻辑 + Ebbinghaus算法 + 队列管理
    │   ├── ReviewSession.kt          # 跨Screen复习队列/状态传递
    │   ├── AnalysisScreen.kt         # 分析：科目掌握度+章节分布+薄弱知识点
    │   └── AnalysisViewModel.kt      # 统计数据计算
    └── theme/
        ├── Color.kt                  # InkStoneBlack/AmberGold/CardDark/TextCream
        └── Theme.kt
```

## 核心功能

- **主页**：科目筛选 Chip、今日待复习列表（已复习/未复习标签）、逾期列表（逾期天数+降序）、总错题/已掌握统计、快捷入口
- **录入**：拍照/选图（自动复制到本地存储）、单选题/多选题/主观题切换、按钮式选项（A~H标签+✓标记正确+×删除）、三级分类（科目→章节→知识点）、选项以`|`分隔存储
- **复习**：单列布局（题目图片+文字+选项按钮）、单选圆形/多选方形、提交后绿色正确/红色错误高亮+结果横幅、"下一题"循环（按主页列表顺序）、已复习卡片直接展示结果、主观题自评（答对/答错/跳过）
- **分析**：科目掌握度（进度条+百分比）、章节错题分布、薄弱知识点排行
- **算法**：Ebbinghaus 间隔重复（1天→3天→7天→掌握）

## 数据模型

| 表 | 关键字段 | 说明 |
|-----|---------|------|
| subjects | id, name, color | CPA六科（预置数据） |
| chapters | id, subjectId, name, order | 112章（预置数据） |
| knowledge_points | id, chapterId, name | 知识点（预置/自定义） |
| mistakes | id, questionType, options, correctAnswer, questionImagePath | 错题主体 |
| review_records | id, mistakeId, result, nextReviewDate, correctCount | 复习历史 |

- `options`：`|`分隔的选项文本（如"长投\|交易性金融资产"）
- `correctAnswer`：答案字母（单选"A"，多选"AB"）
- `questionImagePath`：图片本地绝对路径（录入时从content://复制到filesDir/question_images/）
- 今日判定：`nextReviewDate ∈ [today 00:00, today 23:59]`

## 跨Screen数据传递

`ReviewSession` 单例对象（`ui/screens/ReviewSession.kt`）传递复习队列：
- `queue: List<Mistake>` — 当前复习队列
- `startIndex: Int` — 起始位置
- `isViewingResult: Boolean` — 是否查看已复习结果
- `preReviewedIndices: Set<Int>` / `preReviewedResults: Map<Int, Boolean?>` — 预审核卡片索引与结果

HomeScreen 设置 → ReviewViewModel 读取后 clear → 后续循环用 ViewModel 内部 `reviewedIndices`/`reviewedResults`。

## 注意事项

- 图片存储在 `context.filesDir/question_images/`，不是原始 content:// URI
- ReviewViewModel 用 `.first()` 快照加载队列，不响应数据库变更
- HomeViewModel 用 `.collect()` 响应式更新列表状态
- SKIP 结果的 ReviewRecord 不计入"已复习"
- 数据库用 `fallbackToDestructiveMigration()`，版本2
