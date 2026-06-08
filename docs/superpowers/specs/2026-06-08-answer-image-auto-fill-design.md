# Design: 答案图片自动识别 → 题型 + 勾选正确答案

**日期**: 2026-06-08
**状态**: 已批准（待实施）
**作者**: brainstorm 会话

---

## 1. 概述

### 1.1 目标

在 `ImportScreen`（录入/编辑错题）上传**答案/解析图片**时，**本地 OCR 提取图片中的字母 A-H**，根据字母数量自动：

- **1 个字母** → 设置 `questionType = SINGLE_CHOICE`，勾选对应选项
- **2+ 个字母** → 设置 `questionType = MULTI_CHOICE`，勾选所有对应选项
- **0 个字母** → 设置 `questionType = ESSAY`（图片本身就是参考答案）

让用户**少一步手动操作**：在题目类型/正确答案上减少点击。

### 1.2 非目标

- **不**改 Room schema（数据库 0 改动，老数据 0 风险）
- **不**复用 RAG 分类器（DeepSeek 调用昂贵、延迟高、不必要）
- **不**改 `ReviewScreen`（本题型不在复习页做）
- **不**自动改"题目图片"的题目类型（仅答案图触发）
- **不**做"主主观题自动设置参考解析图片 OCR 文字提取"（v2 再说）

---

## 2. 用户视角行为

| 场景 | 用户操作 | 系统反馈 |
|------|---------|---------|
| 上传"答案：A"图 | 点 + → 选图 → 裁剪 → 完成 | Snackbar "已识别为单选题，答案：A" + A 选项自动变金色 ✓ |
| 上传"答案：AB"图 | 同上 | Snackbar "已识别为多选题，答案：A、B" + A、B 同时勾选 |
| 上传"略"或"解析"图（无字母） | 同上 | Snackbar "未识别到答案字母，标记为主观题" + 题型切到主观题 |
| 上传"ACD"但只 A、B、C 三选项 | 同上 | Snackbar "已识别为多选题，答案：A、C（D 超出选项范围已忽略）" + A、C 勾选 |
| 答案图 OCR 失败（模型未下载/网络断） | 同上 | Snackbar "OCR 识别失败：{error}，请手动设置" + 不改任何字段 |
| 上传完第一张，又传第二张"解析"图 | 选第二张图 → 裁剪 → 完成 | 第二张**不**触发 OCR（仅首张触发），仅加进列表 |
| 上传答案图后**删掉**那张图 | 点图片右上角 ✕ | 回滚题型 / 勾选到"上传前"的状态 + Snackbar "已恢复题目类型" |

**手动控制保留**：用户可**在 OCR 之后**手动改题型或勾选，OCR 不会重复触发（仅在 `addAnswerImageUri` 时跑一次）。

---

## 3. 实现细节

### 3.1 触发流程

```
[用户点 "+" 答案图按钮]
  → 现有代码: answerImageLauncher.launch("image/*")
  → CropScreen 裁剪
  → 现有代码: addAnswerImageUri(croppedUri)
  → [新增] ViewModel 内部:
      - 若 answerImageUris.size == 1（即这是第一张答案图）
          - snapshot 当前 questionType + correctOptionIndices → preInferenceSnapshot
          - viewModelScope.launch { runOcrAndInfer(croppedUri) }
      - 否则 仅加入列表，不跑 OCR
  → runOcrAndInfer:
      - ocrEngine.recognizeText(croppedUri) → text  // ML Kit 本地
      - 解析 text → letters: List<Char>  // 正则
      - applyAnswerInference(letters, text)  // 写 state + 算 snackbar 文案
```

### 3.2 字母提取（核心正则）

```kotlin
// 匹配独立 A-H 字母（不是单词一部分）
// 例 "答案：A" → [A]
// 例 "AB"     → [A, B]
// 例 "AB型"   → [B]  (A 后面是 B，被吃；B 后面是 型，OK)
// 例 "C语言"  → []   (C 后面是 语，不是字母)
private val ANSWER_LETTER_REGEX = Regex("(?<![A-Z])[A-H](?![A-Za-z])")
```

**验证用例**：

| 输入 | 期望 | 备注 |
|------|------|------|
| `答案：A` | `[A]` | ✓ |
| `正确答案:A` | `[A]` | ✓ |
| `答案：AB` | `[A, B]` | ✓ |
| `ABCD` | `[A, B, C, D]` | ✓ |
| `略` | `[]` | ✓ |
| `AB型` | `[B]` | A 后是 B 被吞 |
| `A. xxx` | `[A]` | ✓ A 后是 `.` |
| `C语言` | `[]` | ✓ C 后是中文 |
| `答案 A、B` | `[A, B]` | ✓ |

