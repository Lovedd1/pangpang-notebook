# CPA 错题 RAG 知识库 — 会计科目自动归类 设计文档

**日期**：2026-06-06
**作者**：Claude (brainstorming + design)
**关联 CLAUDE.md**：必须遵守"数据保护"原则

---

## 概述

为 CPA 错题笔记应用增加 **"题目图片自动归类到章节 + 知识点"** 能力。范围限定为 **CPA 会计（subjectId=1, 30 章）**。流程：题目图片 → ML Kit 本地 OCR 提取文字 → APK 内置关键词知识库召回 top-5 候选 → DeepSeek 精排 → 自动填充 ImportScreen 的章节与知识点下拉。用户可手动覆盖。

| 项 | 决策 |
|---|------|
| 范围 | CPA **会计**（30 章）|
| 输入 | 题目**图片**（添加后自动跑）|
| 部署 | C 模式（APK 内置知识库 + 云 LLM DeepSeek）|
| 知识库来源 | 用户提供教材/PDF/讲义 + Python 脚本辅助生成 |
| LLM | DeepSeek（API Key 由用户在设置页填入 DataStore）|
| 触发 | **添加图片后自动跑**（无感）|
| OCR | **Google ML Kit 端侧文字识别**（中文，免费，离线，不需要 VPN）|

---

## 设计原则

### 数据保护（强制，与 CLAUDE.md 一致）

- **零 schema 变更**：`mistakes.knowledgePointId` 字段已存在（Entities.kt 第 83 行），本次只填这个字段
- 数据库版本号保持 6
- `fallbackToDestructiveMigration()` 不会被触发
- 现有 `MIGRATION_2_3` ~ `MIGRATION_5_6` 全部继续生效
- 用户当前所有数据（错题、复习记录、收藏、置顶）100% 保留
- `knowledge_points` 表**不预置数据**——由 RAG 选过一次后，逐步填入该表

### 架构原则

- **接口优先**：`KnowledgeClassifier` 接口隔离实现，UI/ViewModel 不知道也不关心背后是 Mock、DeepSeek-VL 还是 ML Kit+DeepSeek
- **可降级**：整链路（OCR → 召回 → LLM）任何一步失败，自动 fallback 到 Mock 或手动模式
- **用户优先**：RAG 永远不覆盖用户已手动修改的值
- **可解释**：`ClassifyResult.reasoning` 字段记录"为什么这么分类"，UI 可展开

### 依赖原则

- 最小化新增依赖：仅 `com.google.mlkit:text-recognition-chinese`（~5MB）、`com.squareup.retrofit2:retrofit`（已习惯的网络栈）、`com.squareup.moshi:moshi`（JSON 解析）
- 不引入 Tesseract（用户用 Google ML Kit 替换）
- 不引入端侧 LLM（云 LLM 够用）

---

## §1 架构总览

### 分层

```
┌─────────────────────────────────────────┐
│  UI 层（ImportScreen）                   │
│  - 添加图片 → 调用 ViewModel            │
│  - 等待结果 → 自动填 3 个下拉           │
│  - 加载中显示 Spinner / 失败时 Snackbar │
└────────────────┬────────────────────────┘
                 │
┌────────────────▼────────────────────────┐
│  ViewModel 层（ImportViewModel）         │
│  - collect Flow 展示 RAG 状态            │
│  - 失败回退：3 个下拉留空 + Toast        │
│  - 用户可手动覆盖                       │
└────────────────┬────────────────────────┘
                 │
┌────────────────▼────────────────────────┐
│  领域服务层（KnowledgeClassifier 接口）   │
│  - classify(uri): ClassifyResult        │
│  - 内部状态：IDLE / LOADING / DONE/ERROR │
└────────────────┬────────────────────────┘
                 │
       ┌─────────┴──────────┐
       │                    │
┌──────▼─────────┐  ┌──────▼────────────────┐
│ MockKnowledge- │  │ DeepSeekKnowledge-    │
│ Classifier     │  │ Classifier            │
│ (无 Key 时用)  │  │ (有 Key 时用)         │
│ 固定返回       │  │ ML Kit OCR +          │
│                │  │   关键词召回 +         │
│                │  │   DeepSeek 精排        │
└────────────────┘  └────────────────────────┘
                            │
                    ┌───────▼────────┐
                    │  DeepSeek API  │
                    │  (云端)        │
                    └────────────────┘
```

