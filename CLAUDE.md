# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**交流语言**：除代码外，所有输出和回答使用中文。

## 项目状态

CPA 错题笔记应用 — 核心复习流程（录入→复习→分析）已完成。多图上传、图片裁剪、收藏夹、置顶、复习进度显示、7 个新功能（答案图片全题型、题号弹窗、已掌握按钮、已掌握复习、录入时间、题型筛选、图片自适应预览）已实现。功能稳定，可日常使用。

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
- **GitHub**：https://github.com/Lovedd1/pangpang-notebook（默认 push 目标）

## 目录结构

```
app/src/main/java/com/mistakenotes/
├── MainActivity.kt
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt            # Room 数据库 + 预置CPA六科112章数据
│   │   ├── Dao.kt                    # 数据访问对象（含updateFavorite/updateTop）
│   │   └── Entities.kt              # 数据库实体
│   └── repository/
│       └── MistakeRepository.kt      # 数据仓库（Entity↔Domain映射，含setFavorite/setTop）
├── domain/model/
│   ├── Subject.kt, Chapter.kt, KnowledgePoint.kt
│   ├── Mistake.kt                    # 错题（含isFavorite/isTop、getQuestionImagePaths/getAnswerImagePaths）
│   └── ReviewRecord.kt               # 复习记录（含correctCount）
├── di/
│   └── DatabaseModule.kt             # Hilt DI（Room + DAO）
└── ui/
    ├── navigation/
    │   └── NavGraph.kt               # 5屏导航（Home/Import/Review/Analysis/Browse?favorites=&mastered=）
    ├── components/
    │   ├── QuestionTypeFilter.kt     # 3 chip 多选题型筛选（单选/多选/主观题）
    │   ├── ZoomableImage.kt          # 双指缩放 + 双击放大 + 拖动
    │   ├── ImagePreviewDialog.kt     # 全屏黑色背景图片预览
    │   ├── AdaptiveImage.kt          # 按原图比例自适应大小 + 点击预览
    │   ├── EntryDateRow.kt           # 录入时间行 + DatePickerDialog
    │   ├── JumpToQuestionDialog.kt   # 题号网格弹窗（AlertDialog）
    │   └── RescheduleDialog.kt       # 1~10 天滑动选择器
    ├── screens/
    │   ├── HomeScreen.kt             # 主页：科目筛选 + 今日待复习/逾期可展开列表 + 复习进度
    │   ├── HomeViewModel.kt          # 今日/逾期分离逻辑 + cardSubjectIds + correctCount
    │   ├── ImportScreen.kt           # 录入：多图上传+裁剪 + 按钮式选项 + 三级分类
    │   ├── ImportViewModel.kt        # 多图管理(addImageUri/removeImageUri) + ||分隔存储
    │   ├── CropScreen.kt             # Compose原生裁剪：拖动裁剪框+四角调整大小
    │   ├── ReviewScreen.kt           # 复习：HorizontalPager多图翻页 + 题目栏高度限制
    │   ├── ReviewViewModel.kt        # 复习逻辑 + 5天间隔算法 + 队列管理
    │   ├── ReviewSession.kt          # 跨Screen复习队列/状态传递
    │   ├── BrowseScreen.kt           # 错题浏览/收藏夹：筛选 + 复习进度 + 收藏★/置顶↑
    │   ├── BrowseViewModel.kt        # 排序 + 收藏模式切换 + ebbinghausCount
    │   ├── AnalysisScreen.kt         # 分析：科目掌握度+章节分布(按科目分组)+薄弱知识点
    │   └── AnalysisViewModel.kt      # 统计数据计算
    └── theme/
        ├── Color.kt                  # InkStoneBlack/AmberGold/CardDark/TextCream
        └── Theme.kt
```

## 核心功能

