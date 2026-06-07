# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**交流语言**：除代码外，所有输出和回答使用中文。

## 项目状态

CPA 错题笔记应用 — 核心复习流程（录入→复习→分析）已完成。多图上传、图片裁剪、收藏夹、置顶、复习进度显示、7 个新功能（答案图片全题型、题号弹窗、已掌握按钮、已掌握复习、录入时间、题型筛选、图片自适应预览）已实现。功能稳定，可日常使用。

**RAG 知识库状态（2026-06-07）**：会计 30 章共 **299 知识点**（PDF 三册抽取 + DeepSeek 辅助生成）。PC 端 30 道真题测试准确率 **25/30 = 83%**（详见 `结果.md`）。**5 章召回质量差需重抽**：ch5 投资性房地产、ch14 租赁、ch21 债务重组、ch24 会计政策变更、ch25 资产负债表日后事项。

## 技术栈

- **Android 原生开发**：Kotlin 2.0.0 + Jetpack Compose
- **最低支持**：Android 8.0（API 26）
- **JDK**：17
- **依赖注入**：Hilt 2.52
- **本地数据库**：Room 2.7.0 + KSP
- **图片加载**：Coil 2.6.0
- **导航**：Navigation Compose 2.8.0
- **Compose BOM**：2025.02.00
- **RAG 知识库**（CPA 会计）：Google ML Kit 端侧 OCR（中文）+ Retrofit 2.11.0 + kotlinx-serialization 1.7.3 + DeepSeek API
- **Python 工具**（不入仓，工具链）：pdfplumber + openai + deepseek（gen_kb.py / rag_test.py / batch_rag_test.py）

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
│   ├── rag/
│   │   ├── KnowledgeClassifier.kt     # 分类器接口 + ClassifyResult
│   │   ├── MockKnowledgeClassifier.kt # 无 API Key 时用
│   │   ├── DeepSeekKnowledgeClassifier.kt # OCR + 召回 + DeepSeek 精排
│   │   ├── OcrEngine.kt               # ML Kit 端侧 OCR
│   │   ├── KnowledgeBase.kt           # 内存知识库 + recall()
│   │   ├── KnowledgeBaseLoader.kt     # 从 assets 加载
│   │   ├── DeepSeekApi.kt             # Retrofit + DTOs
│   │   └── ApiKeyProvider.kt          # DataStore 包装
├── domain/model/
│   ├── Subject.kt, Chapter.kt, KnowledgePoint.kt
│   ├── Mistake.kt                    # 错题（含isFavorite/isTop、getQuestionImagePaths/getAnswerImagePaths）
│   └── ReviewRecord.kt               # 复习记录（含correctCount）
├── di/
│   ├── DatabaseModule.kt             # Hilt DI（Room + DAO）
│   └── ClassifierModule.kt            # KnowledgeClassifier Hilt 绑定
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
    │   ├── AnalysisViewModel.kt      # 统计数据计算
    │   └── SettingsScreen.kt          # DeepSeek API Key 设置
    └── theme/
        ├── Color.kt                  # InkStoneBlack/AmberGold/CardDark/TextCream
        └── Theme.kt