### 关键边界

- **UI 不知道**是哪个 Classifier 在工作 → Mock 开发和换实现零成本
- **Classifier 不知道**是 ImportScreen 还是 ReviewScreen → 将来"批量重分类"可复用
- **知识库**作为只读资源（assets 里的 JSON）注入到 Classifier，不在 UI/ViewModel 层出现

---

## §2 知识库结构 + 数据流

### 知识库数据结构

每个会计知识点一条记录（JSON Schema）：

```json
{
  "id": 1,
  "chapterId": 2,
  "name": "存货的初始计量",
  "description": "企业取得存货时，应当按照成本进行初始计量。存货成本包括采购成本、加工成本和其他成本...",
  "keywords": ["存货", "初始计量", "采购成本", "加工成本", "入库", "买价", "运杂费"],
  "formulas": ["存货成本 = 买价 + 运杂费 + 保险费 + 装卸费 - 商业折扣"],
  "commonPitfalls": ["现金折扣不冲减存货成本", "增值税进项税额单独核算不计入成本"]
}
```

| 字段 | 类型 | 用途 | 备注 |
|------|------|------|------|
| `id` | Long | 唯一标识 | 运行时映射到 Room `knowledge_points.id` |
| `chapterId` | Long | 关联章节 | 1-30（会计科目的章 id）|
| `name` | String | 知识点名 | 用户最终看到的标签（≤15 字）|
| `description` | String | 长描述（200-500 字）| 精排 prompt 上下文用 |
| `keywords` | List<String> | 关键词（5-15 个）| 召回主用 |
| `formulas` | List<String> | 公式数组 | 召回加权 |
| `commonPitfalls` | List<String> | 易错点 | 召回加权 |

**存储方式**：**assets/json/accounting_knowledge_points.json**（APK 内置，只读）。运行时加载到内存做关键词索引。Room `knowledge_points` 表**不预置**——由用户在 ImportScreen 选过一次后逐步填入。

### 关键词召回算法

```kotlin
// 1. 文本分词（HanLP-Android 或简易切分）
val tokens = text.splitAndSegment()  // ["存货", "计量", "成本", ...]

// 2. 对每个候选知识点计算 TF-IDF-like 得分
val scores = knowledgePoints.map { kp ->
    val matchCount = kp.keywords.count { it in tokens }
    val formulaMatch = kp.formulas.count { f -> f in text }
    val pitfallMatch = kp.commonPitfalls.count { p -> p in text }
    matchCount * 3.0 + formulaMatch * 2.0 + pitfallMatch * 1.5
}

// 3. 取 top-K=5
val top5 = scores.sortedDescending().take(5)
```

**关键词库生成**：用户提供 PDF/讲义后，写 Python 脚本（一次性）从 PDF 抽每章大纲 → 人工 review → 生成 JSON 入库（详见 §5）。

### 完整数据流

```
用户点"选图/拍题" 
   ↓
ImportViewModel.addImageUri(uri)
   ↓
viewModelScope.launch {
   _uiState.update { copy(ragStatus = LOADING) }
   try {
      val result = classifier.classify(uri)   // 异步, 5-15s
      _uiState.update {
         copy(
            chapterId = result.chapterId,
            knowledgePointId = result.knowledgePointId,
            ragStatus = DONE
         )
      }
   } catch (e: Exception) {
      _uiState.update { copy(ragStatus = ERROR) }   // 失败时 3 个下拉留空
   }
}
```

### `KnowledgeClassifier` 接口

```kotlin
interface KnowledgeClassifier {
    suspend fun classify(
        questionImage: Uri,
        subjectHint: Subject? = null
    ): ClassifyResult
}

data class ClassifyResult(
    val chapterId: Long,
    val knowledgePointId: Long,
    val confidence: Float,           // 0~1, <0.5 时 UI 高亮"建议复核"
    val reasoning: String = ""       // 可选, "为什么这么分类" 展开用
) {
    val isFailed: Boolean get() = chapterId < 0
    companion object {
        fun failed(reason: String) = ClassifyResult(-1, -1, 0f, reason)
    }
}
```

### 关键设计决策