- **主页**：科目筛选 Chip（显示有今日卡片+逾期卡片的科目，彩虹配色）、今日/逾期独立题型筛选 Chip（单选/多选/主观题）、今日待复习列表（已复习/未复习标签+科目/章节信息+复习进度）、逾期列表（按逾期天数分组展开→按题型二级分组→方块网格题号）、总错题/已掌握统计、快捷入口
- **录入**：多图上传（水平滚动列表+添加/删除）、Compose原生裁剪（拖动定位+四角缩放）、拍照/选图（自动复制到本地）、标题（不填自动生成 `YYYY-MM-DD-NN`）、单选题/多选题/主观题切换、按钮式选项（A~H标签+✓标记正确+×删除）、三级分类（科目→章节→知识点）、**所有题型**答案/解析图片上传、**录入时间选择**（DatePickerDialog）、选项以`|`分隔存储、编辑模式+删除功能
- **复习**：左右滑动HorizontalPager切换题目、多图翻页（题目/答案独立+页码指示器）、图片自适应比例显示不裁剪 + 点击全屏预览（双指缩放+双击放大）、单列布局、单选圆形/多选方形、提交后绿色正确/红色错误高亮+结果横幅、主观题自评（答对/答错/跳过）、**所有题型**查看答案/解析按钮（每题独立状态）、顶栏收藏★+**已掌握✓✓**按钮、顶栏📋选题底部弹窗（按题型分组+方块网格）、跳过安排到明天优先复习
- **错题浏览**：科目/章节双层筛选、**题型筛选 Chip**、列表按置顶>错误次数>录入时间排序、每条显示复习历史图标+正确/错误计数+复习进度+收藏★+置顶↑+编辑、点击进入单题复习
- **已掌握**：复用错题浏览，路由参数 `mastered=true`，筛选 `correctCount≥3`，保留编辑/置顶/收藏按钮、**🕐重新安排复习**（1-10天滑动选择器），答错后归零回到复习循环
- **收藏夹**：复用错题浏览页面，路由参数 `favorites=true`，数据源 `isFavorite=1`，取消收藏自动移出
- **已掌握**：复用错题浏览页面，路由参数 `mastered=true`，筛选 `correctCount≥3`，隐藏复习历史和进度，仍有编辑/置顶/收藏按钮，答错后归零回到复习循环
- **置顶**：错题浏览和收藏夹均支持，置顶项排最前+橙色图标+置顶标签
- **分析**：科目掌握度（进度条+百分比+科目配色）、章节错题分布（按科目分组）、薄弱知识点排行
- **算法**：5天间隔重复（错一次归零→5天→5天→5天→掌握，共3次正确）；跳过保留 correctCount、安排到明天优先；已掌握题目不加入今日/逾期列表
- **复习进度**：列表项显示"第N次复习·还差X次掌握"或"已掌握"

## 数据模型

| 表 | 关键字段 | 说明 |
|-----|---------|------|
| subjects | id, name, color | CPA六科（彩虹配色预置数据） |
| chapters | id, subjectId, name, order | 112章（预置数据） |
| knowledge_points | id, chapterId, name | 知识点（预置/自定义） |
| mistakes | id, title, questionType, options, correctAnswer, questionImagePath, referenceAnswer, isFavorite, isTop | 错题主体 |
| review_records | id, mistakeId, result, nextReviewDate, correctCount | 复习历史 |

- `title`：用户填写或自动生成（`YYYY-MM-DD-NN`格式）
- `options`：`|`分隔的选项文本
- `correctAnswer`：答案字母（单选"A"，多选"AB"）
- `questionImagePath`：`||`分隔的多图路径（如 `/data/img1.jpg||/data/img2.jpg`）
- `referenceAnswer`：`||`分隔的主观题答案多图路径
- `isFavorite`：是否收藏（Boolean，默认false）
- `isTop`：是否置顶（Boolean，默认false）
- 今日判定：`nextReviewDate ∈ [today 00:00, today 23:59]`
- 掌握判定：`correctCount ≥ 3`

## 跨Screen数据传递

`ReviewSession` 单例对象（`ui/screens/ReviewSession.kt`）传递复习队列：
- `queue: List<Mistake>` — 当前复习队列
- `startIndex: Int` — 起始位置
- `isViewingResult: Boolean` — 是否查看已复习结果
- `preReviewedIndices: Set<Int>` / `preReviewedResults: Map<Int, Boolean?>` — 预审核卡片索引与结果

HomeScreen 设置 → ReviewViewModel 读取后 clear → 后续循环用 ViewModel 内部 `reviewedIndices`/`reviewedResults`。

## 自定义图标

`res/drawable/` 下的自定义矢量图标：

| 文件 | 用途 | 颜色 |
|------|------|------|
| `ic_launcher_foreground.xml` | App图标（粉色笔记本） | 粉色/蓝/黄/白 |
| `ic_fav_on.xml` | 已收藏（金色徽章+深色星） | AmberGold #D4A574 |
| `ic_fav_off.xml` | 未收藏（奶油描边徽章+空心星） | TextCream #E8E4DC |
| `ic_pin_on.xml` | 已置顶 | 橙色 #FF6700 |
| `ic_pin_off.xml` | 未置顶 | 深灰 #2c2c2c |
| `ic_edit.xml` | 编辑按钮（铅笔+纸张） | AmberGold + TextCream |
| `ic_correct.xml` | 复习正确标记（绿色实心圆+白色对勾） | 绿色 #11AA66 |
| `ic_wrong.xml` | 复习错误标记 | 红色 #F5222D |
| `ic_flame.xml` | 收藏夹入口图标（火焰） | 红色 #fc5531 |
| `ic_globe.xml` | 拍照录入入口图标（浏览器） | 多色原色 |

## 注意事项

