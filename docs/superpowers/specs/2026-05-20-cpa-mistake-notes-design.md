# CPA 错题笔记应用设计文档

## 1. 项目概述

**项目名称**：CPA 错题笔记
**项目类型**：Android 原生应用（Kotlin + Jetpack Compose）
**核心功能**：专为 CPA 考生设计的错题笔记管理 + 手写复习工具
**目标用户**：CPA（注册会计师）考生

## 2. 技术栈

| 类别 | 技术 |
|------|------|
| 平台 | Android 原生（Kotlin + Jetpack Compose） |
| 最低支持 | Android 8.0（API 26） |
| JDK | 11 |
| 手写实现 | Android 原生 View（低延迟） |
| 笔画算法 | Catmull-Rom 样条 + 压力感应 |
| 依赖注入 | Hilt |
| 本地数据库 | Room + KSP |
| 图片加载 | Coil |
| 导航 | Navigation Compose |
| AI 评分 | DeepSeek API |
| OCR | Tesseract OCR（中文语言包） |

## 3. UI 设计风格

### 砚台风格

| 元素 | 值 |
|------|-----|
| 背景色 | `#1A1A1A` |
| 卡片色 | `#242424` |
| 文字色 | `#E8E4DC` |
| 强调色 | `#D4A574`（金/琥珀色）|
| 成功色 | `#6ABF6A` |
| 错误色 | `#D44040` |

## 4. 功能模块

### 4.1 首页（HomeScreen）

**布局**：
- 顶部：应用标题 + 切换科目按钮
- 中部：统计卡片区
  - 待复习数量
  - 逾期数量
  - 已掌握数量
  - 总错题数
- 下部：快速入口
  - 拍照录入
  - 搜索题目
  - 错题分析

**统计卡片**：点击进入对应筛选的题目列表

### 4.2 录入流程（ImportScreen）

**流程**：
1. 拍照或从相册选择图片
2. 系统自动 OCR 识别（离线 Tesseract）
3. 用户确认/编辑识别结果
4. 选择科目 → 章节 → 知识点（三级分类）
5. 选择题目类型：单选题 / 多选题 / 主观题
6. 输入/确认标准答案和得分点（主观题）
   - AI 自动生成得分点，用户确认
   - 可手动调整
7. 保存

**题目类型处理**：
- 单选题：存储选项和正确答案
- 多选题：存储选项和正确答案（多个）
- 主观题：存储题目、参考答案、得分点

### 4.3 复习界面（ReviewScreen）

**布局**：左右分栏（横向平板）

```
┌─────────────────┬──────────────────────────────┐
│                 │  [草稿纸] [答题区]  ←切换按钮   │
│   题目区域       │                              │
│   (30-40%)      │      手写答题区域             │
│                 │      (60-70%)                │
│                 │                              │
│  [全屏按钮]     │      [提交]                  │
└─────────────────┴──────────────────────────────┘
```

**模式切换**：
- 默认：左右分栏
- 全屏：题目以弹出层显示，点击关闭回到分栏

**答题区**：
- 选择题：显示 ABCD 选项按钮，点击提交
- 主观题：显示手写答题区 + 提交按钮

**顶部工具栏**：
- 左侧 80%：[撤销 | 重做] | [笔 | 橡皮擦 | 清空] | [缩放比例]
- 右侧 20%：根据工具显示选项面板
  - 笔工具：颜色（蓝/黑/红）+ 粗细（0.1/0.3/0.5mm）
  - 橡皮擦：像素级擦除

**提交逻辑**：
- 选择题：自动对比正确答案，标记对错
- 主观题：
  1. 将用户作答图片 + 参考答案发送给 DeepSeek API
  2. AI 对照得分点评分
  3. 显示得分点对比
  4. 用户确认最终得分

**草稿纸**：
- 独立于答题区存在
- 提交前保留
- 提交后自动清除（也可手动删除）

### 4.4 错题分析（AnalysisScreen）

**统计维度**：
- 科目级统计：正确率、错题数、掌握度
- 章节级统计：各章节错题分布
- 知识点级统计：高频错题知识点

**重点标记**：
- 反复做错的章节/知识点高亮显示
- 标记"重点复习"标签

**可视化**：
- 饼图/柱状图展示正确率
- 列表展示错题详情

### 4.5 题库管理

**分类结构**：科目 → 章节 → 知识点（三级）

**预设数据**：
- CPA 六科预设大纲知识点
- 用户可补充自定义知识点

**题目操作**：
- 收藏/取消收藏
- 置顶/取消置顶
- 删除
- 编辑

## 5. 手写模块设计

### 5.1 核心数据结构

**StrokePoint**：
```kotlin
data class StrokePoint(
    val x: Float,           // 位置 X
    val y: Float,           // 位置 Y
    val pressure: Float,    // 压力值 0.0-1.0
    val timestamp: Long = 0 // 时间戳
)
```

**VectorStroke**：
```kotlin
data class VectorStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<StrokePoint>,
    val color: Int,         // Color 颜色
    val baseThickness: Float // 基础粗细
)
```

**VectorLayer**：
```kotlin
class VectorLayer {
    val strokes: MutableList<VectorStroke> = mutableStateListOf()
    var opacity: Float = 1.0f
    var isVisible: Boolean = true
    var isLocked: Boolean = false
}
```

### 5.2 渲染算法

**Catmull-Rom 样条平滑**：
- 每两个采样点之间插值 4 个细分点
- 公式：
  ```
  q(t) = 0.5 * [(2*P1) + (-P0+P2)*t + (2*P0-5*P1+4*P2-P3)*t² + (-P0+3*P1-3*P2+P3)*t³]
  ```