1. **"添加图片后自动跑"是单次触发**——多张题图时，**只对第一张**触发分类
2. **失败静默**——OCR/LLM 异常时，下拉留空 + Snackbar 提示"AI 归类失败，可手动选择"，不阻塞保存
3. **用户可改**——AI 填的值**永远**可手动覆盖
4. **可解释**——`reasoning` 字段记录匹配原因

---

## §3 `KnowledgeClassifier` 的两个实现 + DI

### 3.1 `MockKnowledgeClassifier`

```kotlin
class MockKnowledgeClassifier @Inject constructor() : KnowledgeClassifier {
    override suspend fun classify(questionImage: Uri, subjectHint: Subject?): ClassifyResult {
        delay(800)  // 模拟网络延迟
        return ClassifyResult(
            chapterId = 1, knowledgePointId = 1, confidence = 0.85f,
            reasoning = "[Mock] 这是 mock 返回值"
        )
    }
}
```

**作用**：clone 代码、打开 Android Studio 跑，**立刻能在 UI 看到 RAG 流程生效**（下拉被自动填充），不依赖任何外部服务。

### 3.2 `DeepSeekKnowledgeClassifier`

内部三步管线：

```kotlin
class DeepSeekKnowledgeClassifier @Inject constructor(
    private val ocr: OcrEngine,                  // ML Kit 包装
    private val knowledgeBase: KnowledgeBase,    // 加载自 assets
    private val llm: DeepSeekApi,                // OkHttp + Retrofit
    private val apiKeyProvider: ApiKeyProvider,  // 从 DataStore 取
) : KnowledgeClassifier {

    override suspend fun classify(uri: Uri, subjectHint: Subject?): ClassifyResult {
        try {
            // Step 1: OCR 提取题目文字 (本地, 0.5-2s)
            val text = ocr.recognizeText(uri)
            if (text.isBlank()) return ClassifyResult.failed("OCR 提取为空")

            // Step 2: 关键词召回 top-5 候选 (本地, 50ms)
            val candidates = knowledgeBase.recall(text, topK = 5, chapterHint = subjectHint)

            // Step 3: 精排 - 拼 prompt 调 DeepSeek (云端, 1-3s)
            val prompt = buildPrompt(text, candidates)
            val rawJson = llm.complete(prompt, apiKey = apiKeyProvider.get())

            // Step 4: 解析 JSON 返回结果
            return parseResult(rawJson, candidates)
        } catch (e: Exception) {
            return ClassifyResult.failed(e.message ?: "未知错误")
        }
    }
}
```

### 3.3 依赖注入

新增 `ClassifierModule`：

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object ClassifierModule {
    @Provides @Singleton
    fun provideClassifier(
        mock: Provider<MockKnowledgeClassifier>,
        real: Provider<DeepSeekKnowledgeClassifier>,
        keyStore: ApiKeyProvider
    ): KnowledgeClassifier = if (keyStore.hasKey()) real.get() else mock.get()
}
```

**运行时切换**：用户进设置页填 API Key → `keyStore.hasKey()` 变 `true` → 下次启动就用真分类器。无需重新构建 APK。

### 3.4 失败容错链

```
真实分类器失败（OCR 空 / 网络挂 / LLM 错） 
   ↓ 自动回退
MockClassifier（避免 UI 卡死）
   ↓ 用户看到
"AI 归类失败，已切到手动模式" Snackbar
```

实现：`DeepSeekKnowledgeClassifier.classify` 内部 `try/catch` 所有异常，**不抛出**，返回"失败标记"的 `ClassifyResult`（`chapterId = -1`），UI 端识别后走 mock fallback 路径。

---

## §4 时序图 + 边界 case

### 主流程时序

```
用户                  ImportScreen        ImportViewModel      Classifier          ML Kit/DeepSeek
 │  点"选图"            │                      │                    │                    │
 ├─────────────────────►│                      │                    │                    │
 │                      │ addImageUri(uri)     │                    │                    │
 │                      ├─────────────────────►│                    │                    │
 │                      │                      │ _uiState =         │                    │
 │                      │                      │   LOADING          │                    │
 │                      │  UI 显示 Spinner     │                    │                    │
 │                      │◄─────────────────────┤                    │                    │
 │                      │                      │ classify(uri)      │                    │
 │                      │                      ├───────────────────►│                    │
 │                      │                      │                    │ ① OCR              │
 │                      │                      │                    ├───────────────────►│
 │                      │                      │                    │◄───── text ────────┤
 │                      │                      │                    │ ② 召回 top-5 (内存) │
 │                      │                      │                    │ ③ DeepSeek 精排    │
 │                      │                      │                    ├───────────────────►│
 │                      │                      │                    │◄─── {ch,kp} ───────┤
 │                      │                      │  ClassifyResult    │                    │
 │                      │                      │◄───────────────────┤                    │
 │                      │  _uiState =          │                    │                    │
 │                      │   chapterId=X        │                    │                    │
 │                      │   knowledgePointId=Y │                    │                    │
 │                      │◄─────────────────────┤                    │                    │
 │  下拉自动选中         │                      │                    │                    │
 │◄─────────────────────┤                      │                    │                    │