```

## 核心功能

- **AI 自动归类（会计）**：录入选图后自动跑 RAG（ML Kit OCR + 关键词召回 + DeepSeek 精排）→ 自动填科目/章节/知识点下拉；用户可手动覆盖；失败时 Snackbar 提示不影响保存。设置页填入 DeepSeek API Key 后启用。**当前仅限 CPA 会计 30 章**。
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

- **RAG 知识库数据保护**：APK assets/json/accounting_knowledge_points.json 是只读资源；用户已录入错题 knowledgePointId 字段**绝不受 RAG 重跑影响**——RAG 只在 `addImageUri` 触发时填充；用户手动修改下拉后 RAG 不覆盖
- **DeepSeek API Key 存储**：DataStore Preferences，仅本机保留，不上传任何服务器
- **ML Kit 端侧 OCR**：首次使用需联网下载 ~5MB 中文模型，下载后完全离线
- **RAG 触发只对第一张图**：多张题图时只对 `imageUris[0]` 触发一次，避免 token 浪费
- **RAG 失败容错**：整链路（OCR → 召回 → LLM）任何异常不抛，返回 `ClassifyResult.failed(reason)`；UI 端识别后 Snackbar + 下拉留空 + 不阻塞保存
- **RAG 与用户优先级**：RAG 回来时若 `chapterId != null`（用户已手动选），则丢弃 RAG 结果——用户优先
- **RAG 知识库当前 299 知识点 / 30 章**（2026-06-07 commit `f19a32b`）；中册按物理页码硬编码切分（用 `extract_middle.py`），其它两册用 `extract_pdf.py` 按"第N章"正则
- **重抽章节时只需重跑 `gen_kb.py`**：自动跳过已生成的 28 章，只对 5 章重跑（断点续跑）。成本约 30K-50K tokens
- **跨章节题目召回弱**：高频通用词（"现值/初始/应付账款/账面价值"）分散到多章关键词库，导致 ch14/ch21/ch24/ch25 召回错。修复：改 `KnowledgeBase.recall()` 加 IDF 权重 + bigram 匹配（待做）
- **PC 端 RAG 测试不调 ML Kit OCR**（Android 端侧能力，PC 不可用）。用户须自己打字发题目或装 EasyOCR 跑识别
- **KnowledgeBase 由 KnowledgeBaseLoader 通过 Hilt 单例加载**：`data/rag/KnowledgeBase` 类本身无 `@Inject constructor()`，必须通过 `ClassifierModule.provideKnowledgeBase` 注入
- 图片存储在 `context.filesDir/question_images/`，不是原始 content:// URI
- 多图路径用 `||` 分隔，通过 `Mistake.getQuestionImagePaths()` / `getAnswerImagePaths()` 解析
- 录入时裁剪输出到 cacheDir，保存时 `copyImageToLocal` 复制到永久存储
- 初始录入 `nextReviewDate = entryDate + 5天`——用用户选的错题日期起算，不是 now；回填 6/1 录入的题目首次复习落在 6/6，不是 today+5
- **编辑模式 createdAt**：构造 `Mistake` 时 `createdAt` 直接用 `entryDateMs`（`loadMistakeForEditing` 已预填为原始 createdAt），不要用 `existingMistake?.createdAt` 兜底，否则 DatePicker 改动丢失
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
- **跳过 vs 初始录入**：两者都产生 SKIP 记录，区别在 `nextReviewDate`——初始录入 = entryDate+5d，用户跳过 = 明天00:00。HomeViewModel 用 `nextReviewDate == tomorrowStart` + `reviewDate in today` + `result == SKIP` 三条件判用户主动跳过（`isSkippedToday`），满足时把 `skippedAt` 置为 reviewDate。**UI 渲染"已跳过" badge 必须用 `info.skippedAt > 0`，不能用 `lastResult == SKIP`**——录入/编辑也会写 SKIP 记录但 `nextReviewDate` 是 entryDate+5d，会被误判
- **复习历史累积**：`ReviewViewModel.updateReviewRecord` 新记录必须 `id = 0`（Room 自增），否则 `OnConflictStrategy.REPLACE` 会覆盖旧记录导致只保留最新一条
- **裁剪拖动边界**：四角/四边/中心拖动的约束边界是 `imageLeft/Right/Top/Bottom`（图片实际显示区域），不是 `0f`/`containerSize`
- **复习页图片缩放**：用 `ContentScale.Fit` 完整显示，`FillWidth` 会导致竖长图被截断
- **数据保护（强制）**：所有新功能必须**复用现有字段**，不得修改数据库 schema；不得引入 `fallbackToDestructiveMigration()` 触发的变更；如确需新增字段，必须写 Migration 6→N（明确迁移步骤）并保持 `fallbackToDestructiveMigration()` 不生效。新功能 PR 必须在 Android 真机覆盖安装后验证数据不丢失。
- **答案图片全题型**：选择题复习页的"查看答案"按钮对所有题型显示（不再仅主观题），答案图片每题独立可见状态
- **题型筛选独立**：主页今日和逾期各自独立的题型 Chip（`todayQuestionTypes` / `overdueQuestionTypes`），互不影响
- **逾期列表分组**：逾期按天数降序分组，点击展开后按单选→多选→主观二级分组，方块网格显示题号
- **左右滑动切题**：复习页用 `HorizontalPager` 包裹内容，左右滑动切换上/下一题，与底栏"下一题"按钮和选题弹窗协同
- **录入时间语义**：`createdAt` = 用户选择的录入日期，`nextReviewDate` = entryDate + 5 天（从错题日期起算 5 天后首次复习），`reviewDate` = 当前保存时间
- **已掌握按钮**：复习页顶栏金色 ✓✓ 始终可见（不依赖提交状态），点击弹出确认对话框
- **选题弹窗**：顶栏 📋 按钮 → `ModalBottomSheet`，按题型分组显示方块网格，方块 100dp，未选中背景 `InkStoneBlack` 与弹窗 `CardDark` 区分
- **RescheduleDialog**：已掌握列表 🕐 按钮 → `Slider` 滑动选择 1-10 天
- **HorizontalPager page lambda 的 per-page state**：必须以 lambda 接收的 `page` 参数作 key（如 `mutableStateMapOf<Int, Boolean>()[page]`），不能用 ViewModel 的全局 `currentIndexValue`——后者在 swipe settle 后才更新，期间是滞后旧值；且 HorizontalPager 会预渲染相邻页，错误 key 会让相邻页共享 / 互相覆盖 state
- **逾期复习按天分组**：点逾期题块时 `ReviewSession.queue = 当天 dayGroup.map { it.mistake }`（按 typeOrder：单选→多选→主观题 排序后的 dayMistakes），`startIndex = dayList.indexOf(card)`。**不是** `uiState.overdueCards.map { it.mistake }`（flatList），否则把不同逾期天数混在同一个复习队列
- **可折叠区块的筛选 chips**：filter 控件放在 `AnimatedVisibility` 内的 `Column` 顶部（紧跟 header），**不要**作为独立 `item { ... }` 放在 `AnimatedVisibility` 外——否则折叠时 chips 不消失，且 LazyColumn 多一个永远可见的行

## 待开发功能

| 优先级 | 功能 | 说明 |
|--------|------|------|
| **高** | **RAG 重抽 5 章知识库** | 30 道题测试发现 5 章召回质量差：**ch5 投资性房地产 / ch14 租赁 / ch21 债务重组 / ch24 会计政策变更 / ch25 资产负债表日后事项**。需用 `gen_kb.py` 重抽这几章并人工 review |
| **高** | RAG 扩展到其他科目 | 当前仅会计 30 章；后续扩展到审计/财管/税法/经济法/战略 |
| **高** | 召回算法优化 | 加 IDF 权重 + bigram 匹配，解决"现值/初始/应付账款"等高频词分散问题。当前关键词召回在跨章节场景召回率偏低 |
| 高 | 知识点评 UI 增删改 | knowledge_points 表已支持；RAG 自动 upsert 是主要入口 |
| 中 | 搜索题目 | 关键词搜索功能 |
| 中 | 解析字段 | Mistake.explanation 从未使用 |
| v2 | OCR 识别 | Tesseract 拍照自动识别文字 |
| v2 | AI 评分 | DeepSeek API 主观题智能评分 |
| v2 | AI 得分点 | AI 自动拆解参考答案得分点 |
| 体验 | 通知提醒 | 每日待复习推送 |
| 体验 | 数据备份 | 错题数据导出 |

## RAG PC 端测试工具（不入仓）

`tools/` 目录下有 4 个 Python 工具（仅 `*.py` 入仓，中间产物 `tools/.tmp/` 排除）：

| 脚本 | 用途 |
|------|------|
| `extract_pdf.py` | 用 `pdfplumber` 从单册 PDF 按"第N章"正则抽 30 章文本 |
| `extract_middle.py` | 中册按硬编码物理页码精准切分（解决"第N章"页眉重复误切）|
| `gen_kb.py` | 调 DeepSeek 把每章文本转 5-15 个知识点 JSON。**支持断点续跑**（已生成的章节跳过）+ 3 次重试 |
| `merge_kb.py` | 合并 30 个 JSON 为单个 `accounting_knowledge_points.json` |
| `rag_test.py` | PC 端 RAG 单题测试（跳过 OCR，直接输入题目文字 → 关键词召回 + DeepSeek 精排）|
| `batch_rag_test.py` | 批量并发跑多道题（max 10 workers），输出 JSON |
| `make_results_md.py` | 把 batch 结果转成可读 `结果.md` 表格 |

**用法**：
```bash
# 单题测试
DEEPSEEK_KEY=sk-xxx python tools/rag_test.py "题目文字"

# 批量测试
DEEPSEEK_KEY=sk-xxx python tools/batch_rag_test.py questions.json results.json
python tools/make_results_md.py   # 生成 结果.md
```

**注意**：测试时设 `unset HISTFILE` 防止 Key 入 shell history；用完 **rotate** Key。