**A 后面跟 B 被吞的边界**：用户写 `AB` 期望识别 [A, B]，A 后面是 B → A 的"后瞻"看到 B 字母，A 不算 → B 字母前面是 A 不算？**这个需要二次验证**。先用简单方案 `(?<![A-Z])[A-H](?![A-Z])`（只看大写后缀），A 后是 B 也算。

调整后：
```kotlin
private val ANSWER_LETTER_REGEX = Regex("(?<![A-Z])[A-H](?![A-Z])")
```

| 输入 | 期望 | 实际 |
|------|------|------|
| `AB` | `[A, B]` | A: 前=start, 后=B → 匹配 ✓；B: 前=A, 后=end → 匹配 ✓ → [A, B] ✓ |
| `AB型` | `[A, B]` | A: 前=start, 后=B → ✓；B: 前=A, 后=型（不是大写）→ ✓ → [A, B] |
| `C语言` | `[]` | C: 前=start, 后=语 → ✓；语后无 A-H → 最终 [C]？等等 |

这个 regex 仍然有问题。中文不算 `[A-Z]`，所以 C 后面是"语"（不是大写）也算独立。**最终方案**：识别为独立 token 后，过滤 token 长度=1 且 token ∈ A-H：

```kotlin
// 把文字 split 成 token，过滤出 A-H 单字母 token
val letters = text.split(Regex("[^A-Za-z]"))
    .filter { it.length == 1 && it[0] in 'A'..'H' }
    .map { it[0] }
    .distinct()  // 去重
```

| 输入 | tokens | 过滤后 |
|------|--------|--------|
| `答案：A` | `["答案", "A"]` | `[A]` |
| `答案：AB` | `["答案", "AB"]` | `[]` ⚠️ |

**问题**：相邻字母 `AB` 会被当成一个 token。要在 split 时也按"字母之间"再切：

```kotlin
val tokens = text.split(Regex("[^A-Za-z]+"))  // 连续非字母当分隔
val letters = tokens.flatMap { token ->
    token.toList().filter { it in 'A'..'H' }
}.distinct()
```

| 输入 | tokens | 过滤后 |
|------|--------|--------|
| `答案：A` | `["答案", "A"]` | `[A]` ✓ |
| `答案：AB` | `["答案", "AB"]` | `[A, B]` ✓ |
| `AB型` | `["AB", "型"]` | `[A, B]` ✓ |
| `C语言` | `["C", "语言"]` | `[C]` ⚠️ |

C语言仍识别出 C。这是 OCR 真实场景吗？用户答案图写"C语言"是想勾 C 吗？**不太可能**——答案图里出现"C语言"通常是题目内容。**优化**：只取**出现在"答案"、"答"、"正确答案"等关键词之后**的字母。增加上下文过滤：

```kotlin
// 简单方案：包含"答案"或"答"关键词的 segment 才提取
val segments = text.split(Regex("[。.；;\n]"))  // 句子切分
val answerSegment = segments.firstOrNull { 
    it.contains("答案") || it.contains("答") 
} ?: text
val letters = answerSegment.split(Regex("[^A-Za-z]+"))
    .flatMap { it.toList().filter { c -> c in 'A'..'H' } }
    .distinct()
```

| 输入 | 切分 | 答案 segment | 过滤后 |
|------|------|-------------|--------|
| `答案：A` | `["答案：A"]` | `答案：A` | `[A]` ✓ |
| `本题采用C语言编写` | `["本题采用C语言编写"]` | null → fallback `text` | `[C]` ⚠️ |

仍不完美。**最终方案 — 折中**：用 "答案/答/正确答案" 关键词的 segment；没有关键词时 fallback 取全文字母 A-H，让用户看到 snackbar 自己判断。

**用最终方案 + 明确反馈**：

```kotlin
// 1) 优先找 "答案/正确答案" segment
val segments = text.split(Regex("[。\n;；]"))
val answerSeg = segments.firstOrNull { 
    it.contains(Regex("正确答案|答\s*案|答案|答[是为：:]")) 
}
// 2) 在该 segment 中提取 A-H 字母
val segText = answerSeg ?: text
val letters = segText.split(Regex("[^A-Za-z]+"))
    .flatMap { it.toList().filter { c -> c in 'A'..'H' } }
    .distinct()
```

预期：90%+ 实际场景下正确（用户答案图通常包含"答案"二字）。剩余 10% 边界（无"答案"二字）走 fallback + snackbar 警告让用户核对。