- 图片存储在 `context.filesDir/question_images/`，不是原始 content:// URI
- 多图路径用 `||` 分隔，通过 `Mistake.getQuestionImagePaths()` / `getAnswerImagePaths()` 解析
- 录入时裁剪输出到 cacheDir，保存时 `copyImageToLocal` 复制到永久存储
- 初始录入 `nextReviewDate = now + 5天`，不是立刻/今天
- ReviewViewModel 用 `.first()` 快照加载队列，不响应数据库变更
- HomeViewModel 用 `.collect()` 响应式更新列表状态
- SKIP 结果的 ReviewRecord 不计入"已复习"
- 无 gradlew：通过 Android Studio 构建，命令行编译不可用
- 数据库用 `fallbackToDestructiveMigration()`，版本6
- **数据保护**：日常 Run（覆盖安装）不会删除数据；修改数据库 Schema 时必须写 Migration（从当前版本迁到新版本），否则数据会被清空
- 科目配色在 `onOpen` 中自动更新，旧数据无需迁移
- 收藏/置顶通过 DAO 的 `updateFavorite`/`updateTop` 直接更新单字段，无需加载完整 Entity
- BrowseScreen 通过导航参数 `favorites`（BoolType）复用为收藏夹视图
- BrowseItem.ebbinghausCount 来自最新 ReviewRecord 的 correctCount（Ebbinghaus连续计数），而非总正确次数
- 主页科目 Chip 筛选范围 = 今日卡片 ∪ 逾期卡片的科目（`cardSubjectIds`）
- **数据库迁移**：修改章节预置数据时必须同时写 Migration。策略：`PRAGMA foreign_keys = OFF` → DELETE 旧章节 → INSERT 新章节 → CASE 映射 mistakes.chapterId/knowledge_points.chapterId → `PRAGMA foreign_keys = ON`
- **裁剪坐标映射**：确认裁剪时必须考虑 `ContentScale.Fit` 的图片偏移量（`imageLeft/Top/Right/Bottom`），用统一缩放比映射到原图像素，不能用分开的 scaleX/scaleY
- **图片文件名唯一性**：`copyImageToLocal` 用 `System.nanoTime()` 而非 `Date()`（毫秒级时间戳在紧密循环中会冲突）
- **跳过 vs 初始录入**：两者都产生 SKIP 记录，区别在 `nextReviewDate`——初始录入 = now+5天，用户跳过 = 明天00:00。HomeViewModel 用 `nextReviewDate == tomorrowStart` 区分
- **复习历史累积**：`ReviewViewModel.updateReviewRecord` 新记录必须 `id = 0`（Room 自增），否则 `OnConflictStrategy.REPLACE` 会覆盖旧记录导致只保留最新一条
- **裁剪拖动边界**：四角/四边/中心拖动的约束边界是 `imageLeft/Right/Top/Bottom`（图片实际显示区域），不是 `0f`/`containerSize`
- **复习页图片缩放**：用 `ContentScale.Fit` 完整显示，`FillWidth` 会导致竖长图被截断
- **数据保护（强制）**：所有新功能必须**复用现有字段**，不得修改数据库 schema；不得引入 `fallbackToDestructiveMigration()` 触发的变更；如确需新增字段，必须写 Migration 6→N（明确迁移步骤）并保持 `fallbackToDestructiveMigration()` 不生效。新功能 PR 必须在 Android 真机覆盖安装后验证数据不丢失。
- **答案图片全题型**：选择题复习页的"查看答案"按钮对所有题型显示（不再仅主观题），答案图片每题独立可见状态
- **题型筛选独立**：主页今日和逾期各自独立的题型 Chip（`todayQuestionTypes` / `overdueQuestionTypes`），互不影响
- **逾期列表分组**：逾期按天数降序分组，点击展开后按单选→多选→主观二级分组，方块网格显示题号
- **左右滑动切题**：复习页用 `HorizontalPager` 包裹内容，左右滑动切换上/下一题，与底栏"下一题"按钮和选题弹窗协同
- **录入时间语义**：`createdAt` = 用户选择的录入日期，`nextReviewDate` = 今天 + 5 天（第一次复习从录入 App 的日期起算），`reviewDate` = 当前保存时间
- **已掌握按钮**：复习页顶栏金色 ✓✓ 始终可见（不依赖提交状态），点击弹出确认对话框
- **选题弹窗**：顶栏 📋 按钮 → `ModalBottomSheet`，按题型分组显示方块网格，方块 100dp，未选中背景 `InkStoneBlack` 与弹窗 `CardDark` 区分
- **RescheduleDialog**：已掌握列表 🕐 按钮 → `Slider` 滑动选择 1-10 天

## 待开发功能

| 优先级 | 功能 | 说明 |
|--------|------|------|
| **高** | 知识点管理 | knowledge_points 表空，需 UI 增删改 |
| 中 | 搜索题目 | 关键词搜索功能 |
| 中 | 解析字段 | Mistake.explanation 从未使用 |
| v2 | OCR 识别 | Tesseract 拍照自动识别文字 |
| v2 | AI 评分 | DeepSeek API 主观题智能评分 |
| v2 | AI 得分点 | AI 自动拆解参考答案得分点 |
| 体验 | 通知提醒 | 每日待复习推送 |
| 体验 | 数据备份 | 错题数据导出 |