- 位置和压力同时插值
- 压力钳制在 [0.1, 1.0]

**笔画多边形构建**：
1. 计算每点处的切线方向
2. 根据压力计算半宽：`halfWidth = baseThickness * pressure / 2`
3. 构建左右边缘点序列
4. 添加圆头端点（椭圆形）

**渲染**：
- 绘制填充多边形（Qt.WindingFill 处理自相交）
- 圆头端点用椭圆绘制

### 5.3 工具类型

| 工具 | 特性 |
|------|------|
| 钢笔（Pen） | 压力感应粗细，颜色+粗细可调 |
| 荧光笔（Highlighter） | 半透明，固定粗细 |
| 橡皮擦（Eraser） | 像素级擦除（Bitmap 级别操作） |

### 5.4 撤销/重做

- 快照制，最多 50 步
- 每笔完成后生成一个快照
- 支持撤销/重做操作

### 5.5 触控笔处理

- 笔落下：锁定缩放/移动
- 笔抬起：解锁
- 支持压力感应（Pressure）

## 6. 数据库设计

### 6.1 实体

**Subject（科目）**：
- id, name, color

**Chapter（章节）**：
- id, subjectId, name, order

**KnowledgePoint（知识点）**：
- id, chapterId, name, isPreset（是否预设）

**Mistake（错题）**：
- id, title, subjectId, chapterId, knowledgePointId
- questionType（ SINGLE_CHOICE / MULTI_CHOICE / ESSAY ）
- questionImagePath, recognizedText
- options（JSON，选题用）
- correctAnswer, explanation
- scoringPoints（主观题得分点，JSON）
- createdAt, isFavorite, isTop

**ReviewRecord（复习记录）**：
- id, mistakeId
- reviewDate, result（CORRECT / WRONG / SKIP）
- score（主观题得分）
- nextReviewDate（下次复习日期）
- correctCount（连续正确次数）

### 6.2 艾宾浩斯记忆

**复习间隔计算**：
- 第 1 次做对：1 天后复习
- 第 2 次做对：3 天后复习
- 第 3 次做对：7 天后复习
- 第 4 次做对：不再复习（已掌握）
- 第 1 次做错：重置为第 1 次（1 天后复习）

## 7. AI 评分设计

### 7.1 DeepSeek API 调用

**请求格式**：
```json
{
  "model": "deepseek-chat",
  "messages": [
    {
      "role": "system",
      "content": "你是一个CPA考试评分专家..."
    },
    {
      "role": "user",
      "content": "题目：xxx\n参考答案：xxx\n考生作答：xxx\n请对照参考答案和考生作答，给出得分点匹配情况..."
    }
  ]
}
```

**响应处理**：
- 解析 AI 返回的得分点匹配结果
- 计算得分
- 返回给用户确认

### 7.2 得分点管理

- 录题时：AI 自动从参考答案为每个得分点生成描述
- 用户可手动编辑/确认
- 格式：`[{point: "xxx", score: 2}, ...]`

## 8. 文件结构

```
app/src/main/java/com/mistakenotes/
├── MainActivity.kt
├── MistakeNotesApp.kt              # Application 类
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── Converters.kt
│   │   └── Dao.kt
│   ├── remote/
│   │   └── DeepSeekApi.kt
│   └── repository/
│       └── MistakeRepository.kt
│
├── domain/model/
│   ├── Mistake.kt
│   ├── ReviewRecord.kt
│   ├── Subject.kt
│   ├── Chapter.kt
│   └── KnowledgePoint.kt
│
├── ui/
│   ├── canvas/
│   │   ├── StrokePoint.kt
│   │   ├── VectorStroke.kt
│   │   ├── StrokeRenderer.kt
│   │   ├── VectorLayer.kt
│   │   ├── CanvasView.kt
│   │   ├── EraserTool.kt
│   │   └── UndoRedoManager.kt
│   │
│   ├── screens/
│   │   ├── HomeScreen.kt
│   │   ├── ImportScreen.kt
│   │   ├── ImportViewModel.kt
│   │   ├── ReviewScreen.kt
│   │   ├── ReviewViewModel.kt
│   │   ├── AnalysisScreen.kt
│   │   └── AnalysisViewModel.kt
│   │
│   └── theme/
│       ├── Color.kt
│       ├── Type.kt
│       └── Theme.kt
│
└── di/
    └── AppModule.kt
```

## 9. 实现优先级

### 第一阶段（核心功能）
1. 数据库设计与实现
2. 手写画布核心（笔画渲染 + 压力感应）
3. 首页 + 题目列表
4. 录入流程

### 第二阶段（复习功能）
5. 左右分栏复习界面
6. 选择题答题逻辑
7. 主观题手写答题
8. AI 评分集成

### 第三阶段（增强功能）
9. 艾宾浩斯复习算法
10. 错题分析统计
11. 收藏/置顶功能
12. 像素级橡皮擦优化

## 10. 依赖版本

```kotlin
// build.gradle.kts
plugins {
    id("com.android.application") version "8.2.2"
    id("org.jetbrains.kotlin.android") version "1.9.22"
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
    id("com.google.dagger.hilt.android") version "2.50"
}

dependencies {
    // Compose BOM
    platform("androidx.compose:compose-bom:2024.06.00")

    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Coil
    implementation("io.coil-kt:coil-compose:2.5.0")

    // ExifInterface
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Tesseract OCR
    implementation("com.rmtheis:tess-two:9.1.0")

    // Retrofit + OkHttp（DeepSeek API）
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
}
```