```

### 5 个边界 case + 处置

#### Case 1：图片加了又删，RAG 还在跑
- **场景**：用户选图 → RAG 启动 → 5s 内用户又点了删除按钮
- **问题**：`ClassifyResult` 回来时 `imageUris` 已经变了
- **方案**：
  - 给 `ImportViewModel` 加 `_pendingClassifyJob: Job?`
  - `addImageUri` 时 `cancel()` 旧 job + 启新 job
  - `removeImageUri` 时 `cancel()` 当前 job
  - `classify()` 内部用 `ensureActive()` 守护
  - 回来时 compare `imageUris[0] == uri` ，不匹配则**丢弃结果**

#### Case 2：多张图（一次上传 3 张）
- **方案**：**只对第 1 张**触发 RAG。后续添加不进 RAG 流程

#### Case 3：用户在 RAG loading 时改了下拉
- **方案**：
  - `classify()` 内部用 `coroutineScope { launch { ... } }`
  - 回来时 check `_uiState.value.chapterId == null && knowledgePointId == null` 才填
  - 否则**丢弃 AI 结果**（用户优先）

#### Case 4：保存时 RAG 还在 loading
- **方案**：
  - `saveMistake()` 入口先 `cancel(_pendingClassifyJob)` 再保存
  - 保存时取的 `_uiState.value` 已是稳态

#### Case 5：网络挂了 / API Key 错 / LLM 超时
- **方案**：
  - `DeepSeekKnowledgeClassifier` 内部 `try/catch` 任何异常
  - 失败时返回 `ClassifyResult.failed(reason)`
  - `ImportViewModel` 识别到 `failed` 时：
    - `_uiState.ragStatus = ERROR`
    - **不填**下拉（保持空，让用户手动选）
    - 显示 Snackbar `"AI 归类失败：${reason}，请手动选择"`
    - 不阻塞保存

### 状态机（`RagStatus`）

```kotlin
enum class RagStatus { IDLE, LOADING, DONE, ERROR }
```

UI 表现：
- `IDLE` → 三个下拉空白，无 Spinner
- `LOADING` → 三个下拉右侧显示小 Spinner，下拉本身仍可选
- `DONE` → 三个下拉已填，Spinner 消失
- `ERROR` → 三个下拉留空，Snackbar 提示，1.5s 后自动回到 `IDLE`

---

## §5 知识库内容生成流程（一次性工作）

### 总体流程

```
你的 PDF/讲义
    ↓
Python 脚本抽取章节文字
    ↓
按章节切分（每章一个 .txt）
    ↓
LLM 辅助生成结构化 JSON（脚本化批跑）
    ↓
人工 review（你看一遍，错的改）
    ↓
合并为 accounting_knowledge_points.json
    ↓
放入 app/src/main/assets/json/
    ↓
App 启动时加载到内存索引
```

### Step 1：PDF → 章节文本

```python
# tools/extract_pdf.py（一次性脚本，不入仓）
import pdfplumber

with pdfplumber.open("cpa_accounting_2024.pdf") as pdf:
    chapters = split_by_outline(pdf)  # 按"第N章"切分
    for i, ch in enumerate(chapters, 1):
        Path(f"out/ch{i:02d}.txt").write_text(ch)
```

输出：`out/ch01.txt` ~ `out/ch30.txt`，每章一个文件。

### Step 2：LLM 辅助生成 JSON

```python
# tools/gen_kb.py（一次性脚本，工具链外）
import json
from openai import OpenAI  # 复用你的 DeepSeek Key