### 3.3 applyAnswerInference 算法

```kotlin
private fun applyAnswerInference(
    uri: Uri,
    letters: List<Char>,
    rawText: String
) {
    val state = _uiState.value
    // 守护：用户可能已经删除/替换了这张图
    if (state.answerImageUris.firstOrNull() != uri) return

    val (newType, newCorrect, feedback) = when {
        letters.isEmpty() -> Triple(
            QuestionType.ESSAY,
            emptySet<Int>(),
            "未识别到答案字母，标记为主观题"
        )
        letters.size == 1 -> {
            val idx = LABELS.indexOf(letters[0].toString())  // A→0, B→1, ...
            if (idx < 0) {
                Triple(QuestionType.ESSAY, emptySet(), "识别异常")
            } else {
                Triple(
                    QuestionType.SINGLE_CHOICE,
                    setOf(idx),
                    "已识别为单选题，答案：${letters[0]}"
                )
            }
        }
        else -> {
            // 过滤超范围
            val validIndices = letters.mapNotNull { LABELS.indexOf(it.toString()).takeIf { i -> i >= 0 } }
            val validLetters = validIndices.map { LABELS[it][0] }
            val dropped = letters - validLetters.toSet()
            val msg = buildString {
                append("已识别为多选题，答案：")
                append(validLetters.joinToString("、"))
                if (dropped.isNotEmpty()) {
                    append("（")
                    append(dropped.joinToString("、"))
                    append(" 超出选项范围已忽略）")
                }
            }
            Triple(QuestionType.MULTI_CHOICE, validIndices.toSet(), msg)
        }
    }

    _uiState.update {
        it.copy(
            questionType = newType,
            correctOptionIndices = newCorrect,
            answerOcrFeedback = feedback
        )
    }
}
```

`LABELS = listOf("A", "B", "C", "D", "E", "F", "G", "H")`，参照 `OptionEntryRow` 里的定义。

### 3.4 状态机扩展

`ImportUiState` 新增字段：

```kotlin
data class ImportUiState(
    // ... 现有字段 ...
    val answerOcrFeedback: String? = null,       // 单次 OCR 反馈，LaunchedEffect 消费
    val answerInferredFromOcr: Boolean = false,  // 是否处于"OCR 推断态"
    val preInferenceSnapshot: AnswerSnapshot? = null  // OCR 前的快照
)

data class AnswerSnapshot(
    val questionType: QuestionType,
    val correctOptionIndices: Set<Int>
)
```

### 3.5 addAnswerImageUri 改造

```kotlin
fun addAnswerImageUri(uri: Uri) {
    val isFirst = _uiState.value.answerImageUris.isEmpty()
    _uiState.update { 
        it.copy(
            answerImageUris = it.answerImageUris + uri,
            // 清理上一次的反馈（避免新 OCR 前显示旧文）
            answerOcrFeedback = null
        ) 
    }
    if (isFirst) {
        // Snapshot 必须在 OCR 前捕获（OCR 完会改 state）
        val current = _uiState.value
        _uiState.update {
            it.copy(
                preInferenceSnapshot = AnswerSnapshot(
                    questionType = current.questionType,
                    correctOptionIndices = current.correctOptionIndices
                )
            )
        }
        // 异步 OCR
        _pendingAnswerOcrJob.value?.cancel()
        _pendingAnswerOcrJob.value = viewModelScope.launch {
            runOcrAndInfer(uri)
        }
    }
}

private suspend fun runOcrAndInfer(uri: Uri) {
    val text = try {
        ocrEngine.recognizeText(uri)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        _uiState.update { 
            it.copy(answerOcrFeedback = "OCR 识别失败：${e.message ?: "未知错误"}，请手动设置") 
        }
        return
    }
    if (text.isBlank()) {
        _uiState.update { 
            it.copy(answerOcrFeedback = "OCR 识别为空，请手动设置") 
        }
        return
    }
    val letters = extractAnswerLetters(text)
    applyAnswerInference(uri, letters, text)
    _uiState.update { it.copy(answerInferredFromOcr = true) }
}
```

### 3.6 removeAnswerImageUri 改造（回滚逻辑）