client = OpenAI(api_key=DEEPSEEK_KEY, base_url="https://api.deepseek.com")
PROMPT = """
你是 CPA 会计老师。从下面章节内容里抽取 5~15 个**核心知识点**。
对每个知识点输出 JSON：
{
  "name": "不超过 15 字",
  "description": "200~400 字, 讲清是什么/怎么做/与什么相关",
  "keywords": ["5~10 个高频术语"],
  "formulas": ["出现的核心公式, 没有就空数组"],
  "commonPitfalls": ["考生常错点, 没有就空数组"]
}
章节内容：{chapter_text}
"""

for ch_id in range(1, 31):
    text = Path(f"out/ch{ch_id:02d}.txt").read_text()
    raw = client.chat.completions.create(
        model="deepseek-chat",
        messages=[{"role": "user", "content": PROMPT.format(chapter_text=text)}]
    )
    parsed = json.loads(extract_json(raw.choices[0].message.content))
    out = [{"id": ..., "chapterId": ch_id, **p} for p in parsed]
    Path(f"out/kb_ch{ch_id:02d}.json").write_text(json.dumps(out, ensure_ascii=False, indent=2))
```

**预期产出**：~30 个 JSON 文件，每章 5-15 个知识点，总计 ~150-300 个。

### Step 3：人工 review（**关键质量门**）

- 用户（懂 CPA 的人）**逐章 review** 生成的 JSON
- 错的改：漏抽的补、抽错的删、keywords 改写得更贴近真题用语
- **不能省**——LLM 生成的 description 可能有幻觉，keywords 可能漏掉 CPA 习惯叫法（如"长投"、"顺逆流"、"权益法"）

**预估工作量**：会计 30 章 × 每章 10 分钟 review ≈ **5 小时**一次性投入。

### Step 4：合并入库

```python
# tools/merge_kb.py
merged = []
for ch_id in range(1, 31):
    merged += json.loads(Path(f"out/kb_ch{ch_id:02d}.json").read_text())

# 重新分配 id（1~N 连续）
for i, item in enumerate(merged, 1):
    item["id"] = i

# 写入 assets
Path("../app/src/main/assets/json/accounting_knowledge_points.json").write_text(
    json.dumps({"version": 1, "knowledgePoints": merged}, ensure_ascii=False)
)
```

最终文件结构：
```
app/src/main/assets/json/
└── accounting_knowledge_points.json
    {
      "version": 1,
      "knowledgePoints": [
        { "id": 1, "chapterId": 1, "name": "...", "description": "...", ... },
        { "id": 2, "chapterId": 1, "name": "...", ... },
        ...
        { "id": 287, "chapterId": 30, ... }
      ]
    }
```

### Step 5：App 加载逻辑

```kotlin
// KnowledgeBaseLoader.kt（每次启动跑一次，~50ms）
@Singleton
class KnowledgeBaseLoader @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val moshi = Moshi.Builder().build()
    private val adapter: JsonAdapter<KbFile> = moshi.adapter(KbFile::class.java)

    fun load(): KnowledgeBase {
        val json = ctx.assets.open("json/accounting_knowledge_points.json")
            .bufferedReader().use { it.readText() }
        val file = adapter.fromJson(json)!!
        return KnowledgeBase(
            points = file.knowledgePoints,
            keywordIndex = buildKeywordIndex(file.knowledgePoints)  // 倒排索引
        )
    }
}
```

加载结果缓存在 `SingletonComponent` 作用域，**不重建**。

### 升级路径

将来教材改版：
1. 重新跑 Step 1~4 生成新 JSON
2. 改 `version: 2`
3. App 检测到新 version → 重新加载到内存

JSON 内嵌 `version` 字段 → 未来 schema 演进可平滑迁移。

---

## §6 测试策略 + 风险清单 + 范围边界

### 6.1 测试策略（三层）

#### Layer 1：单元测试（JUnit + Robolectric）

```kotlin
class KeywordRecallTest {
    @Test fun `高频术语应召回正确知识点`() {
        val kb = KnowledgeBaseLoader.loadFromTestResource("kb_sample.json")
        val result = kb.recall("长投权益法顺逆流交易抵销", topK = 5)
        assertEquals("权益法下顺逆流交易", result.first().name)
    }