```kotlin
fun removeAnswerImageUri(index: Int) {
    val current = _uiState.value
    val newList = current.answerImageUris.filterIndexed { i, _ -> i != index }
    
    // 回滚判定：仅当 list 变空 + 处于 OCR 推断态 + 有 snapshot
    val shouldRollback = newList.isEmpty() && 
                         current.answerInferredFromOcr && 
                         current.preInferenceSnapshot != null
    
    if (shouldRollback) {
        val snap = current.preInferenceSnapshot!!
        _uiState.update {
            it.copy(
                answerImageUris = newList,
                questionType = snap.questionType,
                correctOptionIndices = snap.correctOptionIndices,
                answerInferredFromOcr = false,
                preInferenceSnapshot = null,
                answerOcrFeedback = "已恢复题目类型"
            )
        }
        // 取消可能正在跑的 OCR
        _pendingAnswerOcrJob.value?.cancel()
        _pendingAnswerOcrJob.value = null
    } else {
        _uiState.update { it.copy(answerImageUris = newList) }
    }
}
```

### 3.7 Screen 侧 snackbar 弹窗

```kotlin
// 在 ImportScreen Scaffold 内，参照现有 RAG snackbar
LaunchedEffect(uiState.answerOcrFeedback) {
    uiState.answerOcrFeedback?.let { message ->
        snackbarHostState.showSnackbar(message)
        viewModel.clearAnswerOcrFeedback()
    }
}
```

`ImportViewModel` 新增：

```kotlin
fun clearAnswerOcrFeedback() {
    _uiState.update { it.copy(answerOcrFeedback = null) }
}
```

### 3.8 依赖注入

`ImportViewModel` 已有 `@Inject constructor` 通过 Hilt 接收 `KnowledgeClassifier` 和 `KnowledgeBase`。**新增依赖**：

```kotlin
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: MistakeRepository,
    @ApplicationContext private val context: Context,
    private val classifier: KnowledgeClassifier,
    private val knowledgeBase: KnowledgeBase,
    private val ocrEngine: OcrEngine,  // ← 新增（已被 DeepSeekKnowledgeClassifier 间接验证过 Hilt 注入）
    savedStateHandle: SavedStateHandle
)
```

`OcrEngine` 已有 `@Inject constructor()`，Hilt 单例（已通过 `DeepSeekKnowledgeClassifier` 验证）。

新增字段：

```kotlin
private val _pendingAnswerOcrJob = MutableStateFlow<Job?>(null)
```

---

## 4. 边界场景汇总

| # | 场景 | 行为 |
|---|------|------|
| 1 | 多张答案图（先"答案：A"、再"解析：xxx"） | **仅第一张触发**。后续不触发，避免解析图盖掉题型 |
| 2 | 多张反序（先"略"无字母、后"答案：A"） | 同上，**仅第一张触发**。后续不跑 → 保持 ESSAY |
| 3 | 字母超范围 | **丢弃 + snackbar 警告**："A、C（D 超出范围已忽略）" |
| 4 | 删图回滚 | 列表变空 + 处于 OCR 推断态 + 有 snapshot → 回滚到 pre-inference 状态 |
| 5 | 删图但列表非空（如 3 张删中间） | **不回滚**。仅删图，题型/勾选保持 |
| 6 | 编辑模式加载老错题 | **不触发 OCR**。仅新上传图片触发 |
| 7 | OCR 异常（模型未下载/网络断） | snackbar 错误信息 + **不写 state** |
| 8 | OCR 文字为空 | snackbar "OCR 识别为空" + 不写 state |
| 9 | 用户已手动设 ESSAY，再上传"答案：A" | 覆盖 → SINGLE_CHOICE + A 勾选。snackbar 提示已识别 |
| 10 | 用户在 OCR 跑完前又上传第二张图 | 第二张**不**触发 OCR（因 `isFirst=false`） |
| 11 | 用户在 OCR 跑完前点保存 | 保存逻辑不受影响（OCR 异步） |
| 12 | 用户在 OCR 跑完前删除第一张图 | OCR 协程守护：`answerImageUris.firstOrNull() != uri` → return |

---

## 5. 文件改动清单

| 文件 | 改动 | 行数估计 |
|------|------|---------|
| `ImportViewModel.kt` | + 构造参数 `ocrEngine`<br>+ UiState 字段 `answerOcrFeedback` / `answerInferredFromOcr` / `preInferenceSnapshot`<br>+ `extractAnswerLetters(text)` 私有函数<br>+ `applyAnswerInference(...)` 私有函数<br>+ `runOcrAndInfer(uri)` 私有函数<br>+ `clearAnswerOcrFeedback()` 公有<br>+ 改 `addAnswerImageUri`（snapshot + 异步 OCR）<br>+ 改 `removeAnswerImageUri`（回滚逻辑） | ~80 行新增 |
| `ImportScreen.kt` | + `LaunchedEffect(uiState.answerOcrFeedback)` snackbar 弹窗 | ~10 行新增 |
| **其余文件** | **0 改动**（Room / Repository / OcrEngine / 导航 / 其他 Screen） | 0 |

**总计**：~90 行新代码，0 行删除。

---

## 6. 测试策略

### 6.1 单元测试

`extractAnswerLetters` 抽成可测纯函数（输入 `text: String`，输出 `List<Char>`）：

| 测试用例 | 输入 | 期望输出 |
|---------|------|---------|
| 单选 1 字母 | `答案：A` | `[A]` |
| 多选 2 字母 | `答案：AB` | `[A, B]` |
| 多选 4 字母 | `正确答案：ABCD` | `[A, B, C, D]` |
| 无字母 | `略` | `[]` |
| 词内字母 | `C语言` | `[C]`（**已知边界**，snackbar 让用户自核） |
| 词内字母 | `AB型` | `[A, B]`（**期望正确**） |
| 句子切分 | `解析：详见解析。答案：A` | `[A]`（答案 segment 在第二句） |
| 数字 | `答案：1+1=2` | `[]`（数字不算） |
| 小写 | `answer: a` | `[]`（小写不算，仅大写） |
| 重复字母 | `AA` | `[A]`（distinct） |

### 6.2 手动验证（真机覆盖安装后）

| # | 场景 | 操作 | 预期 |
|---|------|------|------|
| 1 | 单选识别 | 录入单选，答案图传"答案：A" | 题型自动→单选，A 勾选，snackbar 提示 |
| 2 | 多选识别 | 录入多选，答案图传"答案：AB" | 题型自动→多选，A、B 勾选，snackbar 提示 |
| 3 | 主观识别 | 录入题，答案图传"略"或"详见解析" | 题型自动→主观题，snackbar 提示 |
| 4 | 超范围 | 录入题（A、B、C 三选项），答案图传"答案：ACD" | A、C 勾选，snackbar 含"D 超出" |
| 5 | 多张图 | 录入题，先传"答案：A"再传"解析：xxx" | 第一张触发 OCR，第二张不触发；状态保持 SINGLE |
| 6 | 删图回滚 | 场景 1 后，删掉答案图 | 题型回到录入前状态，snackbar "已恢复题目类型" |
| 7 | OCR 失败 | 飞行模式下录入 | snackbar 错误，状态不变 |
| 8 | 编辑模式 | 编辑一条已存在的错题，**新上传**答案图 | OCR 触发，结果应用 |
| 9 | 编辑模式 | 编辑一条已存在的错题，**查看已有**答案图 | **不**触发 OCR |
| 10 | 数据保护 | 覆盖安装前，录入 5 条错题；安装新 APK 后 | 5 条数据完整，OCR 功能可用 |

### 6.3 回归测试

- 现有 RAG 自动归类（题目图 RAG）行为不变
- `ReviewScreen` 不变
- `BrowseScreen` / `HomeScreen` / `AnalysisScreen` 不变
- Room 数据库 schema 0 改动

---

## 7. 数据保护

- ✅ **Room 数据库 0 改动**（schema 不动）→ 老数据 0 丢失风险
- ✅ **Repository 0 改动**（不新增/修改字段）
- ✅ **Mistake 实体 0 改动**
- ✅ **现有 RAG 链路 0 改动**（新增 `OcrEngine` 直接注入，但 `DeepSeekKnowledgeClassifier` 已用过相同 Hilt 单例，零风险）
- ✅ **新功能在 ViewModel 状态机内部**，不影响已落库的 mistake
- ✅ **OCR 失败 fail-soft**：异常时不动 state，仅 snackbar 提示
- ✅ **删除答案图有回滚**（§2.4 用户要求）：避免"删图后状态错乱"

---

## 8. 实施步骤（后续 writing-plans 拆解）

1. 改 `ImportUiState` 加 3 个字段
2. 改 `ImportViewModel` 构造加 `OcrEngine` 注入
3. 写 `extractAnswerLetters(text: String): List<Char>` 私有函数 + 单元测试
4. 写 `applyAnswerInference(uri, letters, rawText)` 私有函数
5. 写 `runOcrAndInfer(uri)` 私有函数
6. 改 `addAnswerImageUri`（加 snapshot + 异步 OCR 触发）
7. 改 `removeAnswerImageUri`（加回滚判定）
8. 加 `clearAnswerOcrFeedback()` 公有函数
9. 改 `ImportScreen.kt` 加 `LaunchedEffect` snackbar
10. 真机手动验证（按 §6.2 表格 10 项）
11. 覆盖安装验证数据不丢失（按 §6.2 #10）