    @Test fun `空 OCR 结果不抛异常`() = runTest {
        val classifier = DeepSeekKnowledgeClassifier(...)
        val result = classifier.classify(uri)
        assertTrue(result.isFailed)
    }
}
```

覆盖：
- ✅ 关键词召回正确性
- ✅ Prompt 拼接的 JSON 解析容错
- ✅ `MockKnowledgeClassifier` 不挂
- ✅ 5 个边界 case（图片加删/多图/loading 中修改/loading 中保存/网络挂）

#### Layer 2：集成测试（手动 + 截图）

- [ ] 选 1 张会计真题图 → 5s 内下拉被自动填
- [ ] 选 3 张题图 → 只有第 1 张触发 RAG，其他不触发
- [ ] 选图后 2s 内点删除 → RAG 任务被取消，下拉留空
- [ ] 选图后立刻手动改下拉 → RAG 回来不覆盖
- [ ] RAG 跑完前点保存 → 保存的值不带 RAG 结果（或等 RAG 完成）
- [ ] 关 WiFi 选图 → 5s 后 Snackbar 提示"AI 归类失败"，下拉留空
- [ ] 没填 API Key 启动 → 自动用 Mock，能看到下拉被填

#### Layer 3：真实样本测试（最重要）

**找 10-20 道会计真题**（不同章节、不同题型）跑一遍：
- 章节准确率（目标 ≥ 90%）
- 知识点准确率（目标 ≥ 80%）
- 响应时间（目标 ≤ 8s 中位数）

不达标就回 review JSON 调整 keywords。

### 6.2 风险清单 + 缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| DeepSeek API 限流 | RAG 失败 | 失败时返回 `ClassifyResult.failed`，UI 走 mock fallback + Snackbar |
| 用户填错 API Key | 调用 401 | `ApiKeyProvider.get()` 抛 `InvalidKeyException`，UI 引导到设置页 |
| ML Kit 模型未下载 | 首次 OCR 慢 | 第一次分类前检查 `model.isDownloaded`，没下就 fallback |
| 关键词 JSON 漏抽 | 召回率低 | 10-20 道真题小批测，发现漏召回就人工补 keywords |
| LLM 返回非 JSON | 解析崩 | prompt 强制要求 ` ```json ` 块 + 正则容错提取 |
| 教材改版 | 知识库过期 | JSON 内 version 字段 + 升级流程已设计 |
| 多个会计题混在 1 张图 | OCR 提取错 | Prompt 引导 LLM "如有多题选最相关"；UI 也允许手动覆盖 |
| 用户选错题图 | 误分类 | 永远允许手动覆盖 + confidence < 0.5 时 UI 高亮"建议复核" |
| 隐私：题目图上传云 | 敏感题目泄漏 | 首次使用弹窗"需联网调用 DeepSeek" + 隐私说明（不强制阻塞）|

### 6.3 不在本期范围（明确排除）

- ❌ 审计/财管/税法/经济法/战略的知识点（先做会计）
- ❌ 知识点的 UI 增删改（先内置 + 后续可扩）
- ❌ 已录入题目的"批量重分类"（接口预留，UI 后做）
- ❌ 端侧 Embedding / 离线 LLM（云 LLM 够用）
- ❌ 题目图片的二次裁剪/压缩（保持现状）
- ❌ 答案图片的 RAG（只对题目做）

---

## §7 交付物清单

| 类别 | 内容 |
|------|------|
| **代码** | `ClassifierModule` + `MockKnowledgeClassifier` + `DeepSeekKnowledgeClassifier` + `OcrEngine`（ML Kit 包装）+ `KnowledgeBase` + `KnowledgeBaseLoader` + 改 `ImportViewModel` / `ImportScreen` + 设置页（API Key 输入）|
| **资源** | `assets/json/accounting_knowledge_points.json`（150-300 条）|
| **脚本** | `tools/extract_pdf.py` + `tools/gen_kb.py` + `tools/merge_kb.py`（不入仓，放本地）|
| **文档** | 知识库 review check-list + API Key 设置说明 |
| **测试** | 单元测试 ~10 个 + 手动测试清单 |

### 新增/修改文件清单

**新增**：
- `app/src/main/java/com/mistakenotes/data/rag/KnowledgeClassifier.kt`（接口 + ClassifyResult）
- `app/src/main/java/com/mistakenotes/data/rag/MockKnowledgeClassifier.kt`
- `app/src/main/java/com/mistakenotes/data/rag/DeepSeekKnowledgeClassifier.kt`
- `app/src/main/java/com/mistakenotes/data/rag/OcrEngine.kt`
- `app/src/main/java/com/mistakenotes/data/rag/KnowledgeBase.kt`
- `app/src/main/java/com/mistakenotes/data/rag/KnowledgeBaseLoader.kt`
- `app/src/main/java/com/mistakenotes/data/rag/DeepSeekApi.kt`（Retrofit 接口）
- `app/src/main/java/com/mistakenotes/data/rag/ApiKeyProvider.kt`（DataStore 包装）
- `app/src/main/java/com/mistakenotes/di/ClassifierModule.kt`
- `app/src/main/java/com/mistakenotes/ui/screens/SettingsScreen.kt`（API Key 设置）
- `app/src/main/java/com/mistakenotes/ui/navigation/NavGraph.kt`（加 settings 路由）
- `app/src/main/assets/json/accounting_knowledge_points.json`
- `app/src/test/java/com/mistakenotes/data/rag/KeywordRecallTest.kt`
- `app/src/test/java/com/mistakenotes/data/rag/ClassifierTest.kt`

**修改**：
- `app/build.gradle.kts`：加 `com.google.mlkit:text-recognition-chinese` + `retrofit2` + `moshi` 依赖
- `app/src/main/java/com/mistakenotes/ui/screens/ImportScreen.kt`：加 LOADING Spinner + ERROR Snackbar
- `app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt`：加 `ragStatus` 状态机 + 取消逻辑 + RAG 触发

**不入仓（本地工具）**：
- `tools/extract_pdf.py`
- `tools/gen_kb.py`
- `tools/merge_kb.py`

---

## §8 实施顺序（高层）

1. **Step 1（基础架构）**：
   - 加依赖（ML Kit / Retrofit / Moshi）
   - 写 `KnowledgeClassifier` 接口 + `MockKnowledgeClassifier`
   - 加 `ClassifierModule` + DI 接线
   - 改 `ImportViewModel` 加 `ragStatus` 状态机
   - 改 `ImportScreen` 加 LOADING Spinner
   - **验收**：启动 app 选图，能看到下拉被自动填（mock 数据）

2. **Step 2（设置页）**：
   - `ApiKeyProvider`（DataStore 包装）
   - `SettingsScreen`（简单输入框 + 保存按钮）
   - 导航加 settings 路由
   - **验收**：进设置填入 Key，重启 app，能切到真实分类器

3. **Step 3（真实分类器）**：
   - `OcrEngine`（ML Kit 端侧 OCR）
   - `DeepSeekApi`（Retrofit 接口 + JSON 解析）
   - `DeepSeekKnowledgeClassifier`（三步管线）
   - **验收**：填好 Key 后选真实题目图，5-15s 内下拉被填

4. **Step 4（边界 case 完备）**：
   - 加 `Job?` 取消逻辑
   - 加 "user override" 守护
   - 加 save 时 cancel
   - 加 `ClassifyResult.failed()` 路径
   - **验收**：跑完 §6.1 Layer 2 全部 7 条手动测试

5. **Step 5（知识库）**：
   - 写 `tools/extract_pdf.py` + `tools/gen_kb.py` + `tools/merge_kb.py`
   - 用户跑工具生成 JSON 初稿
   - 用户 review JSON
   - 合并入 `assets/json/`
   - 写 `KnowledgeBaseLoader` + `KnowledgeBase.recall()`
   - **验收**：用 10-20 道真题跑 §6.1 Layer 3 准确率测试

6. **Step 6（单元测试）**：
   - 写 `KeywordRecallTest` + `ClassifierTest`
   - **验收**：`./gradlew test` 全绿

7. **Step 7（CLAUDE.md 同步）**：
   - 在 CLAUDE.md 的"自定义图标"、"目录结构"两节加 RAG 相关条目
   - 在"核心功能"加"AI 自动归类"一节

---

## §9 后续 Phase（不在本期）

- **Phase 2-A**：支持已录入题目的"批量重分类"（Classifier 接口已预留，UI 后做）
- **Phase 2-B**：知识点的 UI 增删改（用户能手动加 CPA 漏抽的知识点）
- **Phase 2-C**：扩展到其他 5 个科目（审计/财管/税法/经济法/战略）
- **Phase 3**：加入"按知识点刷题"、"薄弱知识点排行"等功能
